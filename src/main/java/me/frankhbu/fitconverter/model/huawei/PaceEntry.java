package me.frankhbu.fitconverter.model.huawei;

/**
 * 配速分段项，对应 paceMap / britishPaceMap 中的一项。
 * <p>paceMap 的 key 是「公里序号 + 累计用时(秒)」的编码字符串（如 "10000347" 表示
 * 第 1 公里累计用时 347 秒，"211108953" 表示第 21.1 公里累计用时 8953 秒），
 * value 是该公里的配速（秒/公里）。</p>
 * <p>britishPaceMap 同理，value 是英制配速（秒/英里）。</p>
 */
public class PaceEntry {
    /** 原始 key（公里序号 + 累计用时的编码字符串） */
    private String key;
    /** 配速值（秒/公里，英制为秒/英里） */
    private double pace;

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }
    public double getPace() { return pace; }
    public void setPace(double pace) { this.pace = pace; }
}