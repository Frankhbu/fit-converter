package me.frankhbu.fitconverter.convert;

import com.garmin.fit.ActivityType;
import com.garmin.fit.DateTime;
import com.garmin.fit.RecordMesg;
import me.frankhbu.fitconverter.model.huawei.HuaweiSportRecord;
import me.frankhbu.fitconverter.model.huawei.HuaweiSportType;
import me.frankhbu.fitconverter.model.huawei.TrackPoint;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 将华为多路异步采样的轨迹点重建为统一 1Hz 时间轴的 Record 序列。
 *
 * <p>华为原始数据各通道采样率不一致（GPS 1~3s、心率/步频/速度 5s），直接按点写 Record
 * 会导致同一秒的多路数据被拆散、或某些通道长期缺失。本类的做法是：
 * <ol>
 *   <li>把每个通道整理成「秒级时间戳 → 数值」的有序序列；</li>
 *   <li>以 startTime 为起点、totalTime 为长度建立逐秒时间轴；</li>
 *   <li>每一秒在各通道上做线性插值取值（跨度超限则留空，绝不凭空填充）；</li>
 *   <li>距离由 GPS 轨迹或速度积分推算，并按 totalDistance 归一化。</li>
 * </ol>
 */
public class RecordTimeline {

    /** 通道插值的最大跨度（秒）：华为常规采样间隔为 5s，超过该跨度视为数据缺失 */
    private static final long MAX_GAP_SEC = 30L;

    /** 跨度超限时允许的端点保持填充跨度（秒） */
    private static final long HOLD_SEC = 5L;

    /** 华为 lbs 无效点的原始度哨兵值 */
    private static final double INVALID_LAT_DEG = 90.0;
    private static final double INVALID_LON_DEG = -80.0;

    /** 哨兵值比较容差：避免 JSON 浮点往返产生的微小误差导致等值比较失败 */
    private static final double GPS_SENTINEL_EPS = 1e-6;

    private final CoordinateConverter coord = new CoordinateConverter();

    /** 一路采样通道：秒级时间戳 → 数值，支持带跨度限制的线性插值取值 */
    static class Series {
        private final TreeMap<Long, Double> data = new TreeMap<>();

        void put(long tsSec, double v) {
            data.put(tsSec, v);
        }

        boolean isEmpty() {
            return data.isEmpty();
        }

        Long firstKey() {
            return data.isEmpty() ? null : data.firstKey();
        }

        Long lastKey() {
            return data.isEmpty() ? null : data.lastKey();
        }

        /**
         * 取 t 时刻的插值值。
         * 前后相邻点跨度在 {@link #MAX_GAP_SEC} 内时线性插值；
         * 跨度超限时仅在距端点 {@link #HOLD_SEC} 内保持端点值，否则返回 null（缺失）。
         */
        Double at(long t) {
            if (data.isEmpty()) return null;
            // t 左侧最近的数据点（时间戳 ≤ t）
            Map.Entry<Long, Double> leftEntry = data.floorEntry(t);
            // t 右侧最近的数据点（时间戳 ≥ t）
            Map.Entry<Long, Double> rightEntry = data.ceilingEntry(t);
            if (leftEntry == null && rightEntry == null) return null;
            // 只有一侧有数据：距该点 HOLD_SEC 内则沿用其值，否则视为缺失
            if (leftEntry == null) return (rightEntry.getKey() - t <= HOLD_SEC) ? rightEntry.getValue() : null;
            if (rightEntry == null) return (t - leftEntry.getKey() <= HOLD_SEC) ? leftEntry.getValue() : null;
            // 两侧数据点之间的时间跨度（秒）
            long gapSeconds = rightEntry.getKey() - leftEntry.getKey();
            if (gapSeconds == 0) return leftEntry.getValue();
            // 跨度超限：只在紧邻端点处沿用端点值，中间段视为缺失
            if (gapSeconds > MAX_GAP_SEC) {
                if (t - leftEntry.getKey() <= HOLD_SEC) return leftEntry.getValue();
                if (rightEntry.getKey() - t <= HOLD_SEC) return rightEntry.getValue();
                return null;
            }
            // 跨度正常：按 t 在两点间的位置做线性插值
            double ratio = (double) (t - leftEntry.getKey()) / (double) gapSeconds;
            return leftEntry.getValue() + ratio * (rightEntry.getValue() - leftEntry.getValue());
        }
    }

    /** 重采样结果：Record 序列 + 由其派生的统计量 */
    public static class Result {
        public List<RecordMesg> records = new ArrayList<>();
        /** 逐秒累计距离的终值（米），0 表示无距离数据 */
        public double totalDistanceM;
        /** 是否含有效 GPS 轨迹 */
        public boolean hasGps;
    }

    /** 各通道原始序列，供 Lap/Session 级统计复用 */
    public static class Channels {
        Series hr = new Series();
        Series cadence = new Series();   // 原始值：跑走为步/分，骑行/跳绳为转(次)/分
        Series speed = new Series();     // 米/秒
        Series altitude = new Series();  // 米
        Series lat = new Series();       // 度
        Series lon = new Series();       // 度
    }

    /**
     * 构建 1Hz Record 序列。
     *
     * @param record 华为运动记录
     * @return 重采样结果
     */
    public Result build(HuaweiSportRecord record) {
        long startMs = record.getStartTime();
        long startSec = startMs / 1000L;
        long totalTimeSec = record.getTotalTime() / 1000L;
        double totalDistanceM = record.getTotalDistance();
        boolean hasDistance = totalDistanceM > 0;
        HuaweiSportType type = HuaweiSportType.getByCode(record.getSportType());

        //各通道的key是绝对时间秒级时间戳，unix时间戳，我的源码中，所有地方用的都是绝对时间戳
        //fit纪元时间戳是在com.garmin.fit.DateTime中由佳明sdk转换的
        Channels channels = collectChannels(record, startMs, type);

        long endSec = startSec + Math.max(totalTimeSec, 0);
        // 数据实际覆盖的末端，避免 totalTime偏大时尾部产生大量空 Record
        Long dataEnd = maxLastKey(channels);
        if (dataEnd != null && dataEnd < endSec) {
            endSec = dataEnd;
        }
        if (endSec < startSec) endSec = startSec;

        Result result = new Result();
        result.hasGps = !channels.lat.isEmpty();

        // 第一轮：逐秒取值并累计原始距离
        List<RecordMesg> rawRecords = new ArrayList<>();
        List<Double> cumulativeDistanceList = new ArrayList<>();
        double cumulativeDistance = 0.0;
        double prevLat = Double.NaN;
        double prevLon = Double.NaN;

        for (long second = startSec; second <= endSec; second++) {
            Double heartRate = channels.hr.at(second);
            Double cadence = channels.cadence.at(second);
            Double speed = channels.speed.at(second);
            Double altitude = channels.altitude.at(second);
            Double latitude = channels.lat.at(second);
            Double longitude = channels.lon.at(second);

            if (heartRate == null && cadence == null && speed == null && altitude == null && latitude == null) {
                continue;   // 该秒全通道无数据，不产出 Record
            }

            RecordMesg recordMesg = new RecordMesg();
            recordMesg.setTimestamp(new DateTime(new Date(second * 1000L)));
            //运动类型

            recordMesg.setActivityType(toActivityType(type));
            //经纬度
            if (latitude != null && longitude != null) {
                //把经纬度（度为单位）转换成「半圆」（semicircles）单位
                int[] semicircles = coord.toSemicircles(latitude, longitude);
                recordMesg.setPositionLat(semicircles[0]);
                recordMesg.setPositionLong(semicircles[1]);
                if (!Double.isNaN(prevLat)) {
                    cumulativeDistance += haversine(prevLat, prevLon, latitude, longitude);
                }
                prevLat = latitude;
                prevLon = longitude;
            } else if (speed != null && speed > 0) {
                // GPS 缺失段用速度积分兜底（1Hz 轴，步长 1 秒）
                cumulativeDistance += speed;
            }
            //海拔
            if (altitude != null)
                recordMesg.setEnhancedAltitude(altitude.floatValue());

            //心率
            if (heartRate != null && heartRate > 0)
                recordMesg.setHeartRate((short) Math.round(heartRate));
            //实时速度
            // 无距离类运动（力量/自由训练/爬楼等）的 rs 通道速度无实际意义，不写
            if (hasDistance && speed != null && speed >= 0)
                recordMesg.setEnhancedSpeed(speed.floatValue());
            //步频
            applyCadence(recordMesg, cadence, type);
            //步幅，步长
            applyStepLength(recordMesg, hasDistance ? speed : null, cadence, type);

            rawRecords.add(recordMesg);
            cumulativeDistanceList.add(cumulativeDistance);
        }

        // 第二轮：距离按 totalDistance 归一化后写入
        if (totalDistanceM > 0 && cumulativeDistance > 0) {
            double scale = totalDistanceM / cumulativeDistance;
            for (int i = 0; i < rawRecords.size(); i++) {
                rawRecords.get(i).setDistance((float) (cumulativeDistanceList.get(i) * scale));
            }
            result.totalDistanceM = totalDistanceM;
        } else if (totalDistanceM > 0) {
            result.totalDistanceM = totalDistanceM;
        } else {
            result.totalDistanceM = 0;
        }

        result.records = rawRecords;
        return result;
    }

    /**
     * 把原始轨迹点分发到各通道（单位统一到 FIT 口径）。
     *
     * <p>华为通道单位与 FIT 的换算依据：
     * <ul>
     *   <li>{@code rs}：实时速度，单位分米/秒，FIT enhanced_speed 为米/秒，故除以 10。</li>
     *   <li>{@code s-r}：步频，单位步/分且为双脚计数；FIT cadence 语义为单腿转/分，
     *       故在 {@link #applyCadence} 中除以 2，余数 0.5 写入 fractional_cadence。</li>
     *   <li>{@code cad}：骑行踏频，已是转/分，直接写入（本批源数据该通道恒 ≤0，实际不可用）。</li>
     *   <li>{@code scp}：跳绳瞬时频率，次/分，按 cadence 原值写入。</li>
     *   <li>{@code alti}：海拔，单位米；{@code lbs.alt} 在本批源数据中恒为 0，不可用。</li>
     *   <li>{@code p-m} / {@code b-p-m}：配速通道，其 k 为编码值（序号×10000000+累计秒）
     *       而非时间戳，且配速可由 speed 反推，故不采集。</li>
     * </ul>
     */
    private Channels collectChannels(HuaweiSportRecord record, long startMs, HuaweiSportType type) {
        Channels ch = new Channels();
        List<TrackPoint> track = record.getTrackPoints();
        if (track == null) return ch;

        for (TrackPoint p : track) {
            String t = p.getType();
            if (t == null) continue;
            switch (t) {
                case "lbs": {
                    double la = p.getLat();
                    double lo = p.getLon();
                    // 哨兵无效点用容差比较，避免浮点往返误差导致漏判
                    if (Math.abs(la - INVALID_LAT_DEG) < GPS_SENTINEL_EPS
                            && Math.abs(lo - INVALID_LON_DEG) < GPS_SENTINEL_EPS) break;
                    if (la == 0 && lo == 0) break;
                    long ts = p.getTimeMillis() / 1000L;
                    if (ts <= 0) break;
                    ch.lat.put(ts, la);
                    ch.lon.put(ts, lo);
                    break;
                }
                case "h-r":
                case "i-h-r": {
                    if (p.getV() > 0) ch.hr.put(absSec(p, startMs), p.getV());
                    break;
                }
                case "s-r": {
                    // 步/分（双脚）
                    if (p.getV() > 0) ch.cadence.put(absSec(p, startMs), p.getV());
                    break;
                }
                case "cad": {
                    // 骑行踏频，转/分
                    if (p.getV() > 0) ch.cadence.put(absSec(p, startMs), p.getV());
                    break;
                }
                case "scp": {
                    // 跳绳频率，次/分（含 0 值，表示静止，需保留以体现间歇）
                    if (p.getV() >= 0) ch.cadence.put(absSec(p, startMs), p.getV());
                    break;
                }
                case "rs": {
                    // 实时速度，分米/秒
                    if (p.getV() >= 0) ch.speed.put(absSec(p, startMs), p.getV() / 10.0);
                    break;
                }
                case "alti":
                case "alt": {
                    ch.altitude.put(absSec(p, startMs), p.getV());
                    break;
                }
                default:
                    break;
            }
        }
        return ch;
    }

    /** 传感器点的绝对秒级时间戳：rs/spo2 的 k 为相对起始秒偏移，其余为绝对时间戳 */
    private long absSec(TrackPoint p, long startMs) {
        long k = p.getK();
        String t = p.getType();
        if ("rs".equals(t) || "spo2".equals(t)) {
            return startMs / 1000L + k;
        }
        return TrackPoint.normalizeToMillis(k) / 1000L;
    }

    /** 按运动类型写入 cadence：跑走需由步/分折算为单腿转/分并保留 0.5 小数位 */
    private void applyCadence(RecordMesg rec, Double cad, HuaweiSportType type) {
        if (cad == null || cad < 0) return;
        switch (type) {
            case RUN:
            case INDOOR_RUN:
            case TRAIL_RUN:
            case WALK:
            case INDOOR_WALK: {
                double rpm = cad / 2.0;
                short whole = (short) Math.floor(rpm);
                if (whole <= 0) return;
                rec.setCadence(whole);
                float frac = (float) (rpm - whole);
                if (frac > 0) rec.setFractionalCadence(frac);
                break;
            }
            case CYCLE:
            case JUMP_ROPE: {
                if (cad <= 0) return;
                rec.setCadence((short) Math.round(cad));
                break;
            }
            default:
                break;
        }
    }

    /**
     * 逐点运动形态（record.activity_type）。
     * 仅按运动类型给定，源数据没有逐点形态切换信息。
     */
    private ActivityType toActivityType(HuaweiSportType type) {
        switch (type) {
            case RUN:
            case INDOOR_RUN:
            case TRAIL_RUN:
                return ActivityType.RUNNING;
            case WALK:
            case INDOOR_WALK:
                return ActivityType.WALKING;
            case CYCLE:
                return ActivityType.CYCLING;
            default:
                return ActivityType.GENERIC;
        }
    }

    /**
     * 根据速度与步频推算步长，写入 record 消息。
     *
     * <p>计算公式：
     * <pre>
     *   步长(mm) = 速度(m/s) ÷ 步频(步/s) × 1000
     * </pre>
     *
     * <p>源数据无直接步长通道，仅对跑步 / 步行类运动有效；
     * 骑行等无步长概念的运动直接跳过。
     *
     * @param rec        待写入的 Record 消息
     * @param speed      瞬时速度，单位 m/s；为 null 或 ≤0 时跳过
     * @param cadenceSpm 瞬时步频，单位 步/分；为 null 或 ≤0 时跳过
     * @param type       运动类型，用于判断是否适用步长概念
     */
    private void applyStepLength(RecordMesg rec, Double speed, Double cadenceSpm, HuaweiSportType type) {
        // 前置校验：速度或步频无效时无法计算
        if (speed == null || cadenceSpm == null || speed <= 0 || cadenceSpm <= 0) return;
        // 仅跑步/步行类运动有步长概念
        if (!hasStepLength(type)) return;

        // 步频转换：步/分 → 步/秒
        double stepsPerSec = cadenceSpm / 60.0;
        // 步长 = 速度 ÷ 步频，单位换算为毫米
        double stepLengthMm = speed / stepsPerSec * 1000.0;

        // 合理性校验：步长应在 (0, 5000] mm 范围内，超出视为传感器异常
        if (stepLengthMm <= 0 || stepLengthMm > 5000) return;
        rec.setStepLength((float) stepLengthMm);
    }

    /**
     * 判断该运动类型是否适用「步长」概念。
     * 跑步、越野跑、步行有步长；骑行、游泳等无步长概念。
     */
    private static boolean hasStepLength(HuaweiSportType type) {
        switch (type) {
            case RUN:
            case INDOOR_RUN:
            case TRAIL_RUN:
            case WALK:
            case INDOOR_WALK:
                return true;
            default:
                return false;
        }
    }

    /** 各通道末端时间戳的最大值 */
    private Long maxLastKey(Channels ch) {
        Long max = null;
        for (Series s : new Series[]{ch.hr, ch.cadence, ch.speed, ch.altitude, ch.lat}) {
            Long k = s.lastKey();
            if (k != null && (max == null || k > max)) max = k;
        }
        return max;
    }

    /** Haversine 距离（米） */
    static double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}