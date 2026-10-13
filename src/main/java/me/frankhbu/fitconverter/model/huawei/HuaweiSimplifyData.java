package me.frankhbu.fitconverter.model.huawei;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 华为运动记录 attribute 中 {@code HW_EXT_TRACK_SIMPLIFY@is{...}} 块解析出的权威统计数据。
 * 该块是运动轨迹的汇总统计（平均/最大/最小心率、配速、步频、海拔、卡路里等），
 * 与逐点轨迹数据（HW_EXT_TRACK_DETAIL）互补，是生成 FIT 文件 summary 的权威来源。
 *
 * <p><b>存储方式</b>：内部直接持有解析后的有序 {@link JSONObject}，因此：</p>
 * <ul>
 *   <li>不预设字段集合，块中出现任何新字段都不会丢失；</li>
 *   <li>不改变原始字面类型（整数不会变成 {@code x.0}），{@link #toString()} 可还原原始 JSON 文本；</li>
 *   <li>下面的强类型 getter 只是「读取视图」，按需做类型转换。</li>
 * </ul>
 */
public class HuaweiSimplifyData {

    /** 原始 JSON 对象（有序，保真） */
    private final JSONObject raw;

    public HuaweiSimplifyData() {
        this.raw = new JSONObject(true);
    }

    public HuaweiSimplifyData(JSONObject raw) {
        this.raw = raw == null ? new JSONObject(true) : raw;
    }

    /** 原始 JSON 对象（可直接读写任意字段） */
    public JSONObject getRaw() {
        return raw;
    }

    /** 还原为原始 JSON 文本 */
    @Override
    public String toString() {
        return JSON.toJSONString(raw);
    }

    // ==================== 强类型读取视图 ====================

    /** 异常轨迹标记（0=正常） */
    public int getAbnormalTrack() { return raw.getIntValue("abnormalTrack"); }
    /** 平均心率（次/分） */
    public double getAvgHeartRate() { return raw.getDoubleValue("avgHeartRate"); }
    /** 平均配速（秒/公里，0 表示无） */
    public double getAvgPace() { return raw.getDoubleValue("avgPace"); }
    /** 平均步频（步/分） */
    public double getAvgStepRate() { return raw.getDoubleValue("avgStepRate"); }
    /** 最佳配速（秒/公里） */
    public double getBestPace() { return raw.getDoubleValue("bestPace"); }
    /** 最佳步频（步/分） */
    public double getBestStepRate() { return raw.getDoubleValue("bestStepRate"); }
    /** 英制配速列表 */
    public List<PaceEntry> getBritishPaceMap() { return toPaceList(raw.getJSONObject("britishPaceMap")); }
    /** 英制分段用时列表 */
    public List<PartTimeEntry> getBritishPartTimeMap() { return toPartTimeList(raw.getJSONObject("britishPartTimeMap")); }
    /** 主运动数据类型 */
    public int getChiefSportDataType() { return raw.getIntValue("chiefSportDataType"); }
    /** 爬坡波动 */
    public double getCreepingWave() { return raw.getDoubleValue("creepingWave"); }
    /** 是否有轨迹点 */
    public boolean isHasTrackPoint() { return raw.getBooleanValue("hasTrackPoint"); }
    /** 是否自由运动 */
    public boolean isFreeMotion() { return raw.getBooleanValue("isFreeMotion"); }
    /** 平均腾空时间 */
    public double getMAverageHangTime() { return raw.getDoubleValue("mAverageHangTime"); }
    /** 平均外翻幅度 */
    public double getMAvgEversionExcursion() { return raw.getDoubleValue("mAvgEversionExcursion"); }
    /** 平均前掌着地模式 */
    public double getMAvgForeFootStrikePattern() { return raw.getDoubleValue("mAvgForeFootStrikePattern"); }
    /** 平均触地时间 */
    public double getMAvgGroundContactTime() { return raw.getDoubleValue("mAvgGroundContactTime"); }
    /** 平均落地冲击加速度 */
    public double getMAvgGroundImpactAcceleration() { return raw.getDoubleValue("mAvgGroundImpactAcceleration"); }
    /** 平均后掌着地模式 */
    public double getMAvgHindFootStrikePattern() { return raw.getDoubleValue("mAvgHindFootStrikePattern"); }
    /** 平均摆动角度 */
    public double getMAvgSwingAngle() { return raw.getDoubleValue("mAvgSwingAngle"); }
    /** 平均全掌着地模式 */
    public double getMAvgWholeFootStrikePattern() { return raw.getDoubleValue("mAvgWholeFootStrikePattern"); }
    /** 英制游泳分段 */
    public List<SwimSegment> getMBritishSwimSegments() { return toSwimList(raw.getJSONArray("mBritishSwimSegments")); }
    /** 英制游泳 SWOLF 基准 */
    public double getMBritishSwolfBase() { return raw.getDoubleValue("mBritishSwolfBase"); }
    /** 是否重复 */
    public int getMDuplicated() { return raw.getIntValue("mDuplicated"); }
    /** 扩展轨迹数据（含步态异常、算法 key、配速区间统计等） */
    public ExtendTrackData getMExtendTrackDataMap() {
        JSONObject o = raw.getJSONObject("mExtendTrackDataMap");
        return o == null ? null : o.toJavaObject(ExtendTrackData.class);
    }
    /** 触地腾空时间比率 */
    public double getMGroundHangTimeRate() { return raw.getDoubleValue("mGroundHangTimeRate"); }
    /** 心率区间类型 */
    public int getMHeartrateZoneType() { return raw.getIntValue("mHeartrateZoneType"); }
    /** 是否新坐标系 */
    public boolean isMIsNewCoordinate() { return raw.getBooleanValue("mIsNewCoordinate"); }
    /** 最大海拔（米） */
    public double getMMaxAlti() { return raw.getDoubleValue("mMaxAlti"); }
    /** 最大血氧 SpO2 */
    public double getMMaxSpo2() { return raw.getDoubleValue("mMaxSpo2"); }
    /** 最小海拔（米） */
    public double getMMinAlti() { return raw.getDoubleValue("mMinAlti"); }
    /** 最小血氧 SpO2 */
    public double getMMinSpo2() { return raw.getDoubleValue("mMinSpo2"); }
    /** 游泳分段 */
    public List<SwimSegment> getMSwimSegments() { return toSwimList(raw.getJSONArray("mSwimSegments")); }
    /** 游泳 SWOLF 基准 */
    public double getMSwolfBase() { return raw.getDoubleValue("mSwolfBase"); }
    /** 总下降（米） */
    public double getMTotalDescent() { return raw.getDoubleValue("mTotalDescent"); }
    /** 最大心率（次/分） */
    public double getMaxHeartRate() { return raw.getDoubleValue("maxHeartRate"); }
    /** 最小心率（次/分） */
    public double getMinHeartRate() { return raw.getDoubleValue("minHeartRate"); }
    /** 配速列表（每项：公里序号+累计用时秒编码 -> 该公里配速秒/公里） */
    public List<PaceEntry> getPaceMap() { return toPaceList(raw.getJSONObject("paceMap")); }
    /** 分段用时列表（每项：距离标识 -> 累计用时秒） */
    public List<PartTimeEntry> getPartTimeMap() { return toPartTimeList(raw.getJSONObject("partTimeMap")); }
    /** 运动数据来源 */
    public int getSportDataSource() { return raw.getIntValue("sportDataSource"); }
    /** 运动类型（SIMPLIFY 块内的 sportType，与顶层可能不同） */
    public int getSportType() { return raw.getIntValue("sportType"); }
    /** 总卡路里（千卡 * 1000） */
    public long getTotalCalories() { return raw.getLongValue("totalCalories"); }
    /** 总距离（米） */
    public long getTotalDistance() { return raw.getLongValue("totalDistance"); }
    /** 总步数 */
    public long getTotalSteps() { return raw.getLongValue("totalSteps"); }
    /** 总时长（毫秒） */
    public long getTotalTime() { return raw.getLongValue("totalTime"); }
    /** 轨迹类型 */
    public int getTrackType() { return raw.getIntValue("trackType"); }
    /** 厂商（如 AMAP 高德，仅部分户外运动出现） */
    public String getVendor() { return raw.getString("vendor"); }
    /** 穿戴设备运动数据 */
    public WearSportData getWearSportData() {
        JSONObject o = raw.getJSONObject("wearSportData");
        return o == null ? null : o.toJavaObject(WearSportData.class);
    }

    // ==================== 转换辅助 ====================

    private static List<PaceEntry> toPaceList(JSONObject o) {
        List<PaceEntry> list = new ArrayList<>();
        if (o == null) return list;
        for (String key : o.keySet()) {
            PaceEntry e = new PaceEntry();
            e.setKey(key);
            e.setPace(o.getDoubleValue(key));
            list.add(e);
        }
        return list;
    }

    private static List<PartTimeEntry> toPartTimeList(JSONObject o) {
        List<PartTimeEntry> list = new ArrayList<>();
        if (o == null) return list;
        for (String key : o.keySet()) {
            PartTimeEntry e = new PartTimeEntry();
            e.setDistance(key);
            e.setElapsedTime(o.getDoubleValue(key));
            list.add(e);
        }
        return list;
    }

    private static List<SwimSegment> toSwimList(JSONArray arr) {
        List<SwimSegment> list = new ArrayList<>();
        if (arr == null) return list;
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o != null) list.add(o.toJavaObject(SwimSegment.class));
        }
        return list;
    }
}