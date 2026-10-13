package me.frankhbu.fitconverter.model.huawei;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 华为运动轨迹点，对应 attribute 中 HW_EXT_TRACK_DETAIL@is 内的一行。
 * 每行格式：{@code tp=<类型>;k=<键>;v=<值>;}（不同 tp 类型字段不同）。
 *
 * <p><b>存储方式</b>：内部用有序 Map 保存「原始文本」键值对，因此：</p>
 * <ul>
 *   <li>不预设字段集合，出现任何新字段都不会丢失；</li>
 *   <li>不改写数值文本格式（如 {@code 1720351696000.0}、{@code 0.0}），{@link #toString()} 可原样还原该行；</li>
 *   <li>强类型 getter 只是「读取视图」，按需把文本转成 long/double。</li>
 * </ul>
 *
 * <p><b>轨迹点类型（tp）说明</b>：</p>
 * <ul>
 *   <li><b>lbs</b> —— 位置：k=索引序号，lat=纬度，lon=经度，alt=海拔(米)，t=卫星时间</li>
 *   <li><b>p-m / b-p-m / pm-n / pm-b</b> —— 公制/英制/间隔配速：k=编码值(序号×10000000+累计秒)，v=配速</li>
 *   <li><b>h-r / i-h-r</b> —— 心率：k=绝对毫秒时间戳，v=心率(次/分)</li>
 *   <li><b>s-r</b> —— 步频：k=绝对毫秒时间戳，v=步频(步/分)</li>
 *   <li><b>alti</b> —— 海拔：k=绝对毫秒时间戳(5s 一点)，v=海拔(米)</li>
 *   <li><b>swf / p-f</b> —— SWOLF / 划水频率：k=开始时间(秒)，v=数值</li>
 *   <li><b>r-pm</b> —— 实时速度配速：k=运动秒数，s=速度(km/h)，p=配速(秒/公里)</li>
 *   <li><b>rs</b> —— 实时速度：k=相对 startTime 秒偏移，v=速度(分米/秒)</li>
 *   <li><b>rp</b> —— 实时跑姿：k=运动秒数，gct/gia/sa/ee/fsp/wsp/hsp</li>
 *   <li><b>cad</b> —— 骑行踏频：k=绝对毫秒时间戳，v=踏频(转/分)</li>
 *   <li><b>scp</b> —— 跳绳瞬时频率：k=绝对毫秒时间戳(5s 一点)，v=次/分，
 *       未跳动的采样点为 0（仅在 sportType=283 跳绳样本中出现）</li>
 *   <li><b>spo2</b> —— 血氧饱和度：k=绝对毫秒时间戳，v=百分比</li>
 *   <li><b>sec</b> —— 分段统计：无 k，用 sn 段序号标识，sn/st/sd/sp/shr/sc/ssl/str/std/
 *       sgct/sgia/ssa/see/stp/sht/sgtb/svo/svr/spi/spir</li>
 * </ul>
 */
public class TrackPoint {

    /** 有序原始字段：key -> 原始文本值（保持解析时的出现顺序） */
    private final Map<String, String> fields = new LinkedHashMap<>();

    /** 原始行是否以 {@code ;} 结尾（还原时保持一致） */
    private boolean trailingSemicolon = true;

    // ==================== 解析 ====================

    /**
     * 把一行文本解析为轨迹点。
     * <p>按 {@code ;} 切段、按每段第一个 {@code =} 切键值，键值一律按原始文本保存，
     * 不识别、也不依赖任何具体字段名。</p>
     *
     * @return 解析结果；若该行不符合 {@code k=v;} 结构（存在不含 {@code =} 的段）则返回 {@code null}，
     *         由调用方决定原样保留文本，避免还原时失真。
     */
    public static TrackPoint parseLine(String line) {
        if (line == null || line.isEmpty()) return null;
        String[] parts = line.split(";", -1);
        TrackPoint p = new TrackPoint();
        p.setTrailingSemicolon(parts[parts.length - 1].isEmpty());
        boolean any = false;
        for (int i = 0; i < parts.length; i++) {
            String part = parts[i];
            if (part.isEmpty()) {
                // 仅允许「末尾分号」产生的空段
                if (i == parts.length - 1) continue;
                return null;
            }
            int eq = part.indexOf('=');
            if (eq <= 0) return null;
            p.put(part.substring(0, eq), part.substring(eq + 1));
            any = true;
        }
        return any ? p : null;
    }

    // ==================== 原始字段读写 ====================

    /** 写入一个原始键值对（保持插入顺序） */
    public void put(String key, String rawValue) {
        if (key == null || key.isEmpty()) return;
        fields.put(key, rawValue == null ? "" : rawValue);
    }

    /** 读取某字段的原始文本，不存在返回 null */
    public String raw(String key) {
        return fields.get(key);
    }

    /** 是否包含某字段 */
    public boolean has(String key) {
        return fields.containsKey(key);
    }

    /** 全部原始字段（只读，按原始顺序） */
    public Map<String, String> getFields() {
        return Collections.unmodifiableMap(fields);
    }

    public boolean isTrailingSemicolon() { return trailingSemicolon; }
    public void setTrailingSemicolon(boolean trailingSemicolon) { this.trailingSemicolon = trailingSemicolon; }

    /** 按原始字段顺序与原始文本还原该行，例如 {@code tp=lbs;k=0;lat=39.9;lon=116.3;alt=50.0;t=1.7e9;} */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        int idx = 0;
        int size = fields.size();
        for (Map.Entry<String, String> e : fields.entrySet()) {
            sb.append(e.getKey()).append('=').append(e.getValue());
            idx++;
            if (idx < size || trailingSemicolon) sb.append(';');
        }
        return sb.toString();
    }

    // ==================== 强类型读取视图 ====================

    private long lng(String key) {
        String s = fields.get(key);
        if (s == null || s.isEmpty()) return 0L;
        try { return (long) Double.parseDouble(s.trim()); } catch (Exception e) { return 0L; }
    }

    private double dbl(String key) {
        String s = fields.get(key);
        if (s == null || s.isEmpty()) return 0D;
        try { return Double.parseDouble(s.trim()); } catch (Exception e) { return 0D; }
    }

    /** 轨迹点类型（tp） */
    public String getType() { return fields.get("tp"); }
    public void setType(String type) { put("tp", type); }

    /** k 字段（含义随 tp 变化，详见类注释） */
    public long getK() { return lng("k"); }
    public void setK(long k) { put("k", String.valueOf(k)); }
    /** v 字段（数值，简单类型通用） */
    public double getV() { return dbl("v"); }
    public void setV(double v) { put("v", String.valueOf(v)); }

    /** 纬度（lbs，度） */
    public double getLat() { return dbl("lat"); }
    /** 经度（lbs，度） */
    public double getLon() { return dbl("lon"); }
    /** 海拔（lbs，米） */
    public double getAlt() { return dbl("alt"); }
    /** 卫星时间（lbs，秒或毫秒） */
    public long getT() { return lng("t"); }

    /** 实时速度（r-pm，公里/时） */
    public double getS() { return dbl("s"); }
    /** 实时配速（r-pm，秒/公里） */
    public double getP() { return dbl("p"); }

    /** 触地时间（rp，毫秒） */
    public double getGct() { return dbl("gct"); }
    /** 着地冲击（rp，g） */
    public double getGia() { return dbl("gia"); }
    /** 摆动角度（rp，度） */
    public double getSa() { return dbl("sa"); }
    /** 外翻幅度（rp，度） */
    public double getEe() { return dbl("ee"); }
    /** 前脚掌触地次数（rp，步） */
    public double getFsp() { return dbl("fsp"); }
    /** 全脚掌触地次数（rp，步） */
    public double getWsp() { return dbl("wsp"); }
    /** 后脚掌触地次数（rp，步） */
    public double getHsp() { return dbl("hsp"); }

    /** 段序号（sec） */
    public long getSn() { return lng("sn"); }
    /** 段时长（sec，秒；实测各段之和等于 totalTime 的秒数） */
    public long getSt() { return lng("st"); }
    /** 段距离（sec，厘米；实测各段之和等于 totalDistance 的厘米数） */
    public long getSd() { return lng("sd"); }
    /** 段配速（sec，秒/公里） */
    public double getSp() { return dbl("sp"); }
    /** 段心率（sec，次/分） */
    public double getShr() { return dbl("shr"); }
    /** 段卡路里（sec，语义未明：各段之和与 totalCalories 不符，不可用） */
    public long getSc() { return lng("sc"); }
    /** 段步幅（sec） */
    public double getSsl() { return dbl("ssl"); }
    /** 段步频（sec，语义未明：数值与总步频/总步数均不符，不可用） */
    public double getStr() { return dbl("str"); }
    /** 段步数（sec，语义未明：各段之和与 totalSteps 不符，不可用） */
    public long getStd() { return lng("std"); }
    /** 段触地时间（sec） */
    public double getSgct() { return dbl("sgct"); }
    /** 段落地冲击加速度（sec） */
    public double getSgia() { return dbl("sgia"); }
    /** 段摆动角度（sec） */
    public double getSsa() { return dbl("ssa"); }
    /** 段外翻幅度（sec） */
    public double getSee() { return dbl("see"); }
    /** 段腾空时间（sec） */
    public double getStp() { return dbl("stp"); }
    /** 段触地腾空时间比率（sec） */
    public double getSht() { return dbl("sht"); }
    /** 段触地时间平衡（sec） */
    public double getSgtb() { return dbl("sgtb"); }
    /** 段垂直振幅（sec） */
    public double getSvo() { return dbl("svo"); }
    /** 段垂直振幅比率（sec） */
    public double getSvr() { return dbl("svr"); }
    /** 段表现指数（sec） */
    public double getSpi() { return dbl("spi"); }
    /** 段表现指数比率（sec） */
    public double getSpir() { return dbl("spir"); }

    /**
     * lbs 的 t 字段单位不统一（多为秒，少数毫秒），按位数归一化为毫秒。
     */
    public long getTimeMillis() {
        return normalizeToMillis(getT());
    }

    /** 按数值位数归一化时间戳为毫秒：13 位（毫秒）直接返回，10 位（秒）乘 1000 */
    public static long normalizeToMillis(long val) {
        if (val <= 0) return 0;
        if (val >= 1_000_000_000_000L) return val;      // 毫秒
        if (val >= 1_000_000_000L) return val * 1000L;  // 秒
        return val;
    }
}