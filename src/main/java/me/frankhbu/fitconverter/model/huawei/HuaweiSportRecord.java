package me.frankhbu.fitconverter.model.huawei;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;

import java.util.ArrayList;
import java.util.List;

/**
 * 华为运动记录，对应 motion path detail data.json 中的一条记录。
 *
 * <p><b>attribute 字段</b>：不再以原始字符串透传，而是解析为 {@link HuaweiAttribute} 对象；
 * 序列化时由 {@link #getAttribute()} 重新渲染（{@code attributeData.toString()}），
 * 因此「解析 → 还原」是否无损完全取决于对象的 {@code toString()} 实现。</p>
 */
public class HuaweiSportRecord {
    /** 总步数 */
    private long totalSteps;
    /** 是否合并轨迹（0/1） */
    private int mergedFlag;
    /** 记录 ID */
    private String recordId;
    /** 总爬楼层数 */
    private long totalStoreys;
    /** 数据 ID */
    private int dataId;
    /** 应用类型 */
    private int appType;
    /** 厂商（部分记录没有该字段） */
    private String vendor;
    /** 分段用时映射（公里数 -> 累计用时秒），原样保留为 JSON 对象 */
    private JSONObject partTimeMap;
    /** 异常轨迹（0/1 标记） */
    private int abnormalTrack;
    /** 实际爬楼层数 */
    private long realStoreys;
    /** 开始时间（毫秒时间戳） */
    private long startTime;
    /** 总距离（米） */
    private long totalDistance;
    /** 重复轨迹（0/1 标记） */
    private int duplicateTrack;
    /** 坐标系（部分记录没有该字段） */
    private String coordinate;
    /** 运动类型（sportType） */
    private int sportType;
    /** 总时长（毫秒） */
    private long totalTime;
    /** 时区 */
    private String timeZone;
    /** 设备编码 */
    private int deviceCode;
    /** 配速映射（公里序号+累计用时秒编码 -> 该公里配速），原样保留为 JSON 对象 */
    private JSONObject paceMap;
    /** 总卡路里（千卡 * 1000） */
    private long totalCalories;
    /** 版本（毫秒时间戳） */
    private long version;
    /** 实际步数 */
    private long realSteps;
    /** 运动数据来源（0/1 标记） */
    private int sportDataSource;
    /** 位置点数组（实际 GPS 点多在 attribute 的 HW_EXT_TRACK_DETAIL 中，此处常为占位值） */
    private List<LocationPoint> location;
    /** 结束时间（毫秒时间戳） */
    private long endTime;
    /** 运动状态（0/1 标记） */
    private int runState;
    /** 运动记录时间信息（{ ct, mt, lt, mts }） */
    private TrData tr;

    /** 解析后的 attribute（轨迹详情 + 汇总统计等标记块） */
    private HuaweiAttribute attributeData;

    // ==================== attribute ====================

    /** 由 {@link #attributeData} 重新渲染出的 attribute 字符串（序列化输出的就是它） */
    public String getAttribute() {
        return attributeData == null ? null : attributeData.toString();
    }

    /** 反序列化入口：收到原始字符串时立即解析为对象 */
    public void setAttribute(String attribute) {
        this.attributeData = attribute == null ? null : HuaweiAttribute.parse(attribute);
    }

    /** 结构化的 attribute（不参与 JSON 序列化） */
    @JSONField(serialize = false, deserialize = false)
    public HuaweiAttribute getAttributeData() { return attributeData; }

    @JSONField(serialize = false, deserialize = false)
    public void setAttributeData(HuaweiAttribute attributeData) { this.attributeData = attributeData; }

    /** 逐点轨迹（来自 attribute 的 DETAIL 块，不参与 JSON 序列化） */
    @JSONField(serialize = false, deserialize = false)
    public List<TrackPoint> getTrackPoints() {
        return attributeData == null ? new ArrayList<>() : attributeData.getTrackPoints();
    }

    /** 汇总统计（来自 attribute 的 SIMPLIFY 块，不参与 JSON 序列化） */
    @JSONField(serialize = false, deserialize = false)
    public HuaweiSimplifyData getSimplifyData() {
        return attributeData == null ? null : attributeData.getSimplifyData();
    }

    // ==================== 普通字段 ====================

    public long getTotalSteps() { return totalSteps; }
    public void setTotalSteps(long totalSteps) { this.totalSteps = totalSteps; }
    public int getMergedFlag() { return mergedFlag; }
    public void setMergedFlag(int mergedFlag) { this.mergedFlag = mergedFlag; }
    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public long getTotalStoreys() { return totalStoreys; }
    public void setTotalStoreys(long totalStoreys) { this.totalStoreys = totalStoreys; }
    public int getDataId() { return dataId; }
    public void setDataId(int dataId) { this.dataId = dataId; }
    public int getAppType() { return appType; }
    public void setAppType(int appType) { this.appType = appType; }
    public String getVendor() { return vendor; }
    public void setVendor(String vendor) { this.vendor = vendor; }
    public JSONObject getPartTimeMap() { return partTimeMap; }
    public void setPartTimeMap(JSONObject partTimeMap) { this.partTimeMap = partTimeMap; }
    public int getAbnormalTrack() { return abnormalTrack; }
    public void setAbnormalTrack(int abnormalTrack) { this.abnormalTrack = abnormalTrack; }
    public long getRealStoreys() { return realStoreys; }
    public void setRealStoreys(long realStoreys) { this.realStoreys = realStoreys; }
    public long getStartTime() { return startTime; }
    public void setStartTime(long startTime) { this.startTime = startTime; }
    public long getTotalDistance() { return totalDistance; }
    public void setTotalDistance(long totalDistance) { this.totalDistance = totalDistance; }
    public int getDuplicateTrack() { return duplicateTrack; }
    public void setDuplicateTrack(int duplicateTrack) { this.duplicateTrack = duplicateTrack; }
    public String getCoordinate() { return coordinate; }
    public void setCoordinate(String coordinate) { this.coordinate = coordinate; }
    public int getSportType() { return sportType; }
    public void setSportType(int sportType) { this.sportType = sportType; }
    public long getTotalTime() { return totalTime; }
    public void setTotalTime(long totalTime) { this.totalTime = totalTime; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public int getDeviceCode() { return deviceCode; }
    public void setDeviceCode(int deviceCode) { this.deviceCode = deviceCode; }
    public JSONObject getPaceMap() { return paceMap; }
    public void setPaceMap(JSONObject paceMap) { this.paceMap = paceMap; }
    public long getTotalCalories() { return totalCalories; }
    public void setTotalCalories(long totalCalories) { this.totalCalories = totalCalories; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
    public long getRealSteps() { return realSteps; }
    public void setRealSteps(long realSteps) { this.realSteps = realSteps; }
    public int getSportDataSource() { return sportDataSource; }
    public void setSportDataSource(int sportDataSource) { this.sportDataSource = sportDataSource; }
    public List<LocationPoint> getLocation() { return location; }
    public void setLocation(List<LocationPoint> location) { this.location = location; }
    public long getEndTime() { return endTime; }
    public void setEndTime(long endTime) { this.endTime = endTime; }
    public int getRunState() { return runState; }
    public void setRunState(int runState) { this.runState = runState; }
    public TrData getTr() { return tr; }
    public void setTr(TrData tr) { this.tr = tr; }
}