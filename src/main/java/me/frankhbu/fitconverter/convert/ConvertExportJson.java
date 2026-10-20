package me.frankhbu.fitconverter.convert;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import me.frankhbu.fitconverter.model.huawei.HuaweiAttribute;
import me.frankhbu.fitconverter.model.huawei.HuaweiSportRecord;
import me.frankhbu.fitconverter.model.huawei.HuaweiSportType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 华为健康导出包转换入口：读取华为健康导出的运动记录 JSON，逐条转换为 FIT 文件。
 *
 * <p>输入既可以是单个 JSON 文件，也可以是一个目录——目录时遍历其中所有 {@code .json}
 * 文件，逐个（每个文件可含一条或多条记录）转换。</p>
 *
 * <p>兼容两种导出格式：旧版整包用 {@code sportType} + {@code attribute}（轨迹详情与
 * 汇总统计合并在 attribute 里）；新版整包用 {@code type=30001} + {@code subType}，
 * 轨迹详情拆到 {@code detailData}、汇总统计拆到 {@code summaryData}。本类在解析阶段
 * 把新版字段归一化回 {@link HuaweiSportRecord} 的旧版形态，转换逻辑保持不变。</p>
 *
 * <p>与 {@link ConvertToFit} 的区别：本类处理原始导出记录（可能一个文件多条），
 * 输出文件名为 {@code 开始时间_运动类型中文名.fit}，默认写入 {@code output/real/fit/}；
 * {@link ConvertToFit} 处理 {@code input/sample/huawei/} 下「一个 JSON 一条记录」的样本文件，
 * 输出与输入同名，便于与佳明真实 FIT 做对照。</p>
 */
public class ConvertExportJson {

    /** 新版轨迹详情块内的分组占位符（{@code DETAIL_NULL}），归一化为旧版 attribute 时需剔除 */
    private static final String DETAIL_NULL = "DETAIL_NULL";

    public static void main(String[] args) throws Exception {
        // 输入 JSON 路径（华为健康导出整包，可为单个或多个记录；也可是一个目录，目录内每个 .json 逐个转换）
        String inputPath = "input/real/huawei/motion path detail data.json";
        // 输出目录
        String outputDir = "output/real/fit";

        if (args.length >= 1) inputPath = args[0];
        if (args.length >= 2) outputDir = args[1];

        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        // 归一化输入：目录 → 目录内所有 .json（按名排序）；文件 → 单个文件
        File src = new File(inputPath);
        File[] files;
        if (src.isDirectory()) {
            files = src.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files == null || files.length == 0) {
                System.err.println("未在目录 " + src.getAbsolutePath() + " 找到任何 .json 文件");
                return;
            }
            java.util.Arrays.sort(files);
        } else if (src.isFile()) {
            files = new File[]{src};
        } else {
            System.err.println("输入路径不存在: " + src.getAbsolutePath());
            return;
        }

        FitBuilder builder = new FitBuilder();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        int success = 0;
        int fail = 0;

        for (File file : files) {
            List<HuaweiSportRecord> records;
            try {
                records = loadRecords(file);
            } catch (Exception e) {
                fail++;
                System.err.println("解析失败: " + file.getName() + " - " + e);
                if (fail <= 3) {
                    e.printStackTrace();
                }
                continue;
            }

            for (HuaweiSportRecord rec : records) {
                try {
                    // 输出文件名：时间戳_运动类型中文名.fit
                    String timeStr = sdf.format(new Date(rec.getStartTime()));
                    String name = timeStr + "_" + HuaweiSportType.getByCode(rec.getSportType()).getNameCn() + ".fit";
                    File outFile = new File(outDir, name);

                    builder.build(rec, outFile.getAbsolutePath());
                    success++;
                    System.out.println("已生成: " + name + " (sportType=" + rec.getSportType()
                            + ", 轨迹点=" + rec.getTrackPoints().size() + ")");
                } catch (Exception e) {
                    fail++;
                    System.err.println("转换失败: " + rec.getRecordId() + " - " + e);
                    if (fail <= 3) {
                        e.printStackTrace();
                    }
                }
            }
        }

        System.out.println("转换完成: 成功 " + success + " 条, 失败 " + fail + " 条");
    }

    /**
     * 读取一个 JSON 文件并解析为运动记录列表。
     * 顶层为数组时逐元素解析，为对象时按单条解析。
     */
    private static List<HuaweiSportRecord> loadRecords(File file) throws Exception {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        List<HuaweiSportRecord> records = new ArrayList<>();
        Object parsed = JSON.parse(content, Feature.OrderedField);
        if (parsed instanceof JSONArray) {
            JSONArray array = (JSONArray) parsed;
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj != null) records.add(toRecord(obj));
            }
        } else if (parsed instanceof JSONObject) {
            records.add(toRecord((JSONObject) parsed));
        }
        return records;
    }

    /**
     * 单条记录 JSON → {@link HuaweiSportRecord}，兼容旧版（attribute/sportType）与
     * 新版（detailData/summaryData/subType）两种字段。
     */
    private static HuaweiSportRecord toRecord(JSONObject obj) {
        // 旧版：自带 attribute，字段由 fastjson 直接映射
        if (obj.containsKey("attribute")) {
            return obj.toJavaObject(HuaweiSportRecord.class);
        }

        // 新版：summaryData（JSON 字符串）解析为对象后补平顶层统计字段
        String summaryStr = obj.getString("summaryData");
        JSONObject summary = null;
        if (summaryStr != null && !summaryStr.trim().isEmpty()) {
            try {
                summary = JSON.parseObject(summaryStr, Feature.OrderedField);
            } catch (Exception ignore) {
                summary = null;
            }
        }
        if (summary != null) {
            // 仅补顶层缺失的字段（总距离/时长/卡路里/步数、配速/分段表等），已有字段以顶层为准
            for (Map.Entry<String, Object> e : summary.entrySet()) {
                if (!obj.containsKey(e.getKey())) obj.put(e.getKey(), e.getValue());
            }
        }

        // 运动类型：新版编码在 subType（与旧 sportType 同一套），忽略 summaryData 内的 sportType
        Integer subType = obj.getInteger("subType");
        obj.put("sportType", subType == null ? HuaweiSportType.UNKNOWN.getCode() : subType);

        // 重建 attribute：DETAIL 块取 detailData（剔除 DETAIL_NULL 分组占位符），SIMPLIFY 块取 summaryData 原文
        String detail = obj.getString("detailData");
        if (detail == null || detail.isEmpty()) detail = "0";
        detail = detail.replace(DETAIL_NULL, "");
        StringBuilder attr = new StringBuilder();
        attr.append(HuaweiAttribute.DETAIL_MARKER).append(detail);
        if (summaryStr != null && !summaryStr.trim().isEmpty()) {
            attr.append(HuaweiAttribute.BLOCK_SEPARATOR)
                    .append(HuaweiAttribute.SIMPLIFY_MARKER)
                    .append(summaryStr);
        }
        obj.put("attribute", attr.toString());

        return obj.toJavaObject(HuaweiSportRecord.class);
    }
}
