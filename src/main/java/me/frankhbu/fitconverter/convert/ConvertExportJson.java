package me.frankhbu.fitconverter.convert;

import me.frankhbu.fitconverter.model.huawei.HuaweiSportRecord;
import me.frankhbu.fitconverter.model.huawei.HuaweiSportType;
import me.frankhbu.fitconverter.parser.JsonParser;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * 华为健康导出包转换入口：读取华为健康导出的
 * {@code motion path detail data.json}（内含多条运动记录），逐条转换为 FIT 文件。
 *
 * <p>与 {@link ConvertToFit} 的区别：本类处理「一个 JSON 含多条记录」的原始导出文件，
 * 输出文件名为 {@code 开始时间_运动类型中文名.fit}，默认写入 {@code output/real/fit/}；
 * {@link ConvertToFit} 处理 {@code input/sample/huawei/} 下「一个 JSON 一条记录」的样本文件，
 * 输出与输入同名，便于与佳明真实 FIT 做对照。</p>
 */
public class ConvertExportJson {

    public static void main(String[] args) throws Exception {
        // 输入 JSON 路径（华为健康导出整包，可通过参数覆盖）
        String inputJson = "input/real/huawei/motion path detail data.json";
        // 输出目录
        String outputDir = "output/real/fit";

        if (args.length >= 1) inputJson = args[0];
        if (args.length >= 2) outputDir = args[1];

        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        JsonParser jsonParser = new JsonParser();
        FitBuilder builder = new FitBuilder();

        List<HuaweiSportRecord> records = jsonParser.parse(inputJson);
        System.out.println("共解析到 " + records.size() + " 条运动记录");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        int success = 0;
        int fail = 0;

        for (HuaweiSportRecord rec : records) {
            try {
                // 轨迹点与 SIMPLIFY 汇总统计在 JSON 解析阶段已由 HuaweiAttribute 自动完成

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

        System.out.println("转换完成: 成功 " + success + " 条, 失败 " + fail + " 条");
    }
}