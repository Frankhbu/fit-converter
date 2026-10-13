package me.frankhbu.fitconverter.model.huawei;

/**
 * 游泳分段，对应 SIMPLIFY 块中的 mSwimSegments / mBritishSwimSegments 数组中的一项。
 * <p>仅在游泳运动（sportType=109 等）时有数据，其余运动该字段为空列表。</p>
 * <p>字段说明（华为官方游泳分段结构）：</p>
 * <ul>
 *   <li>distance —— 分段距离（米，英制为码）</li>
 *   <li>duration —— 分段用时（秒）</li>
 *   <li>strokes —— 划水次数</li>
 *   <li>swolf —— SWOLF 值（游泳效率指标，= 用时 + 划水次数）</li>
 *   <li>strokeType —— 泳姿类型（1=自由泳，2=蛙泳，3=仰泳，4=蝶泳，5=混合泳）</li>
 *   <li>strokeRate —— 划水频率（次/分）</li>
 *   <li>pace —— 配速（秒/100米，英制为秒/100码）</li>
 * </ul>
 */
public class SwimSegment {
    /** 分段距离（米，英制为码） */
    private double distance;
    /** 分段用时（秒） */
    private double duration;
    /** 划水次数 */
    private long strokes;
    /** SWOLF 值（游泳效率指标，= 用时 + 划水次数） */
    private double swolf;
    /** 泳姿类型（1=自由泳，2=蛙泳，3=仰泳，4=蝶泳，5=混合泳） */
    private int strokeType;
    /** 划水频率（次/分） */
    private double strokeRate;
    /** 配速（秒/100米，英制为秒/100码） */
    private double pace;

    public double getDistance() { return distance; }
    public void setDistance(double distance) { this.distance = distance; }
    public double getDuration() { return duration; }
    public void setDuration(double duration) { this.duration = duration; }
    public long getStrokes() { return strokes; }
    public void setStrokes(long strokes) { this.strokes = strokes; }
    public double getSwolf() { return swolf; }
    public void setSwolf(double swolf) { this.swolf = swolf; }
    public int getStrokeType() { return strokeType; }
    public void setStrokeType(int strokeType) { this.strokeType = strokeType; }
    public double getStrokeRate() { return strokeRate; }
    public void setStrokeRate(double strokeRate) { this.strokeRate = strokeRate; }
    public double getPace() { return pace; }
    public void setPace(double pace) { this.pace = pace; }
}