package me.frankhbu.fitconverter.model.huawei;

/**
 * 运动记录时间信息（对应顶层 JSON 的 tr 字段）。
 * <p>结构：{ ct, mt, lt, mts }，均为毫秒时间戳。</p>
 */
public class TrData {
    /** 创建时间（create time，毫秒时间戳） */
    private long ct;
    /** 修改时间（modify time，毫秒时间戳） */
    private long mt;
    /** 最后时间（last time，毫秒时间戳） */
    private long lt;
    /** 修改时间戳（modify timestamp，毫秒时间戳） */
    private long mts;

    public long getCt() { return ct; }
    public void setCt(long ct) { this.ct = ct; }
    public long getMt() { return mt; }
    public void setMt(long mt) { this.mt = mt; }
    public long getLt() { return lt; }
    public void setLt(long lt) { this.lt = lt; }
    public long getMts() { return mts; }
    public void setMts(long mts) { this.mts = mts; }
}