package me.frankhbu.fitconverter.convert;

/**
 * 坐标转换器：负责华为坐标格式到佳明 FIT 所使用坐标格式的转换。
 *
 * <p>背景说明：
 * <ul>
 *   <li>华为运动数据中的 lbs 轨迹点 lat/lon 字段为「十进制度」格式，
 *       在中国大陆地区为 GCJ-02（火星坐标，带非线性偏移的加密坐标系）。</li>
 *   <li>佳明 FIT 文件中的 position_lat / position_long 字段为「半圆（semicircle）」，
 *       且地理坐标系为 WGS-84。</li>
 * </ul>
 *
 * <p>因此完整转换链路为：GCJ-02 度 → WGS-84 度 → 半圆值。若跳过中间一步，
 * 轨迹在佳明（WGS-84 底图）上会整体平移数百米——这正是未纠偏时看到的现象。
 *
 * <p>纠偏采用 GCJ-02 正向偏移公式的二分迭代逆解（eviltransform 的 gcj2wgs_exact 思路），
 * 精度优于 0.5 m。境外坐标（{@link #outOfChina}）不做纠偏，因其本身就是 WGS-84。
 *
 * <p>纬度与经度的偏移量互相耦合，必须成对转换，故只对外提供成对接口
 * {@link #toSemicircles(double, double)}。
 */
public class CoordinateConverter {

    /** 克拉索夫斯基椭球长半轴（GCJ-02 偏移公式使用的常量，米） */
    private static final double A = 6378245.0;
    /** 克拉索夫斯基椭球第一偏心率平方 */
    private static final double EE = 0.00669342162296594323;
    /** 逆解迭代的初始搜索半径（度） */
    private static final double INIT_DELTA = 0.01;
    /** 逆解收敛阈值（度），约 0.1 m */
    private static final double THRESHOLD = 0.000001;
    /** 逆解最大迭代次数 */
    private static final int MAX_ITER = 30;

    /** 是否启用 GCJ-02 → WGS-84 纠偏。默认开启。 */
    private final boolean enableGcj02Correction;

    public CoordinateConverter() {
        this(true);
    }

    public CoordinateConverter(boolean enableGcj02Correction) {
        this.enableGcj02Correction = enableGcj02Correction;
    }

    /**
     * 将华为 lbs 轨迹点的一对经纬度（十进制度）转换为 FIT 半圆值。
     *
     * @param latDegrees 纬度（度，GCJ-02）
     * @param lonDegrees 经度（度，GCJ-02）
     * @return [0]=position_lat 半圆值，[1]=position_long 半圆值
     */
    public int[] toSemicircles(double latDegrees, double lonDegrees) {
        double[] wgs = toWgs84(latDegrees, lonDegrees);
        return new int[]{degreesToSemicircles(wgs[0]), degreesToSemicircles(wgs[1])};
    }

    /**
     * 将一对 GCJ-02 经纬度（度）转换为 WGS-84 经纬度（度）。
     *
     * @param lat GCJ-02 纬度（度）
     * @param lon GCJ-02 经度（度）
     * @return WGS-84 经纬度，[0]=lat, [1]=lon
     */
    public double[] toWgs84(double lat, double lon) {
        if (!enableGcj02Correction || outOfChina(lat, lon)) {
            return new double[]{lat, lon};
        }
        return gcj02ToWgs84Exact(lat, lon);
    }

    /**
     * GCJ-02 → WGS-84 精确逆解：对正向偏移公式做二分迭代，直到正向结果与输入重合。
     */
    private double[] gcj02ToWgs84Exact(double gcjLat, double gcjLon) {
        double mLat = gcjLat - INIT_DELTA;
        double mLon = gcjLon - INIT_DELTA;
        double pLat = gcjLat + INIT_DELTA;
        double pLon = gcjLon + INIT_DELTA;
        double wgsLat = gcjLat;
        double wgsLon = gcjLon;
        for (int i = 0; i < MAX_ITER; i++) {
            wgsLat = (mLat + pLat) / 2;
            wgsLon = (mLon + pLon) / 2;
            double[] tmp = wgs84ToGcj02(wgsLat, wgsLon);
            double dLat = tmp[0] - gcjLat;
            double dLon = tmp[1] - gcjLon;
            if (Math.abs(dLat) < THRESHOLD && Math.abs(dLon) < THRESHOLD) {
                break;
            }
            if (dLat > 0) {
                pLat = wgsLat;
            } else {
                mLat = wgsLat;
            }
            if (dLon > 0) {
                pLon = wgsLon;
            } else {
                mLon = wgsLon;
            }
        }
        return new double[]{wgsLat, wgsLon};
    }

    /**
     * WGS-84 → GCJ-02 正向偏移（逆解的被迭代函数）。
     */
    private double[] wgs84ToGcj02(double lat, double lon) {
        if (outOfChina(lat, lon)) {
            return new double[]{lat, lon};
        }
        double dLat = transformLat(lon - 105.0, lat - 35.0);
        double dLon = transformLon(lon - 105.0, lat - 35.0);
        double radLat = lat / 180.0 * Math.PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * Math.PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * Math.PI);
        return new double[]{lat + dLat, lon + dLon};
    }

    private double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y
                + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * Math.PI) + 40.0 * Math.sin(y / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * Math.PI) + 320 * Math.sin(y * Math.PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y
                + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * Math.PI) + 20.0 * Math.sin(2.0 * x * Math.PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * Math.PI) + 40.0 * Math.sin(x / 3.0 * Math.PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * Math.PI) + 300.0 * Math.sin(x / 30.0 * Math.PI)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * 是否在中国大陆 GCJ-02 偏移生效范围之外（境外坐标无偏移，直接视为 WGS-84）。
     */
    private boolean outOfChina(double lat, double lon) {
        if (lon < 72.004 || lon > 137.8347) {
            return true;
        }
        return lat < 0.8293 || lat > 55.8271;
    }

    /**
     * 度 → 半圆值换算。半圆值 = 度 * (2^31 / 180)。
     * 与 FIT SDK 的 SemicirclesConverter.degreesToSemicircles 等价，
     * 独立实现以避免对 SDK 内部类的强依赖。
     */
    private int degreesToSemicircles(double degrees) {
        return (int) Math.round(degrees * (2147483648.0 / 180.0));
    }
}