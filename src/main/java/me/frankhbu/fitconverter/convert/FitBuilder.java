package me.frankhbu.fitconverter.convert;

import com.garmin.fit.Activity;
import com.garmin.fit.ActivityMesg;
import com.garmin.fit.DateTime;
import com.garmin.fit.DeviceIndex;
import com.garmin.fit.DeviceInfoMesg;
import com.garmin.fit.DeveloperDataIdMesg;
import com.garmin.fit.DeveloperField;
import com.garmin.fit.Event;
import com.garmin.fit.EventMesg;
import com.garmin.fit.EventType;
import com.garmin.fit.FieldDescriptionMesg;
import com.garmin.fit.File;
import com.garmin.fit.FileCreatorMesg;
import com.garmin.fit.FileEncoder;
import com.garmin.fit.Fit;
import com.garmin.fit.FileIdMesg;
import com.garmin.fit.Intensity;
import com.garmin.fit.LapMesg;
import com.garmin.fit.LapTrigger;
import com.garmin.fit.Manufacturer;
import com.garmin.fit.MesgNum;
import com.garmin.fit.RecordMesg;
import com.garmin.fit.SessionMesg;
import com.garmin.fit.SessionTrigger;
import com.garmin.fit.SourceType;
import com.garmin.fit.SetMesg;
import com.garmin.fit.SetType;
import com.garmin.fit.Sport;
import com.garmin.fit.SportMesg;
import com.garmin.fit.SubSport;
import com.garmin.fit.TimerTrigger;
import me.frankhbu.fitconverter.model.huawei.HuaweiSimplifyData;
import me.frankhbu.fitconverter.model.huawei.ExtendTrackData;
import me.frankhbu.fitconverter.model.huawei.HuaweiSportRecord;
import me.frankhbu.fitconverter.model.huawei.HuaweiSportType;
import me.frankhbu.fitconverter.model.huawei.PartTimeEntry;
import me.frankhbu.fitconverter.model.huawei.TrackPoint;
import me.frankhbu.fitconverter.model.huawei.WearSportData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TreeMap;

/**
 * 将华为运动记录构建为 FIT 消息序列并写入文件。
 *
 * 轨迹点类型与时间轴：
 *   lbs  —— GPS 点，k=序号，t=绝对毫秒时间戳，lat/lon/alt
 *   h-r  —— 心率，k=绝对毫秒时间戳，v=次/分
 *   s-r  —— 步频，k=绝对毫秒时间戳，v=步/分
 *   alti/alt —— 海拔，k=绝对毫秒时间戳，v=米
 *   cad  —— 踏频，k=绝对毫秒时间戳，v
 *   rs   —— 实时速度，k=秒偏移（相对 startTime），v=分米/秒
 *   i-h-r—— 瞬时心率，k=绝对毫秒时间戳，v=-1 无效
 *   其余类型暂不映射到 Record。
 */
public class FitBuilder {

    /** 无效 GPS 纬度哨兵值（0x40000000 = 1073741824 半圆） */
    private static final int INVALID_POSITION = 1073741824;

    /** 华为 lbs 无效点的原始度哨兵值：lat=90.0, lon=-80.0 */
    private static final double INVALID_LAT_DEG = 90.0;
    private static final double INVALID_LON_DEG = -80.0;

    /** 哨兵值比较容差：避免 JSON 浮点往返产生的微小误差导致等值比较失败 */
    private static final double GPS_SENTINEL_EPS = 1e-6;

    /** 坐标转换器（度→半圆，预留 GCJ-02→WGS-84 纠偏扩展点） */
    private final CoordinateConverter coord = new CoordinateConverter();

    /** 自动计圈分段距离（米）：跑步/步行等默认 1 km 一圈 */
    private static final float LAP_DISTANCE_DEFAULT_M = 1000.0f;

    /** 自动计圈分段距离（米）：骑行默认 5 km 一圈，与佳明骑行 Auto Lap 默认值一致 */
    private static final float LAP_DISTANCE_CYCLING_M = 5000.0f;

    /**
     * 末段 Lap 的最小距离（米）。
     *
     * <p>末段不足一圈仍单独成圈；只有当末段距离小于该阈值（整公里边界与终点几乎重合，
     * 余下距离接近 0）时才并入上一圈，避免产生 0 米碎圈。</p>
     */
    private static final double MIN_TAIL_LAP_M = 10.0;

    /** 有效海拔范围（米）：下限取死海以下余量，上限取珠峰以上余量，用于剔除异常值 */
    private static final float MIN_VALID_ALTITUDE_M = -500.0f;
    private static final float MAX_VALID_ALTITUDE_M = 9000.0f;

    /** 数据来源设备的产品名（华为运动健康导出数据） */
    private static final String HUAWEI_PRODUCT_NAME = "Huawei Health";

    /** 本转换器版本号：1.0.0 → FileCreator 用整型百分位表示，DeviceInfo 用浮点表示 */
    private static final int CONVERTER_SOFTWARE_VERSION = 100;
    private static final float CONVERTER_VERSION = 1.00f;

    /**
     * 本转换器的 Developer Data application_id（16 字节）。
     * 仅用于标识自定义字段的归属应用，与运动数据无关，固定不变。
     * <p>各字节均取 0x00~0x7F：FIT 的 byte 基础类型把 0xFF 作为无效值，
     * SDK 以带符号 Byte 写入，取值高于 0x7F 的字节会在解码端退化为 255。
     */
    private static final byte[] APPLICATION_ID = {
            (byte) 0x7f, (byte) 0x3c, (byte) 0x1a, (byte) 0x26,
            (byte) 0x1b, (byte) 0x04, (byte) 0x4c, (byte) 0x58,
            (byte) 0x0f, (byte) 0x21, (byte) 0x5d, (byte) 0x6e,
            (byte) 0x0a, (byte) 0x33, (byte) 0x7c, (byte) 0x11
    };

    public void build(HuaweiSportRecord record, String outputPath) throws Exception {
        long startTimeMs = record.getStartTime();
        long endTimeMs = record.getEndTime();
        long totalTimeSec = record.getTotalTime() / 1000;
        double totalDistanceM = record.getTotalDistance();
        long totalCalories = record.getTotalCalories() / 1000;

        List<TrackPoint> track = record.getTrackPoints();

        // 重采样为统一 1Hz 时间轴的 Record 序列（多路通道插值合并 + 距离归一化）
        RecordTimeline.Result timeline = new RecordTimeline().build(record);
        List<RecordMesg> records = timeline.records;

        // 提取有效 GPS 点，用于计算起终点位置
        List<TrackPoint> gpsPoints = collectGpsPoints(track);

        // 计算统计字段：爬升/下降直接取华为统计块的权威值（单位分米）
        double[] ascentDescentM = calcAscentDescent(record);
        // FIT 的 total_ascent/total_descent 为整米（uint16），小数部分另由
        // total_fractional_ascent/total_fractional_descent 承载，故此处取下整而非四舍五入，
        // 避免 147.7 被写成 148 导致与华为 App 的显示值差 1 米。
        int totalAscent = ascentDescentM[0] >= 0 ? (int) Math.floor(ascentDescentM[0]) : -1;
        int totalDescent = ascentDescentM[1] >= 0 ? (int) Math.floor(ascentDescentM[1]) : -1;
        float[] avgMaxSpeed = calcAvgMaxSpeed(records);
        Short[] avgMaxCadence = calcAvgMaxCadence(records);
        int[] startEndPos = calcStartEndPosition(gpsPoints);

        FileEncoder encoder = new FileEncoder(new java.io.File(outputPath), Fit.ProtocolVersion.V2_0);

        // 设备标识：华为导出数据里只有 deviceCode 一个设备标识，无型号码/型号名，
        // 因此 manufacturer 用 FIT 规范为「未注册厂商」保留的 DEVELOPMENT，
        // product 不写（无真实型号码，写死值等于伪造），serial_number 取 deviceCode。
        Long serialNumber = record.getDeviceCode() != 0 ? (long) record.getDeviceCode() : null;

        // 1. FileIdMesg
        FileIdMesg fileId = new FileIdMesg();
        fileId.setType(File.ACTIVITY);
        fileId.setManufacturer(Manufacturer.DEVELOPMENT);
        if (serialNumber != null) fileId.setSerialNumber(serialNumber);
        fileId.setTimeCreated(new DateTime(new Date(startTimeMs)));
        encoder.write(fileId);

        // 2. DeviceInfoMesg
        DeviceInfoMesg device = new DeviceInfoMesg();
        device.setTimestamp(new DateTime(new Date(startTimeMs)));
        device.setDeviceIndex(DeviceIndex.CREATOR);
        device.setSourceType(SourceType.LOCAL);
        device.setManufacturer(Manufacturer.DEVELOPMENT);
        if (serialNumber != null) device.setSerialNumber(serialNumber);
        device.setProductName(HUAWEI_PRODUCT_NAME);
        device.setSoftwareVersion(CONVERTER_VERSION);
        encoder.write(device);

        // 3. FileCreatorMesg
        FileCreatorMesg fileCreator = new FileCreatorMesg();
        fileCreator.setSoftwareVersion(CONVERTER_SOFTWARE_VERSION);
        encoder.write(fileCreator);

        // 4. SportMesg
        SportMesg sportMesg = new SportMesg();
        sportMesg.setSport(toSport(record.getSportType()));
        sportMesg.setSubSport(toSubSport(record.getSportType()));
        sportMesg.setName(HuaweiSportType.getByCode(record.getSportType()).getNameCn());
        encoder.write(sportMesg);

        // 5. ActivityMesg
        ActivityMesg activity = new ActivityMesg();
        DateTime activityStart = new DateTime(new Date(startTimeMs));
        activity.setTimestamp(activityStart);
        activity.setTotalTimerTime((float) totalTimeSec);
        activity.setNumSessions(1);
        activity.setType(Activity.MANUAL);
        activity.setEvent(Event.ACTIVITY);
        activity.setEventType(EventType.STOP);
        // local_timestamp 与 timestamp 同为 FIT 纪元秒，二者之差即时区偏移
        activity.setLocalTimestamp(
                activityStart.getTimestamp() + timeZoneOffsetSeconds(record.getTimeZone(), startTimeMs));
        encoder.write(activity);

        // 心率：SIMPLIFY 统计块已给出设备端统计的平均/最高/最低心率（与华为 App 显示一致），
        // 优先直接采用；仅在该统计缺失时才回落为按 h-r 逐点自算。
        Short avgHr = sessionAvgHeartRate(record, track);
        Short maxHr = sessionMaxHeartRate(record, track);
        Short minHr = sessionMinHeartRate(record);

        HuaweiSportType sportType = HuaweiSportType.getByCode(record.getSportType());
        // 力量/自由训练按「组（set）」而非「圈（lap）」组织，与佳明一致
        boolean setBased = sportType == HuaweiSportType.STRENGTH_TRAINING
                || sportType == HuaweiSportType.FREE_TRAINING;
        List<SetSegment> setSegments = setBased ? buildSetSegments(record) : new ArrayList<SetSegment>();
        // 有组数据时不写 lap；无组数据（力量训练无 sec 点）时仍保留单 lap 作为结构兜底
        List<LapSegment> lapSegments = setSegments.isEmpty()
                ? buildLapSegments(records, record, totalCalories, totalTimeSec)
                : new ArrayList<LapSegment>();
        // 分圈爬升/下降在源数据中没有对应字段（sec 分段不含海拔增量），
        // 只能由 1Hz 海拔序列自算；此处按各圈自算量的占比重新标定到 SIMPLIFY 的权威总量，
        // 使 Σlap 与 session 严格相等，避免佳明端分圈之和与总量不一致。
        rescaleLapAltitudeGain(lapSegments, totalAscent, totalDescent);
        int numLaps;
        if (!setSegments.isEmpty()) {
            // 佳明在该场景下 num_laps 记录的是组数
            numLaps = setSegments.size();
        } else {
            numLaps = lapSegments.isEmpty() ? 1 : lapSegments.size();
        }

        // 跳绳的「中断次数」与「最大连跳次数」在 FIT 标准字段中无对应项，
        // 以 Developer Data Field 挂到 session 上。字段描述必须先于 session 写入。
        DeveloperField interruptTimesField = null;
        DeveloperField maxConsecutiveJumpsField = null;
        if (sportType == HuaweiSportType.JUMP_ROPE) {
            ExtendTrackData ext = extendTrackData(record);
            Integer interruptTimes = extNonNegInt(ext == null ? null : ext.getInterruptTimes());
            Integer maxConsecutiveJumps = extInt(ext == null ? null : ext.getMaxSkippingTimes());
            if (interruptTimes != null || maxConsecutiveJumps != null) {
                DeveloperDataIdMesg devId = newDeveloperDataId();
                encoder.write(devId);
                if (interruptTimes != null) {
                    FieldDescriptionMesg fd = newFieldDescription(0, "interrupt_times", "times");
                    encoder.write(fd);
                    interruptTimesField = new DeveloperField(fd, devId);
                    interruptTimesField.setValue(interruptTimes);
                }
                if (maxConsecutiveJumps != null) {
                    FieldDescriptionMesg fd = newFieldDescription(1, "max_consecutive_jumps", "counts");
                    encoder.write(fd);
                    maxConsecutiveJumpsField = new DeveloperField(fd, devId);
                    maxConsecutiveJumpsField.setValue(maxConsecutiveJumps);
                }
            }
        }

        // 6. SessionMesg
        SessionMesg session = new SessionMesg();
        session.setTimestamp(new DateTime(new Date(endTimeMs)));
        session.setStartTime(new DateTime(new Date(startTimeMs)));
        session.setSport(toSport(record.getSportType()));
        session.setSubSport(toSubSport(record.getSportType()));
        // 墙钟时长（含暂停）用于 total_elapsed_time，净运动时长用于 total_timer_time；
        // 华为未给出暂停的具体时间点，故不生成 TIMER STOP/START 对，仅在总量上体现差异。
        long elapsedSec = (endTimeMs - startTimeMs) / 1000;
        if (elapsedSec < totalTimeSec) elapsedSec = totalTimeSec;
        session.setTotalElapsedTime((float) elapsedSec);
        session.setTotalTimerTime((float) totalTimeSec);
        session.setTotalDistance((float) totalDistanceM);
        session.setTotalCalories((int) totalCalories);
        session.setMessageIndex(0);
        session.setFirstLapIndex(0);
        session.setNumLaps(numLaps);
        if (avgHr != null) session.setAvgHeartRate(avgHr);
        if (maxHr != null) session.setMaxHeartRate(maxHr);
        if (minHr != null) session.setMinHeartRate(minHr);
        if (totalAscent >= 0) {
            session.setTotalAscent(totalAscent);
            float frac = (float) (ascentDescentM[0] - totalAscent);
            if (frac > 0) session.setTotalFractionalAscent(frac);
        }
        if (totalDescent >= 0) {
            session.setTotalDescent(totalDescent);
            float frac = (float) (ascentDescentM[1] - totalDescent);
            if (frac > 0) session.setTotalFractionalDescent(frac);
        }
        // 最高/最低海拔：SIMPLIFY 统计块的 mMaxAlti/mMinAlti（单位米）为设备端权威值，
        // 即使 alti 通道缺失也存在，故 session 一律取该统计值
        Float[] sessionAlti = sessionMinMaxAltitude(record);
        if (sessionAlti[1] != null) session.setEnhancedMaxAltitude(sessionAlti[1]);
        if (sessionAlti[0] != null) session.setEnhancedMinAltitude(sessionAlti[0]);
        float sessionAvgSpeed = avgSpeedOf((float) totalDistanceM, (float) totalTimeSec, avgMaxSpeed[0]);
        if (sessionAvgSpeed > 0) session.setEnhancedAvgSpeed(sessionAvgSpeed);
        if (avgMaxSpeed[1] > 0) session.setEnhancedMaxSpeed(avgMaxSpeed[1]);
        // 步频：SIMPLIFY 统计块的 avgStepRate/bestStepRate 为设备端权威值（步/分），
        // 按 Record 同样的口径换算（跑走折半为 rpm），缺失时回落为逐点自算
        applySessionCadence(session, record, sportType, avgMaxCadence);
        Long totalCycles = calcTotalCycles(record);
        if (totalCycles != null) session.setTotalCycles(totalCycles);
        if (startEndPos[0] != INVALID_POSITION) session.setStartPositionLat(startEndPos[0]);
        if (startEndPos[1] != INVALID_POSITION) session.setStartPositionLong(startEndPos[1]);
        if (startEndPos[2] != INVALID_POSITION) session.setEndPositionLat(startEndPos[2]);
        if (startEndPos[3] != INVALID_POSITION) session.setEndPositionLong(startEndPos[3]);
        // session 由「活动结束」触发，并以 SESSION/STOP 事件对收尾（与佳明一致）
        session.setEvent(Event.SESSION);
        session.setEventType(EventType.STOP);
        session.setTrigger(SessionTrigger.ACTIVITY_END);
        session.setSportProfileName(HuaweiSportType.getByCode(record.getSportType()).getNameCn());
        // GPS 轨迹边界框：东北角（nec）/ 西南角（swc）
        int[] bbox = calcBoundingBox(gpsPoints);
        if (bbox != null) {
            session.setNecLat(bbox[0]);
            session.setNecLong(bbox[1]);
            session.setSwcLat(bbox[2]);
            session.setSwcLong(bbox[3]);
        }
        applyTrainingEffect(session, record);
        if (interruptTimesField != null) session.addDeveloperField(interruptTimesField);
        if (maxConsecutiveJumpsField != null) session.addDeveloperField(maxConsecutiveJumpsField);
        encoder.write(session);

        // 7. SetMesg（力量/自由训练的组）或 LapMesg（其余运动的圈）
        if (!setSegments.isEmpty()) {
            writeSets(encoder, setSegments, startTimeMs);
        } else if (lapSegments.isEmpty()) {
            // 无有效距离与分段信息时退化为单 Lap
            writeLap(encoder, record, startTimeMs, endTimeMs, (float) elapsedSec, (float) totalTimeSec,
                    (float) totalDistanceM, totalCalories, avgHr, maxHr,
                    totalAscent, totalDescent, null, null, avgMaxSpeed, avgMaxCadence,
                    startEndPos, LapTrigger.SESSION_END, 0);
        } else {
            for (int i = 0; i < lapSegments.size(); i++) {
                LapSegment seg = lapSegments.get(i);
                LapTrigger trigger = (i == lapSegments.size() - 1)
                        ? LapTrigger.SESSION_END
                        : LapTrigger.DISTANCE;
                writeLap(encoder, record, seg.startTimeMs, seg.endTimeMs,
                        seg.elapsedTimeSec, seg.timerTimeSec, seg.distanceM, seg.calories,
                        seg.avgHr, seg.maxHr, seg.ascent, seg.descent,
                        seg.minAltitude, seg.maxAltitude,
                        seg.avgMaxSpeed, seg.avgMaxCadence,
                        seg.startEndPos, trigger, i);
            }
        }

        // 8. EventMesg（开始）
        // 华为数据无暂停/继续的时间点信息，故只有一对 TIMER START/STOP_ALL；
        // 计时触发方式按手动开始计（华为运动需用户手动点开始）。
        EventMesg eventStart = new EventMesg();
        eventStart.setTimestamp(new DateTime(new Date(startTimeMs)));
        eventStart.setEvent(Event.TIMER);
        eventStart.setEventType(EventType.START);
        eventStart.setTimerTrigger(TimerTrigger.MANUAL);
        encoder.write(eventStart);

        // 9. RecordMesg
        for (RecordMesg rec : records) {
            encoder.write(rec);
        }

        // 10. EventMesg（结束）
        EventMesg eventStop = new EventMesg();
        eventStop.setTimestamp(new DateTime(new Date(endTimeMs)));
        eventStop.setEvent(Event.TIMER);
        eventStop.setEventType(EventType.STOP_ALL);
        eventStop.setTimerTrigger(TimerTrigger.MANUAL);
        encoder.write(eventStop);

        encoder.close();
    }

    /** 收集有效 GPS 点（过滤无效半圆值），按时间排序 */
    private List<TrackPoint> collectGpsPoints(List<TrackPoint> track) {
        List<TrackPoint> gpsPoints = new ArrayList<>();
        for (TrackPoint p : track) {
            if (!"lbs".equals(p.getType())) continue;
            if (isInvalidGps(p.getLat(), p.getLon())) continue;
            int[] semi = coord.toSemicircles(p.getLat(), p.getLon());
            if (semi[0] == 0 && semi[1] == 0) continue;
            gpsPoints.add(p);
        }
        gpsPoints.sort((a, b) -> Long.compare(a.getTimeMillis(), b.getTimeMillis()));
        return gpsPoints;
    }

    /**
     * 累计爬升与下降（米）。
     *
     * <p>华为 lbs 点的 alt 字段恒为 0，不可用；真实海拔在 alti 通道。
     * 而 SIMPLIFY 统计块已给出经设备端滤波的权威值（单位：分米）：
     * {@code creepingWave}=累计爬升、{@code mTotalDescent}=累计下降。
     * 实测 {@code (creepingWave - mTotalDescent)/10} 与「末海拔 − 首海拔」一致，
     * 可确认二者的字段语义与单位；该值小于逐点累加值（设备端已做滤波去抖），
     * 故直接采用，避免自行滤波引入偏差。
     *
     * @return [ascent, descent]，单位米（保留小数），无海拔数据时返回 [-1, -1] 表示不写该字段
     */
    private double[] calcAscentDescent(HuaweiSportRecord record) {
        HuaweiSimplifyData simplify = record.getSimplifyData();
        if (simplify == null) return new double[]{-1, -1};
        double ascentDm = simplify.getCreepingWave();
        double descentDm = simplify.getMTotalDescent();
        if (ascentDm <= 0 && descentDm <= 0) return new double[]{-1, -1};
        return new double[]{Math.max(ascentDm, 0) / 10.0, Math.max(descentDm, 0) / 10.0};
    }

    /** session 平均心率：优先 SIMPLIFY 的 avgHeartRate，缺失时按 h-r 逐点自算 */
    private Short sessionAvgHeartRate(HuaweiSportRecord record, List<TrackPoint> track) {
        HuaweiSimplifyData simplify = record.getSimplifyData();
        if (simplify != null) {
            double v = simplify.getAvgHeartRate();
            if (v > 0) return (short) Math.round(v);
        }
        return avgHeartRate(track);
    }

    /** session 最高心率：优先 SIMPLIFY 的 maxHeartRate，缺失时按 h-r 逐点自算 */
    private Short sessionMaxHeartRate(HuaweiSportRecord record, List<TrackPoint> track) {
        HuaweiSimplifyData simplify = record.getSimplifyData();
        if (simplify != null) {
            double v = simplify.getMaxHeartRate();
            if (v > 0) return (short) Math.round(v);
        }
        return maxHeartRate(track);
    }

    /** session 最低心率：仅来自 SIMPLIFY 的 minHeartRate，无则不写 */
    private Short sessionMinHeartRate(HuaweiSportRecord record) {
        HuaweiSimplifyData simplify = record.getSimplifyData();
        if (simplify == null) return null;
        double v = simplify.getMinHeartRate();
        return v > 0 ? (short) Math.round(v) : null;
    }

    /**
     * session 最低/最高海拔（米）。
     *
     * <p>取 SIMPLIFY 的 {@code mMinAlti}/{@code mMaxAlti}，实测与 alti 通道的极值一致，
     * 单位为米；两者同为 0 时视为无数据。
     *
     * @return [min, max]，无数据时元素为 null 表示不写
     */
    private Float[] sessionMinMaxAltitude(HuaweiSportRecord record) {
        HuaweiSimplifyData simplify = record.getSimplifyData();
        if (simplify == null) return new Float[]{null, null};
        double min = simplify.getMMinAlti();
        double max = simplify.getMMaxAlti();
        if (min == 0 && max == 0) return new Float[]{null, null};
        // 未初始化哨兵：无海拔记录的运动（如手机侧跳绳）mMinAlti=Float.MAX_VALUE、
        // mMaxAlti=Float.MIN_VALUE，表现为 min > max，此时一律不写，
        // 避免 1.4E-45 被写成 0.0 这类凭空产生的海拔
        if (min > max) return new Float[]{null, null};
        if (min < MIN_VALID_ALTITUDE_M || max > MAX_VALID_ALTITUDE_M) return new Float[]{null, null};
        return new Float[]{(float) min, (float) max};
    }

    /**
     * 写入 session 的平均/最大步频。
     *
     * <p>优先取 SIMPLIFY 的 {@code avgStepRate}/{@code bestStepRate}（步/分，设备端权威值），
     * 并与 Record 层保持同一口径：跑走类运动折半为 rpm，余数写入 fractional 字段；
     * 骑行/跳绳按原值写入。统计缺失时回落为由 Record 序列自算的值。
     *
     * <p>跳绳若 {@code avgStepRate} 为 0（手机侧记录常见），改取扩展统计中的
     * {@code skipSpeed}（次/分）作为平均频率；最大频率无对应源字段故不写
     * （{@code maxSkippingTimes} 是最大连跳「次数」而非频率，不能充当最大步频）。
     */
    private void applySessionCadence(SessionMesg session, HuaweiSportRecord record,
                                     HuaweiSportType sportType, Short[] fallback) {
        HuaweiSimplifyData simplify = record.getSimplifyData();
        double avgRaw = simplify != null ? simplify.getAvgStepRate() : 0;
        double maxRaw = simplify != null ? simplify.getBestStepRate() : 0;
        boolean halve;
        switch (sportType) {
            case RUN:
            case INDOOR_RUN:
            case TRAIL_RUN:
            case WALK:
            case INDOOR_WALK:
                halve = true;
                break;
            case CYCLE:
                halve = false;
                break;
            case JUMP_ROPE:
                halve = false;
                // 手机侧记录的跳绳没有 avgStepRate，平均频率在扩展统计的 skipSpeed（次/分）里
                if (avgRaw <= 0) {
                    Integer skipSpeed = extInt(extendTrackData(record) == null
                            ? null : extendTrackData(record).getSkipSpeed());
                    if (skipSpeed != null) avgRaw = skipSpeed;
                }
                break;
            default:
                // 其余运动类型 Record 层不写步频，session 亦不写
                return;
        }
        if (avgRaw > 0) {
            double rpm = halve ? avgRaw / 2.0 : avgRaw;
            short whole = (short) Math.floor(rpm);
            if (whole > 0) {
                session.setAvgRunningCadence(whole);
                float frac = (float) (rpm - whole);
                if (frac > 0) session.setAvgFractionalCadence(frac);
            }
        } else if (fallback[0] != null) {
            session.setAvgRunningCadence(fallback[0]);
        }
        if (maxRaw > 0) {
            double rpm = halve ? maxRaw / 2.0 : maxRaw;
            short whole = (short) Math.floor(rpm);
            if (whole > 0) {
                session.setMaxRunningCadence(whole);
                float frac = (float) (rpm - whole);
                if (frac > 0) session.setMaxFractionalCadence(frac);
            }
        } else if (fallback[1] != null) {
            session.setMaxRunningCadence(fallback[1]);
        }
    }

    /**
     * 将各圈自算的爬升/下降按占比重新标定到 SIMPLIFY 的权威总量，使 Σlap 精确等于 session。
     *
     * <p>源数据的 sec 分段不含任何海拔增量字段，分圈爬升无原始数据可取，
     * 只能由 1Hz 海拔序列自算；而自算结果因未做设备端滤波而系统性偏大。
     * 故以各圈自算量占全程自算量的比例作为权重分配总量，末圈用余量补齐。
     *
     * <p>总量缺失（-1）或全程无海拔样本时不做处理，保持原有的「不写该字段」语义。
     */
    private void rescaleLapAltitudeGain(List<LapSegment> segments, int totalAscent, int totalDescent) {
        if (segments == null || segments.isEmpty()) return;
        if (totalAscent >= 0) rescaleOne(segments, totalAscent, true);
        if (totalDescent >= 0) rescaleOne(segments, totalDescent, false);
    }

    private void rescaleOne(List<LapSegment> segments, int total, boolean ascent) {
        List<Integer> idx = new ArrayList<>();
        double rawSum = 0;
        for (int i = 0; i < segments.size(); i++) {
            LapSegment seg = segments.get(i);
            double v = ascent ? seg.rawAscent : seg.rawDescent;
            if (v < 0) continue;
            rawSum += v;
            idx.add(i);
        }
        if (idx.isEmpty()) return;
        int lastValid = idx.get(idx.size() - 1);
        if (rawSum <= 0) {
            // 全程自算量为 0（如纯平路）：总量无从分配，全部计入最后一个有效圈
            if (ascent) segments.get(lastValid).ascent = total;
            else segments.get(lastValid).descent = total;
            return;
        }
        // 最大余数法：先取各圈配额的整数部分，再把剩余的 1 米按小数部分从大到小补齐，
        // 既保证 Σlap 精确等于权威总量，又避免把舍入残差全部堆到某一圈。
        int n = idx.size();
        int[] quota = new int[n];
        double[] remainder = new double[n];
        int assigned = 0;
        for (int k = 0; k < n; k++) {
            LapSegment seg = segments.get(idx.get(k));
            double v = ascent ? seg.rawAscent : seg.rawDescent;
            double exact = total * (v / rawSum);
            quota[k] = (int) Math.floor(exact);
            remainder[k] = exact - quota[k];
            assigned += quota[k];
        }
        Integer[] order = new Integer[n];
        for (int k = 0; k < n; k++) order[k] = k;
        Arrays.sort(order, (a, b) -> Double.compare(remainder[b], remainder[a]));
        int left = total - assigned;
        for (int k = 0; k < left && k < n; k++) quota[order[k]]++;
        for (int k = 0; k < n; k++) {
            LapSegment seg = segments.get(idx.get(k));
            if (ascent) seg.ascent = quota[k];
            else seg.descent = quota[k];
        }
    }

    /** 取 SIMPLIFY 块中的扩展统计 mExtendTrackDataMap，缺失返回 null */
    private ExtendTrackData extendTrackData(HuaweiSportRecord record) {
        HuaweiSimplifyData simplify = record.getSimplifyData();
        return simplify == null ? null : simplify.getMExtendTrackDataMap();
    }

    /** 扩展统计里的数值均以字符串存储，解析为正整数；非正或非法返回 null（即「无此数据」） */
    private Integer extInt(String raw) {
        if (raw == null) return null;
        try {
            int v = Integer.parseInt(raw.trim());
            return v > 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 同 {@link #extInt}，但允许 0（如「中断次数 0」本身即有效信息） */
    private Integer extNonNegInt(String raw) {
        if (raw == null) return null;
        try {
            int v = Integer.parseInt(raw.trim());
            return v >= 0 ? v : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 构造本转换器的 developer_data_id 消息（developer_data_index 固定为 0）。
     * application_id 是本转换器自身的标识常量，与运动数据无关。
     */
    private DeveloperDataIdMesg newDeveloperDataId() {
        DeveloperDataIdMesg mesg = new DeveloperDataIdMesg();
        for (int i = 0; i < APPLICATION_ID.length; i++) {
            mesg.setApplicationId(i, APPLICATION_ID[i]);
        }
        mesg.setDeveloperDataIndex((short) 0);
        mesg.setApplicationVersion((long) CONVERTER_SOFTWARE_VERSION);
        return mesg;
    }

    /** 构造挂在 SessionMesg 上的自定义字段描述（uint16 计数类） */
    private FieldDescriptionMesg newFieldDescription(int fieldDefinitionNumber, String name, String units) {
        FieldDescriptionMesg fd = new FieldDescriptionMesg();
        fd.setDeveloperDataIndex((short) 0);
        fd.setFieldDefinitionNumber((short) fieldDefinitionNumber);
        fd.setFitBaseTypeId((short) Fit.BASE_TYPE_UINT16);
        fd.setFieldName(0, name);
        fd.setUnits(0, units);
        fd.setNativeMesgNum(MesgNum.SESSION);
        return fd;
    }

    /**
     * 计算 total_cycles（FIT field 10，跑走场景别名为 total_strides）。
     *
     * <p>注意：该字段语义是「周期数」而非「步数」。
     * 跑步/步行一个周期 = 两步，故为 totalSteps/2；
     * 跳绳的周期数优先取 SIMPLIFY 扩展统计中的 skipNum（设备端权威总个数），
     * 缺失时才由 scp 频率（次/分）在 1Hz 轴上积分得到；
     * 骑行/力量/自由训练/爬楼无可靠周期数，不写该字段。
     *
     * @return 周期数，null 表示不写
     */
    private Long calcTotalCycles(HuaweiSportRecord record) {
        HuaweiSportType type = HuaweiSportType.getByCode(record.getSportType());
        switch (type) {
            case RUN:
            case INDOOR_RUN:
            case TRAIL_RUN:
            case WALK:
            case INDOOR_WALK: {
                long steps = record.getTotalSteps();
                return steps > 0 ? steps / 2 : null;
            }
            case JUMP_ROPE: {
                // 优先取设备端权威的跳绳总个数（SIMPLIFY.mExtendTrackDataMap.skipNum），
                // 无该字段时才用 scp 频率通道积分
                Integer skipNum = extInt(extendTrackData(record) == null
                        ? null : extendTrackData(record).getSkipNum());
                if (skipNum != null) return skipNum.longValue();
                long count = countJumpRope(record);
                return count > 0 ? count : null;
            }
            default:
                return null;
        }
    }

    /**
     * 由 scp 通道（跳绳频率，次/分）积分出总跳绳次数。
     * 相邻采样点按梯形法累加：次数 += 均值(次/分) / 60 × 间隔秒。
     */
    private long countJumpRope(HuaweiSportRecord record) {
        List<TrackPoint> track = record.getTrackPoints();
        if (track == null) return 0;
        List<TrackPoint> scp = new ArrayList<>();
        for (TrackPoint p : track) {
            if ("scp".equals(p.getType())) scp.add(p);
        }
        if (scp.size() < 2) return 0;
        scp.sort((a, b) -> Long.compare(TrackPoint.normalizeToMillis(a.getK()),
                TrackPoint.normalizeToMillis(b.getK())));
        double total = 0;
        for (int i = 1; i < scp.size(); i++) {
            long t0 = TrackPoint.normalizeToMillis(scp.get(i - 1).getK()) / 1000L;
            long t1 = TrackPoint.normalizeToMillis(scp.get(i).getK()) / 1000L;
            long gap = t1 - t0;
            if (gap <= 0 || gap > 60) continue;
            double v0 = Math.max(scp.get(i - 1).getV(), 0);
            double v1 = Math.max(scp.get(i).getV(), 0);
            total += (v0 + v1) / 2.0 / 60.0 * gap;
        }
        return Math.round(total);
    }

    /**
     * 平均速度（米/秒）：按佳明语义取「总距离 / 总时长」。
     * 不能用 Record 逐点速度的算术平均——逐点平均会跳过/淡化停驻与低速段，
     * 使结果系统性偏高（实测骑行会从 8.2 km/h 虚高到 13 km/h 以上）。
     * 无距离或无时长的运动（如力量训练）回退到逐点平均速度，仍是源数据的真实值。
     */
    private float avgSpeedOf(float distanceM, float timeSec, float fallbackAvg) {
        if (distanceM > 0 && timeSec > 0) return distanceM / timeSec;
        return fallbackAvg;
    }

    /** 从 Record 序列计算平均/最大速度（米/秒），[0]=逐点平均（仅作回退），[1]=最大 */
    private float[] calcAvgMaxSpeed(List<RecordMesg> records) {
        float sum = 0;
        int count = 0;
        float max = 0;
        for (RecordMesg rec : records) {
            Float speed = rec.getEnhancedSpeed();
            if (speed != null && speed > 0) {
                sum += speed;
                count++;
                if (speed > max) max = speed;
            }
        }
        float avg = count > 0 ? sum / count : 0;
        return new float[]{avg, max};
    }

    /** 从 Record 序列计算平均/最大跑步步频（步/分） */
    private Short[] calcAvgMaxCadence(List<RecordMesg> records) {
        long sum = 0;
        int count = 0;
        short max = 0;
        for (RecordMesg rec : records) {
            Short cadence = rec.getCadence();
            if (cadence != null && cadence > 0) {
                sum += cadence;
                count++;
                if (cadence > max) max = cadence;
            }
        }
        Short avg = count > 0 ? (short) (sum / count) : null;
        Short maxVal = max > 0 ? max : null;
        return new Short[]{avg, maxVal};
    }

    /**
     * 计算 GPS 轨迹的边界框（半圆值）。
     *
     * @return [necLat, necLong, swcLat, swcLong]，无有效 GPS 点时返回 null 表示不写
     */
    private int[] calcBoundingBox(List<TrackPoint> gpsPoints) {
        if (gpsPoints == null || gpsPoints.isEmpty()) return null;
        int maxLat = Integer.MIN_VALUE;
        int maxLon = Integer.MIN_VALUE;
        int minLat = Integer.MAX_VALUE;
        int minLon = Integer.MAX_VALUE;
        for (TrackPoint p : gpsPoints) {
            int[] semi = coord.toSemicircles(p.getLat(), p.getLon());
            int lat = semi[0];
            int lon = semi[1];
            if (lat > maxLat) maxLat = lat;
            if (lon > maxLon) maxLon = lon;
            if (lat < minLat) minLat = lat;
            if (lon < minLon) minLon = lon;
        }
        return new int[]{maxLat, maxLon, minLat, minLon};
    }

    /**
     * 写入训练效果字段。
     *
     * <p>数据来自华为 SIMPLIFY 块的 wearSportData：
     * {@code etraining_effect}=有氧训练效果、
     * {@code anaerobic_exercise_etraining_effect}=无氧训练效果，
     * 原值为实际值的 10 倍（如 35 → 3.5）。
     * 值为 0 表示设备未给出评估，此时不写该字段，避免伪造生理指标。
     * 功率、训练负荷、跑姿等华为完全没有的指标一律不写。
     */
    private void applyTrainingEffect(SessionMesg session, HuaweiSportRecord record) {
        HuaweiSimplifyData simplify = record.getSimplifyData();
        if (simplify == null) return;
        WearSportData wear = simplify.getWearSportData();
        if (wear == null) return;
        long aerobic = wear.getEtrainingEffect();
        long anaerobic = wear.getAnaerobicExerciseEtrainingEffect();
        if (aerobic > 0) session.setTotalTrainingEffect(aerobic / 10.0f);
        if (anaerobic > 0) session.setTotalAnaerobicTrainingEffect(anaerobic / 10.0f);
    }

    /** 从首末 GPS 点计算起终点位置（半圆值），无有效点时返回哨兵值 */
    private int[] calcStartEndPosition(List<TrackPoint> gpsPoints) {
        int[] result = new int[]{INVALID_POSITION, INVALID_POSITION, INVALID_POSITION, INVALID_POSITION};
        if (gpsPoints.isEmpty()) return result;
        TrackPoint first = gpsPoints.get(0);
        TrackPoint last = gpsPoints.get(gpsPoints.size() - 1);
        int[] firstSemi = coord.toSemicircles(first.getLat(), first.getLon());
        int[] lastSemi = coord.toSemicircles(last.getLat(), last.getLon());
        result[0] = firstSemi[0];
        result[1] = firstSemi[1];
        result[2] = lastSemi[0];
        result[3] = lastSemi[1];
        return result;
    }

    /**
     * 判断华为 lbs 点是否为无效 GPS 哨兵点（原始度 lat=90.0, lon=-80.0）。
     * 浮点序列化可能带微小误差，故用容差比较而非等值比较。
     */
    private boolean isInvalidGps(double latDeg, double lonDeg) {
        return Math.abs(latDeg - INVALID_LAT_DEG) < GPS_SENTINEL_EPS
                && Math.abs(lonDeg - INVALID_LON_DEG) < GPS_SENTINEL_EPS;
    }

    /**
     * 解析时区字符串，返回相对 UTC 的秒偏移。
     * 优先按 {@code +0800} / {@code GMT+08:00} / {@code UTC+8} 等偏移形式解析，
     * 失败再尝试按区域名（如 Asia/Shanghai）在 {@code utcMs} 时刻的实际偏移（已含夏令时）解析，
     * 仍失败则返回 0（视为 UTC）。
     */
    private int timeZoneOffsetSeconds(String timeZone, long utcMs) {
        if (timeZone == null || timeZone.isEmpty()) return 0;
        try {
            return parseTimeZoneOffset(timeZone);
        } catch (Exception e) {
            try {
                // 区域名形式：按该时刻的实际偏移（已含夏令时）换算
                java.time.ZoneId zone = java.time.ZoneId.of(timeZone.trim());
                return zone.getRules()
                        .getOffset(java.time.Instant.ofEpochMilli(utcMs))
                        .getTotalSeconds();
            } catch (Exception ignored) {
                return 0;
            }
        }
    }

    /**
     * 解析时区偏移字符串为秒偏移。
     * 支持紧凑形式 {@code +0800} / {@code +08}，以及带冒号与前缀的 {@code GMT+08:00}、{@code UTC+8}。
     * 注意：华为原始数据里 {@code timeZone} 就是 {@code +0800} 这种无冒号形式，
     * 若按 {@code split(":")} 直接取整会把 {@code 0800} 当成 800 小时，必须先按长度拆分时/分。
     */
    private int parseTimeZoneOffset(String timeZone) {
        String tz = timeZone.trim().toUpperCase();
        if (tz.startsWith("GMT") || tz.startsWith("UTC")) {
            tz = tz.substring(3);
        }
        tz = tz.trim();
        if (tz.isEmpty()) return 0;
        int sign = 1;
        if (tz.startsWith("+")) {
            tz = tz.substring(1);
        } else if (tz.startsWith("-")) {
            sign = -1;
            tz = tz.substring(1);
        }
        tz = tz.trim();
        int hours;
        int minutes;
        if (tz.contains(":")) {
            String[] parts = tz.split(":");
            hours = Integer.parseInt(parts[0].trim());
            minutes = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
        } else if (tz.length() > 2) {
            // 紧凑形式 HHMM（如 0800、0930）
            hours = Integer.parseInt(tz.substring(0, tz.length() - 2));
            minutes = Integer.parseInt(tz.substring(tz.length() - 2));
        } else {
            hours = Integer.parseInt(tz);
            minutes = 0;
        }
        if (hours > 18 || minutes > 59) {
            throw new IllegalArgumentException("非法时区偏移: " + timeZone);
        }
        return sign * (hours * 3600 + minutes * 60);
    }

    /** Haversine 距离（米） */
    private double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    private Sport toSport(int sportType) {
        HuaweiSportType type = HuaweiSportType.getByCode(sportType);
        switch (type) {
            case CYCLE: return Sport.CYCLING;          // 骑行
            case RUN: return Sport.RUNNING;            // 户外跑步
            case WALK: return Sport.WALKING;           // 户外步行
            case INDOOR_RUN: return Sport.RUNNING;     // 室内跑步
            case TRAIL_RUN: return Sport.RUNNING;      // 越野跑
            case INDOOR_WALK: return Sport.WALKING;    // 室内步行
            case STAIRS_CLIMB: return Sport.FLOOR_CLIMBING; // 爬楼
            case STRENGTH_TRAINING: return Sport.TRAINING;  // 力量训练
            case FREE_TRAINING: return Sport.TRAINING;      // 自由训练
            case JUMP_ROPE: return Sport.JUMP_ROPE;         // 跳绳
            default: return Sport.GENERIC;
        }
    }

    private SubSport toSubSport(int sportType) {
        HuaweiSportType type = HuaweiSportType.getByCode(sportType);
        switch (type) {
            case CYCLE: return SubSport.GENERIC;          // 骑行
            case RUN: return SubSport.GENERIC;            // 户外跑步（与佳明户外跑一致用 GENERIC）
            case WALK: return SubSport.GENERIC;           // 户外步行
            case INDOOR_RUN: return SubSport.TREADMILL;   // 室内跑步
            case TRAIL_RUN: return SubSport.TRAIL;        // 越野跑
            case INDOOR_WALK: return SubSport.INDOOR_WALKING; // 室内步行
            case STAIRS_CLIMB: return SubSport.GENERIC;   // 爬楼（爬楼梯，非室内攀岩）
            case STRENGTH_TRAINING: return SubSport.STRENGTH_TRAINING; // 力量训练
            case FREE_TRAINING: return SubSport.CARDIO_TRAINING; // 自由训练（对齐佳明）
            case JUMP_ROPE: return SubSport.GENERIC;         // 跳绳
            default: return SubSport.GENERIC;
        }
    }

    private Short avgHeartRate(List<TrackPoint> points) {
        long sum = 0;
        int count = 0;
        for (TrackPoint p : points) {
            if ("h-r".equals(p.getType()) && p.getV() > 0) {
                sum += (long) p.getV();
                count++;
            }
        }
        return count > 0 ? (short) (sum / count) : null;
    }

    private Short maxHeartRate(List<TrackPoint> points) {
        long max = 0;
        for (TrackPoint p : points) {
            if ("h-r".equals(p.getType()) && p.getV() > max) {
                max = (long) p.getV();
            }
        }
        return max > 0 ? (short) max : null;
    }

    /**
     * 单个 Lap 段。
     */
    private static class LapSegment {
        long startTimeMs;
        long endTimeMs;
        float elapsedTimeSec;
        /** 华为 partTimeMap 给出的本圈净计时（秒），<=0 表示源数据未给出，回退为墙钟时长 */
        float timerTimeSec;
        float distanceM;
        long calories;
        Short avgHr;
        Short maxHr;
        int ascent;
        int descent;
        /** 本圈自算爬升的未取整值（米），仅用于分圈权重分配，-1 表示无海拔样本 */
        double rawAscent = -1;
        /** 本圈自算下降的未取整值（米），仅用于分圈权重分配，-1 表示无海拔样本 */
        double rawDescent = -1;
        Float minAltitude;
        Float maxAltitude;
        float[] avgMaxSpeed;   // [0]=avg, [1]=max
        Short[] avgMaxCadence; // [0]=avg, [1]=max
        int[] startEndPos;     // [lat0, lon0, lat1, lon1] 半圆值
    }

    /**
     * 按运动类型取自动计圈分段距离（米）。
     *
     * <p>佳明 Auto Lap 的默认分段距离按运动类型区分：跑步/步行为 1 km，骑行为 5 km。
     * 华为 partTimeMap 一律按公里记录，转换时需按本方法的分段距离重新聚合。</p>
     */
    private float lapDistanceM(int sportType) {
        return HuaweiSportType.getByCode(sportType) == HuaweiSportType.CYCLE
                ? LAP_DISTANCE_CYCLING_M
                : LAP_DISTANCE_DEFAULT_M;
    }

    /**
     * 切分 Lap 段。
     *
     * <p>优先采用华为设备端的分段用时 {@code partTimeMap}（key=公里数，value=到该公里的累计秒），
     * 这是设备自身的自动计圈边界；由于华为一律按每公里记录，这里只取
     * {@link #lapDistanceM(int)} 整数倍的公里边界，使分圈距离与佳明的 Auto Lap 默认值一致；
     * 末段不足一圈仍单独成圈；
     * 当 partTimeMap 为空或无可用边界时退化为按累计距离切分；
     * 再无有效距离时返回空列表，由调用方退化为单 Lap。</p>
     */
    private List<LapSegment> buildLapSegments(List<RecordMesg> records, HuaweiSportRecord record,
                                              long totalCalories, long totalTimeSec) {
        float lapDistM = lapDistanceM(record.getSportType());
        List<LapSegment> byPartTime = buildLapSegmentsByPartTime(records, record, totalCalories, totalTimeSec, lapDistM);
        if (!byPartTime.isEmpty()) return byPartTime;
        return buildLapSegmentsByDistance(records, totalCalories, totalTimeSec, lapDistM);
    }

    /**
     * 按 partTimeMap 的累计用时边界切分 Lap。
     *
     * <p>只保留公里数为 {@code lapDistM} 整数倍的边界；边界以 Record 时间轴起点为基准的秒偏移比较。
     * 每圈的距离取两个整公里边界之差（即 {@code lapDistM} 整数），而不是 Record 累计距离之差：
     * partTimeMap 的公里标记是设备自身的距离刻度，而 Record 距离是由速度/轨迹积分再归一化得到的
     * 近似值，两者会有 1%~2% 漂移；按距离自动计圈的圈距在定义上就是整数的分圈距离，
     * 这与佳明原生 FIT 每圈 5000 m 整的表现一致。末段不足一圈仍单独成圈，其距离为总距离减去
     * 最后一个整公里边界。</p>
     */
    private List<LapSegment> buildLapSegmentsByPartTime(List<RecordMesg> records, HuaweiSportRecord record,
                                                        long totalCalories, long totalTimeSec, float lapDistM) {
        List<LapSegment> segments = new ArrayList<>();
        if (records == null || records.isEmpty()) return segments;
        HuaweiSimplifyData simplify = record.getSimplifyData();
        if (simplify == null) return segments;
        List<PartTimeEntry> entries = simplify.getPartTimeMap();
        if (entries == null || entries.isEmpty()) return segments;

        double lapKm = lapDistM / 1000.0;
        // 分圈边界：[0]=公里数，[1]=该公里处的累计秒
        List<double[]> bounds = new ArrayList<>();
        for (PartTimeEntry e : entries) {
            if (e.getElapsedTime() <= 0) continue;
            Double km = parseLapBoundaryKm(e.getDistance(), lapKm);
            if (km == null) continue;
            bounds.add(new double[]{km, e.getElapsedTime()});
        }
        if (bounds.isEmpty()) return segments;
        bounds.sort(Comparator.comparingDouble(b -> b[0]));

        long baseMs = records.get(0).getTimestamp().getDate().getTime();
        List<List<RecordMesg>> groups = new ArrayList<>();
        // 每组结束处的公里数；null 表示该组一直到活动终点（末段）
        List<Double> groupEndKm = new ArrayList<>();
        // 每组结束处的累计用时（秒，华为设备净计时）；null 表示该组一直到活动终点
        List<Double> groupEndSec = new ArrayList<>();
        List<RecordMesg> current = new ArrayList<>();
        int idx = 0;
        for (RecordMesg rec : records) {
            double offsetSec = (rec.getTimestamp().getDate().getTime() - baseMs) / 1000.0;
            int next = idx;
            while (next < bounds.size() && offsetSec > bounds.get(next)[1]) next++;
            if (next > idx && !current.isEmpty()) {
                groups.add(current);
                // 本组覆盖到的最后一个边界
                groupEndKm.add(bounds.get(next - 1)[0]);
                groupEndSec.add(bounds.get(next - 1)[1]);
                current = new ArrayList<>();
            }
            idx = next;
            current.add(rec);
        }
        if (!current.isEmpty()) {
            groups.add(current);
            groupEndKm.add(null);
            groupEndSec.add(null);
        }

        double totalKm = record.getTotalDistance() / 1000.0;
        // partTimeMap 末尾的整公里边界常与终点几乎重合（如骑行整 15 公里），
        // 此时末段余下距离接近 0，并入上一圈避免 0 米碎圈；余下距离足够时末段单独成圈。
        int lastIdx = groups.size() - 1;
        if (groups.size() > 1 && groupEndKm.get(lastIdx) == null) {
            Double prevBoundKm = groupEndKm.get(lastIdx - 1);
            double tailM = prevBoundKm != null ? (totalKm - prevBoundKm) * 1000.0 : Double.MAX_VALUE;
            if (totalKm <= 0 || tailM < MIN_TAIL_LAP_M) {
                List<RecordMesg> tail = groups.remove(lastIdx);
                groupEndKm.remove(lastIdx);
                groupEndSec.remove(lastIdx);
                groups.get(groups.size() - 1).addAll(tail);
                // 上一圈顺延到终点，保证各圈距离之和等于总距离；
                // 其净计时仍沿用该公里边界的累计用时（华为 App 的圈速即以此为准）
                groupEndKm.set(groups.size() - 1, null);
            }
        }

        RecordMesg prevEnd = null;
        double prevKm = 0;
        double prevSec = 0;
        for (int i = 0; i < groups.size(); i++) {
            List<RecordMesg> g = groups.get(i);
            LapSegment seg = buildSegment(g, prevEnd, totalCalories, totalTimeSec);
            if (seg != null) {
                Double endKm = groupEndKm.get(i);
                // 末段（endKm 为 null）需要总距离才能定出圈距；无总距离时沿用 Record 推算值
                if (endKm != null || totalKm > 0) {
                    double segEndKm = endKm != null ? endKm : totalKm;
                    double distM = (segEndKm - prevKm) * 1000.0;
                    if (distM > 0) seg.distanceM = (float) distM;
                    prevKm = segEndKm;
                }
                // 本圈净计时取华为累计用时之差；末段无边界时用总计时时长收口
                Double endSec = groupEndSec.get(i);
                if (endSec != null || totalTimeSec > 0) {
                    double segEndSec = endSec != null ? endSec : totalTimeSec;
                    if (segEndSec > prevSec) seg.timerTimeSec = (float) (segEndSec - prevSec);
                    prevSec = segEndSec;
                }
                segments.add(seg);
            }
            prevEnd = g.get(g.size() - 1);
        }
        return segments;
    }

    /**
     * 解析 partTimeMap 的一条公里数边界，仅当它落在分圈距离的整数倍上时返回该公里数。
     *
     * <p>{@code distanceKm} 为华为原始字符串（如 "1.0"、"21.0975"）。
     * 非数字、非正数，或不是 {@code lapKm} 整数倍的边界（含末尾的终点距离）一律返回 null。</p>
     */
    private Double parseLapBoundaryKm(String distanceKm, double lapKm) {
        if (distanceKm == null || lapKm <= 0) return null;
        double km;
        try {
            km = Double.parseDouble(distanceKm.trim());
        } catch (NumberFormatException e) {
            return null;
        }
        if (km <= 0) return null;
        double ratio = km / lapKm;
        if (Math.abs(ratio - Math.rint(ratio)) >= 1e-6) return null;
        // 归一到精确的整数倍，消除字符串本身的浮点误差
        return Math.rint(ratio) * lapKm;
    }

    /**
     * 按 Record 的累计距离每 {@code lapDistM} 米切分为多个 Lap 段。
     *
     * <p>每个完整圈的距离取整数的 {@code lapDistM}（与按距离自动计圈的定义一致），
     * 末段不足一圈仍单独成圈，距离为总距离减去已完成的整圈距离。
     * 若累计距离不足一段，则返回单段（覆盖全部记录）。
     * 无有效距离时返回空列表，由调用方退化为单 Lap。</p>
     */
    private List<LapSegment> buildLapSegmentsByDistance(List<RecordMesg> records,
                                                        long totalCalories, long totalTimeSec,
                                                        float lapDistM) {
        List<LapSegment> segments = new ArrayList<>();
        if (records == null || records.isEmpty()) return segments;

        // 找到最大累计距离，判断是否需要分段
        float maxDist = 0;
        for (RecordMesg rec : records) {
            Float d = rec.getDistance();
            if (d != null && d > maxDist) maxDist = d;
        }
        if (maxDist < lapDistM) {
            // 不足一个分圈距离，单段
            LapSegment seg = buildSegment(records, null, totalCalories, totalTimeSec);
            if (seg != null) segments.add(seg);
            return segments;
        }

        // 按每 lapDistM 切分：当前段的累计距离上限
        float segUpper = lapDistM;
        List<List<RecordMesg>> groups = new ArrayList<>();
        // 每组结束处的累计距离（整数倍分圈距离）；null 表示该组一直到活动终点（末段）
        List<Float> groupEndM = new ArrayList<>();
        List<RecordMesg> current = new ArrayList<>();
        for (RecordMesg rec : records) {
            Float d = rec.getDistance();
            float dist = d != null ? d : 0;
            if (dist > segUpper && !current.isEmpty()) {
                // 当前点越过该段上限，封段
                groups.add(current);
                current = new ArrayList<>();
                // 推进上限到下一个分圈距离
                while (segUpper < dist) segUpper += lapDistM;
                groupEndM.add(segUpper - lapDistM);
            }
            current.add(rec);
        }
        if (!current.isEmpty()) {
            groups.add(current);
            groupEndM.add(null);
        }

        // 末段余下距离接近 0 时并入上一圈，避免碎圈
        int lastIdx = groups.size() - 1;
        if (groups.size() > 1 && groupEndM.get(lastIdx) == null) {
            Float prevBoundM = groupEndM.get(lastIdx - 1);
            if (prevBoundM != null && maxDist - prevBoundM < MIN_TAIL_LAP_M) {
                List<RecordMesg> tail = groups.remove(lastIdx);
                groupEndM.remove(lastIdx);
                groups.get(groups.size() - 1).addAll(tail);
                groupEndM.set(groups.size() - 1, null);
            }
        }

        RecordMesg prevEnd = null;
        float prevM = 0;
        for (int i = 0; i < groups.size(); i++) {
            List<RecordMesg> g = groups.get(i);
            LapSegment seg = buildSegment(g, prevEnd, totalCalories, totalTimeSec);
            if (seg != null) {
                Float endM = groupEndM.get(i);
                float segEndM = endM != null ? endM : maxDist;
                float distM = segEndM - prevM;
                if (distM > 0) seg.distanceM = distM;
                prevM = segEndM;
                segments.add(seg);
            }
            prevEnd = g.get(g.size() - 1);
        }
        return segments;
    }

    /**
     * 由一段 Record 序列计算 LapSegment 的各字段。
     *
     * @param recs     本段内的 Record（不含上一段末点）
     * @param prevEnd  上一段的末点，用作本段的时间/距离基准，使相邻圈首尾相接（首段传 null）
     */
    private LapSegment buildSegment(List<RecordMesg> recs, RecordMesg prevEnd,
                                    long totalCalories, long totalTimeSec) {
        if (recs.isEmpty()) return null;
        LapSegment seg = new LapSegment();

        RecordMesg first = recs.get(0);
        RecordMesg last = recs.get(recs.size() - 1);
        // 基准点：有上一段末点时以其为起点，保证圈与圈之间时间与距离无空隙
        RecordMesg base = prevEnd != null ? prevEnd : first;

        long startFitSec = base.getTimestamp().getTimestamp();
        long endFitSec = last.getTimestamp().getTimestamp();
        // getTimestamp() 返回 FIT 秒（1989-12-31 基准），getDate() 转回 Unix 毫秒
        seg.startTimeMs = base.getTimestamp().getDate().getTime();
        seg.endTimeMs = last.getTimestamp().getDate().getTime();
        seg.elapsedTimeSec = (float) (endFitSec - startFitSec);

        Float lastDist = last.getDistance();
        Float baseDist = base.getDistance();
        float d0 = baseDist != null ? baseDist : 0;
        float d1 = lastDist != null ? lastDist : 0;
        seg.distanceM = d1 - d0;

        // 卡路里：源数据只有总卡路里，按本段时长占总时长的比例分摊
        if (totalTimeSec > 0 && totalCalories > 0 && seg.elapsedTimeSec > 0) {
            seg.calories = Math.round(totalCalories * (seg.elapsedTimeSec / (double) totalTimeSec));
        } else {
            seg.calories = 0;
        }

        // 心率
        long hrSum = 0;
        int hrCount = 0;
        short hrMax = 0;
        for (RecordMesg rec : recs) {
            Short hr = rec.getHeartRate();
            if (hr != null && hr > 0) {
                hrSum += hr;
                hrCount++;
                if (hr > hrMax) hrMax = hr;
            }
        }
        seg.avgHr = hrCount > 0 ? (short) (hrSum / hrCount) : null;
        seg.maxHr = hrMax > 0 ? hrMax : null;

        // 爬升/下降：由 1Hz 海拔序列累加。
        // 注意：海拔可能为负（低于海平面），不能用 alt<=0 判无效，
        // 只排除明显越界值；整段无海拔样本时置 -1 表示不写该字段。
        //
        // 差分必须以 double 全程累加后再取整：相邻秒的海拔差通常只有 0.1~0.4 m，
        // 若逐点 Math.round 会被全部舍为 0，使平缓路段的自算爬升恒等于 0，
        // 进而让 rescaleOne 的分圈权重严重失真（爬升被全部挤到陡坡圈）。
        double ascent = 0;
        double descent = 0;
        double prevAlt = Double.NaN;
        boolean hasAlt = false;
        float minAlt = Float.MAX_VALUE;
        float maxAlt = -Float.MAX_VALUE;
        for (RecordMesg rec : recs) {
            Float alt = rec.getEnhancedAltitude();
            if (alt == null || alt < MIN_VALID_ALTITUDE_M || alt > MAX_VALID_ALTITUDE_M) continue;
            hasAlt = true;
            if (alt < minAlt) minAlt = alt;
            if (alt > maxAlt) maxAlt = alt;
            if (!Double.isNaN(prevAlt)) {
                double diff = alt - prevAlt;
                if (diff > 0) ascent += diff;
                else if (diff < 0) descent += -diff;
            }
            prevAlt = alt;
        }
        seg.rawAscent = hasAlt ? ascent : -1;
        seg.rawDescent = hasAlt ? descent : -1;
        seg.ascent = hasAlt ? (int) Math.round(ascent) : -1;
        seg.descent = hasAlt ? (int) Math.round(descent) : -1;
        seg.minAltitude = hasAlt ? minAlt : null;
        seg.maxAltitude = hasAlt ? maxAlt : null;

        // 速度 / 步频
        seg.avgMaxSpeed = calcAvgMaxSpeed(recs);
        seg.avgMaxCadence = calcAvgMaxCadence(recs);

        // 起终点位置（起点取基准点，使相邻圈首尾相接）
        seg.startEndPos = new int[]{INVALID_POSITION, INVALID_POSITION, INVALID_POSITION, INVALID_POSITION};
        Integer lat0 = base.getPositionLat() != null ? base.getPositionLat() : first.getPositionLat();
        Integer lon0 = base.getPositionLong() != null ? base.getPositionLong() : first.getPositionLong();
        Integer lat1 = last.getPositionLat();
        Integer lon1 = last.getPositionLong();
        if (lat0 != null && lon0 != null) {
            seg.startEndPos[0] = lat0;
            seg.startEndPos[1] = lon0;
        }
        if (lat1 != null && lon1 != null) {
            seg.startEndPos[2] = lat1;
            seg.startEndPos[3] = lon1;
        }

        return seg;
    }

    /**
     * 将 LapSegment 或整体统计写入一个 LapMesg。
     */
    private void writeLap(FileEncoder encoder, HuaweiSportRecord record,
                          long startMs, long endMs, float elapsedSec, float timerSec, float distanceM,
                          long calories, Short avgHr, Short maxHr,
                          int ascent, int descent, Float minAlt, Float maxAlt,
                          float[] avgMaxSpeed, Short[] avgMaxCadence,
                          int[] startEndPos, LapTrigger trigger, int messageIndex) {
        // 墙钟时长用于 total_elapsed_time，华为分段净计时用于 total_timer_time；
        // 源数据未给出分段净计时时两者同值。
        float timer = timerSec > 0 ? timerSec : elapsedSec;
        float elapsed = Math.max(elapsedSec, timer);
        LapMesg lap = new LapMesg();
        lap.setTimestamp(new DateTime(new Date(endMs)));
        lap.setStartTime(new DateTime(new Date(startMs)));
        lap.setMessageIndex(messageIndex);
        lap.setTotalElapsedTime(elapsed);
        lap.setTotalTimerTime(timer);
        lap.setTotalDistance(distanceM);
        lap.setTotalCalories((int) calories);
        lap.setSport(toSport(record.getSportType()));
        lap.setSubSport(toSubSport(record.getSportType()));
        // 每个圈都以 LAP/STOP 事件对收尾，强度统一为 ACTIVE（源数据无休息圈）
        lap.setEvent(Event.LAP);
        lap.setEventType(EventType.STOP);
        lap.setIntensity(Intensity.ACTIVE);
        if (avgHr != null) lap.setAvgHeartRate(avgHr);
        if (maxHr != null) lap.setMaxHeartRate(maxHr);
        if (ascent >= 0) lap.setTotalAscent(ascent);
        if (descent >= 0) lap.setTotalDescent(descent);
        if (minAlt != null) lap.setEnhancedMinAltitude(minAlt);
        if (maxAlt != null) lap.setEnhancedMaxAltitude(maxAlt);
        // 圈平均速度 = 圈距离 / 圈净计时（与华为 App 的分段配速口径一致）
        float lapAvgSpeed = avgSpeedOf(distanceM, timer, avgMaxSpeed[0]);
        if (lapAvgSpeed > 0) lap.setEnhancedAvgSpeed(lapAvgSpeed);
        if (avgMaxSpeed[1] > 0) lap.setEnhancedMaxSpeed(avgMaxSpeed[1]);
        if (avgMaxCadence[0] != null) lap.setAvgRunningCadence(avgMaxCadence[0]);
        if (avgMaxCadence[1] != null) lap.setMaxRunningCadence(avgMaxCadence[1]);
        if (startEndPos[0] != INVALID_POSITION) lap.setStartPositionLat(startEndPos[0]);
        if (startEndPos[1] != INVALID_POSITION) lap.setStartPositionLong(startEndPos[1]);
        if (startEndPos[2] != INVALID_POSITION) lap.setEndPositionLat(startEndPos[2]);
        if (startEndPos[3] != INVALID_POSITION) lap.setEndPositionLong(startEndPos[3]);
        lap.setLapTrigger(trigger);
        encoder.write(lap);
    }

    /**
     * 单个 Set 段（力量/自由训练的一组动作），由 sec 轨迹点生成。
     */
    private static class SetSegment {
        int messageIndex;
        long startTimeMs;
        float durationSec;
    }

    /**
     * 由 sec 轨迹点构建 Set 段序列（仅力量/自由训练类型调用）。
     *
     * <p>自由训练的 sec 点语义与跑步不同：{@code sn}=组序号、{@code st}=该组时长（秒）、
     * {@code shr}=组平均心率、{@code stp}=动作类型编号（非次数，故不写 repetitions）。
     * 组的起始时间由 startTime 按各组时长依次累加得到（源数据未给出每组绝对起止时刻）。
     *
     * @return 空列表表示该记录无组数据（如力量训练无 sec 点）
     */
    private List<SetSegment> buildSetSegments(HuaweiSportRecord record) {
        List<SetSegment> sets = new ArrayList<>();
        List<TrackPoint> track = record.getTrackPoints();
        if (track == null) return sets;

        List<TrackPoint> secPoints = new ArrayList<>();
        for (TrackPoint p : track) {
            if ("sec".equals(p.getType())) secPoints.add(p);
        }
        if (secPoints.isEmpty()) return sets;
        secPoints.sort((a, b) -> Long.compare(a.getSn(), b.getSn()));

        long cursorMs = record.getStartTime();
        for (TrackPoint p : secPoints) {
            long durationSec = p.getSt();
            if (durationSec <= 0) continue;
            SetSegment set = new SetSegment();
            set.messageIndex = sets.size();
            set.startTimeMs = cursorMs;
            set.durationSec = durationSec;
            sets.add(set);
            cursorMs += durationSec * 1000L;
        }
        return sets;
    }

    /**
     * 写入 SetMesg（与佳明一致：set 消息位于 session 之后，timestamp 取 session 起始时刻）。
     */
    private void writeSets(FileEncoder encoder, List<SetSegment> sets, long sessionStartMs) {
        for (SetSegment set : sets) {
            SetMesg mesg = new SetMesg();
            mesg.setMessageIndex(set.messageIndex);
            mesg.setTimestamp(new DateTime(new Date(sessionStartMs)));
            mesg.setStartTime(new DateTime(new Date(set.startTimeMs)));
            mesg.setDuration(set.durationSec);
            mesg.setSetType(SetType.ACTIVE);
            encoder.write(mesg);
        }
    }
}