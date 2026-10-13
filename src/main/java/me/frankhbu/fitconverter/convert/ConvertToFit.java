package me.frankhbu.fitconverter.convert;

import me.frankhbu.fitconverter.model.huawei.HuaweiSportRecord;
import me.frankhbu.fitconverter.parser.JsonParser;

import java.io.File;

/**
 * 批量转换入口：把 input/sample/huawei/*.json 逐个转换为 FIT 文件输出到
 * output/sample/garmin_fit/。
 *
 * <p>输出文件名与输入同名（扩展名换为 .fit），便于与 input/sample/garmin 下同类型的
 * 真实佳明 FIT 文件做对照分析。</p>
 */
public class ConvertToFit {

    public static void main(String[] args) throws Exception {
        String sourceDir = args.length >= 1 ? args[0] : "input/sample/huawei";
        String outputDir = args.length >= 2 ? args[1] : "output/sample/garmin_fit";

        File src = new File(sourceDir);
        File out = new File(outputDir);
        if (!out.exists()) out.mkdirs();

        File[] files = src.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("未在目录 " + src.getAbsolutePath() + " 找到任何 .json 文件");
            return;
        }
        java.util.Arrays.sort(files);

        JsonParser jsonParser = new JsonParser();
        FitBuilder builder = new FitBuilder();

        int success = 0;
        int fail = 0;

        for (File file : files) {
            try {
                JsonParser.Parsed parsed = jsonParser.parseFile(file.getAbsolutePath());
                if (parsed.getRecords().isEmpty()) {
                    System.err.println("警告: " + file.getName() + " 解析到 0 条记录");
                    continue;
                }
                HuaweiSportRecord rec = parsed.getRecords().get(0);

                // 去掉文件名末尾的 .json 扩展名（忽略大小写），得到 base，再拼 .fit 作为输出文件名
                String base = file.getName().replaceAll("(?i)\\.json$", "");
                File outFile = new File(out, base + ".fit");
                builder.build(rec, outFile.getAbsolutePath());

                success++;
                System.out.println("已生成: " + outFile.getName()
                        + " (sportType=" + rec.getSportType()
                        + ", 轨迹点=" + rec.getTrackPoints().size() + ")");
            } catch (Exception e) {
                fail++;
                System.err.println("转换失败: " + file.getName() + " - " + e);
                e.printStackTrace();
            }
        }

        System.out.println("转换完成: 成功 " + success + " 个, 失败 " + fail + " 个");
    }
}