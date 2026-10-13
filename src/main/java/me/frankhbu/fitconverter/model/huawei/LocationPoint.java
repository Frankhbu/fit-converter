package me.frankhbu.fitconverter.model.huawei;

/**
 * 华为运动记录中的 location 数组元素。
 * 对应 JSON 中 location 字段的每一项：
 * { altitude, latitude, name, longitude, timestamp }
 * 注意：实际数据中 GPS 点通常存放在 attribute 的 HW_EXT_TRACK_DETAIL 里，
 * 此处 location 常为占位值（经纬度为 0，name 提示 GPS 点在 attribute 中）。
 */
public class LocationPoint {
    /** 海拔（米） */
    private double altitude;
    /** 纬度（度） */
    private double latitude;
    /** 名称/说明 */
    private String name;
    /** 经度（度） */
    private double longitude;
    /** 时间戳（毫秒） */
    private long timestamp;

    public double getAltitude() { return altitude; }
    public void setAltitude(double altitude) { this.altitude = altitude; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}