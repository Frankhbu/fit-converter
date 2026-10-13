package me.frankhbu.fitconverter.model.huawei;

/**
 * 华为运动类型枚举。
 *
 * 来源：
 *   1. 官方定义（基础类型 1-13、9999）出自华为运动健康数据导出文档
 *      《Motion path detail data-Data Field Description.xlsx》的 "Sport type" sheet。
 *   2. 扩展类型（101/117/118/140/281/283）为华为运动健康 App 内部使用的细分运动类型，
 *      官方导出文档未列出，但实际数据（motion path detail data.json）中确实出现，
 *      中文名沿用华为运动健康 App 的命名。
 */
public enum HuaweiSportType {

    STAIRS_CLIMB(1, "stairs climb", "爬楼"),
    MOUNTAIN_CLIMB(2, "mountain climb", "登山"),
    CYCLE(3, "cycle", "骑行"),
    RUN(4, "run", "户外跑步"),
    WALK(5, "walk", "户外步行"),
    SLEEP_DEEP(6, "sleep deep", "深睡"),
    SLEEP_LIGHT(7, "sleep light", "浅睡"),
    SLEEP_AWAKE(8, "sleep awake", "清醒"),
    SWIMMING(9, "swimming", "游泳"),
    FITNESS(10, "fitness", "健身"),
    CHILD_FOOTPRINT(11, "childfootprint", "儿童足迹"),
    DENSE_CHILD_FOOTPRINT(12, "dense childfootprint", "密集儿童足迹"),
    STAND(13, "stand", "站立"),

    // 扩展类型（App 内部细分运动类型）
    INDOOR_RUN(101, "inDoorsRun", "室内跑步"),
    FREE_TRAINING(117, "freeTraining", "自由训练"),
    TRAIL_RUN(118, "trialRun", "越野跑"),
    STRENGTH_TRAINING(140, "strengthTraining", "力量训练"),
    INDOOR_WALK(281, "inDoorsWalk", "室内步行"),
    JUMP_ROPE(283, "jumpRope", "跳绳"),

    UNKNOWN(9999, "unknown", "未知类型");

    private final int code;
    private final String nameEn;
    private final String nameCn;

    HuaweiSportType(int code, String nameEn, String nameCn) {
        this.code = code;
        this.nameEn = nameEn;
        this.nameCn = nameCn;
    }

    public int getCode() {
        return code;
    }

    public String getNameEn() {
        return nameEn;
    }

    public String getNameCn() {
        return nameCn;
    }

    /**
     * 根据代码获取运动类型，未匹配时返回 UNKNOWN。
     */
    public static HuaweiSportType getByCode(int code) {
        for (HuaweiSportType type : values()) {
            if (type.getCode() == code) {
                return type;
            }
        }
        return UNKNOWN;
    }

    @Override
    public String toString() {
        return String.format("HuaweiSportType{code=%d, nameEn='%s', nameCn='%s'}", code, nameEn, nameCn);
    }
}