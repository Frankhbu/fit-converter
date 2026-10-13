package me.frankhbu.fitconverter.model.huawei;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * 扩展轨迹数据，对应 SIMPLIFY 块中的 mExtendTrackDataMap 字段。
 * <p>该对象包含运动算法相关的扩展统计，字段值多为字符串（数值也以字符串形式存储）。
 * 不同运动类型字段略有差异（如爬楼无 segmentNum，跑步/骑行/越野跑有 segmentNum）。</p>
 * <p>字段说明：</p>
 * <ul>
 *   <li>STEP_STATICS_EXCEPTION —— 步态统计异常标记</li>
 *   <li>eteAlgoKey —— ETE（估计完成时间）算法 key</li>
 *   <li>sportHeartPosture —— 运动心率姿态</li>
 *   <li>segmentNum —— 分段数</li>
 *   <li>completionRate —— 完成率</li>
 *   <li>runPaceZoneStatistics —— 配速区间统计（JSON 数组字符串，如 "[1135,190,55,45,45]"）</li>
 *   <li>record_flag —— 记录标记</li>
 *   <li>crossTrainerCadence —— 交叉训练器踏频</li>
 *   <li>runPaceZoneConfig —— 配速区间配置（JSON 数组字符串，如 "[376,334,310,290,270,247]"）</li>
 *   <li>skipNum —— 跳绳总个数</li>
 *   <li>skipSpeed —— 跳绳平均速度（次/分钟）</li>
 *   <li>maxSkippingTimes —— 跳绳最大连跳次数</li>
 *   <li>interruptTimes —— 跳绳中断次数</li>
 * </ul>
 */
public class ExtendTrackData {
    /** 步态统计异常标记 */
    @JSONField(name = "STEP_STATICS_EXCEPTION")
    private String stepStaticsException;
    /** ETE（估计完成时间）算法 key */
    private String eteAlgoKey;
    /** 运动心率姿态 */
    private String sportHeartPosture;
    /** 分段数 */
    private String segmentNum;
    /** 完成率 */
    private String completionRate;
    /** 配速区间统计（JSON 数组字符串，如 "[1135,190,55,45,45]"） */
    private String runPaceZoneStatistics;
    /** 记录标记 */
    @JSONField(name = "record_flag")
    private String recordFlag;
    /** 交叉训练器踏频 */
    private String crossTrainerCadence;
    /** 配速区间配置（JSON 数组字符串，如 "[376,334,310,290,270,247]"） */
    private String runPaceZoneConfig;
    /** 跳绳总个数 */
    private String skipNum;
    /** 跳绳平均速度（次/分钟） */
    private String skipSpeed;
    /** 跳绳最大连跳次数 */
    private String maxSkippingTimes;
    /** 跳绳中断次数 */
    private String interruptTimes;

    public String getStepStaticsException() { return stepStaticsException; }
    public void setStepStaticsException(String stepStaticsException) { this.stepStaticsException = stepStaticsException; }
    public String getEteAlgoKey() { return eteAlgoKey; }
    public void setEteAlgoKey(String eteAlgoKey) { this.eteAlgoKey = eteAlgoKey; }
    public String getSportHeartPosture() { return sportHeartPosture; }
    public void setSportHeartPosture(String sportHeartPosture) { this.sportHeartPosture = sportHeartPosture; }
    public String getSegmentNum() { return segmentNum; }
    public void setSegmentNum(String segmentNum) { this.segmentNum = segmentNum; }
    public String getCompletionRate() { return completionRate; }
    public void setCompletionRate(String completionRate) { this.completionRate = completionRate; }
    public String getRunPaceZoneStatistics() { return runPaceZoneStatistics; }
    public void setRunPaceZoneStatistics(String runPaceZoneStatistics) { this.runPaceZoneStatistics = runPaceZoneStatistics; }
    public String getRecordFlag() { return recordFlag; }
    public void setRecordFlag(String recordFlag) { this.recordFlag = recordFlag; }
    public String getCrossTrainerCadence() { return crossTrainerCadence; }
    public void setCrossTrainerCadence(String crossTrainerCadence) { this.crossTrainerCadence = crossTrainerCadence; }
    public String getRunPaceZoneConfig() { return runPaceZoneConfig; }
    public void setRunPaceZoneConfig(String runPaceZoneConfig) { this.runPaceZoneConfig = runPaceZoneConfig; }
    public String getSkipNum() { return skipNum; }
    public void setSkipNum(String skipNum) { this.skipNum = skipNum; }
    public String getSkipSpeed() { return skipSpeed; }
    public void setSkipSpeed(String skipSpeed) { this.skipSpeed = skipSpeed; }
    public String getMaxSkippingTimes() { return maxSkippingTimes; }
    public void setMaxSkippingTimes(String maxSkippingTimes) { this.maxSkippingTimes = maxSkippingTimes; }
    public String getInterruptTimes() { return interruptTimes; }
    public void setInterruptTimes(String interruptTimes) { this.interruptTimes = interruptTimes; }
}