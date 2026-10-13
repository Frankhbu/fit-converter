package me.frankhbu.fitconverter.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 解析入口：遍历 input/sample/huawei/*.json 典型华为原始数据，解析为 Java 对象后再序列化为 JSON 输出。
 *
 * <p>不生成 FIT 文件，仅做「解析 → 序列化」，供
 * {@link me.frankhbu.fitconverter.compare.CompareJson} 做无损校验。
 * 本类不关心任何具体字段：读文件 → 交给 {@link JsonParser} → 整体序列化，
 * 顶层是对象还是数组与原文件保持一致。</p>
 */
public class ParseToJson {

    public static void main(String[] args) throws Exception {
        String sourceDir = "input/sample/huawei";
        String outputDir = "output/sample/huawei_json";
        if (args.length >= 1) sourceDir = args[0];
        if (args.length >= 2) outputDir = args[1];

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

        int total = 0;
        int success = 0;
        int fail = 0;

        for (File file : files) {
            total++;
            try {
                JsonParser.Parsed parsed = jsonParser.parseFile(file.getAbsolutePath());
                if (parsed.getRecords().isEmpty()) {
                    System.err.println("警告: " + file.getName() + " 解析到 0 条记录");
                    continue;
                }

                // 顶层结构与原文件保持一致：原来是对象就输出对象，是数组就输出数组
                Object payload = parsed.isTopLevelArray()
                        ? parsed.getRecords()
                        : parsed.getRecords().get(0);

                String json = JSON.toJSONString(payload, SerializerFeature.PrettyFormat);
                File outFile = new File(out, file.getName());
                Files.write(outFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
                success++;
                System.out.println("已输出: " + outFile.getName()
                        + " (记录数=" + parsed.getRecords().size()
                        + ", 轨迹点=" + parsed.getRecords().get(0).getTrackPoints().size() + ")");
            } catch (Exception e) {
                fail++;
                System.err.println("解析失败: " + file.getName() + " - " + e);
            }
        }

        System.out.println("序列化完成: 共 " + total + " 个文件, 成功 " + success + ", 失败 " + fail);
    }
}