package me.frankhbu.fitconverter.model.huawei;

import com.alibaba.fastjson.annotation.JSONField;

/**
 * 穿戴设备运动数据，对应 SIMPLIFY 块中的 wearSportData 字段。
 * <p>结构（8 个字段，均为数值）：</p>
 * <ul>
 *   <li>record_id —— 记录 ID</li>
 *   <li>max_met —— 最大代谢当量（MET）</li>
 *   <li>anaerobic_exercise_etraining_effect —— 无氧训练效果</li>
 *   <li>load_peak —— 负荷峰值</li>
 *   <li>etraining_effect —— 有氧训练效果</li>
 *   <li>recovery_time —— 恢复时间（秒）</li>
 *   <li>extra_poc —— 额外指标</li>
 *   <li>status —— 状态</li>
 * </ul>
 */
public class WearSportData {
    /** 记录 ID */
    @JSONField(name = "record_id")
    private long recordId;
    /** 最大代谢当量（MET） */
    @JSONField(name = "max_met")
    private long maxMet;
    /** 无氧训练效果 */
    @JSONField(name = "anaerobic_exercise_etraining_effect")
    private long anaerobicExerciseEtrainingEffect;
    /** 负荷峰值 */
    @JSONField(name = "load_peak")
    private long loadPeak;
    /** 有氧训练效果 */
    @JSONField(name = "etraining_effect")
    private long etrainingEffect;
    /** 恢复时间（秒） */
    @JSONField(name = "recovery_time")
    private long recoveryTime;
    /** 额外指标 */
    @JSONField(name = "extra_poc")
    private long extraPoc;
    /** 状态 */
    @JSONField(name = "status")
    private long status;

    public long getRecordId() { return recordId; }
    public void setRecordId(long recordId) { this.recordId = recordId; }
    public long getMaxMet() { return maxMet; }
    public void setMaxMet(long maxMet) { this.maxMet = maxMet; }
    public long getAnaerobicExerciseEtrainingEffect() { return anaerobicExerciseEtrainingEffect; }
    public void setAnaerobicExerciseEtrainingEffect(long anaerobicExerciseEtrainingEffect) { this.anaerobicExerciseEtrainingEffect = anaerobicExerciseEtrainingEffect; }
    public long getLoadPeak() { return loadPeak; }
    public void setLoadPeak(long loadPeak) { this.loadPeak = loadPeak; }
    public long getEtrainingEffect() { return etrainingEffect; }
    public void setEtrainingEffect(long etrainingEffect) { this.etrainingEffect = etrainingEffect; }
    public long getRecoveryTime() { return recoveryTime; }
    public void setRecoveryTime(long recoveryTime) { this.recoveryTime = recoveryTime; }
    public long getExtraPoc() { return extraPoc; }
    public void setExtraPoc(long extraPoc) { this.extraPoc = extraPoc; }
    public long getStatus() { return status; }
    public void setStatus(long status) { this.status = status; }
}