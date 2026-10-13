package me.frankhbu.fitconverter.model.huawei;

/**
 * 分段用时项，对应 partTimeMap / britishPartTimeMap 中的一项。
 * <p>partTimeMap 的 key 是公里数（字符串，如 "1.0"、"21.0975" 表示第 1 公里、第 21.0975 公里），
 * value 是到该公里为止的累计用时（秒）。</p>
 * <p>britishPartTimeMap 同理，key 是英里数，value 是累计用时（秒）。</p>
 */
public class PartTimeEntry {
    /** 公里数（字符串，如 "1.0"、"21.0975"，英制为英里数） */
    private String distance;
    /** 到该距离为止的累计用时（秒） */
    private double elapsedTime;

    public String getDistance() { return distance; }
    public void setDistance(String distance) { this.distance = distance; }
    public double getElapsedTime() { return elapsedTime; }
    public void setElapsedTime(double elapsedTime) { this.elapsedTime = elapsedTime; }
}