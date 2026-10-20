package me.frankhbu.fitconverter.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import me.frankhbu.fitconverter.model.huawei.HuaweiSportType;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * 拆解入口：把华为健康导出整包的 {@code motion path detail data*.json}
 * （顶层为数组，内含多条运动记录）拆解为「每个活动一个 JSON 文件」，
 * 默认写入 {@code output/real/json/}，文件名为 {@code 开始时间_运动类型中文名.json}。
 * 输入既可以是单个 JSON 文件，也可以是目录（目录时遍历其中所有 {@code .json} 文件，
 * 顶层不是数组或内容为空的文件会被跳过）。
 *
 * <p>与 {@link ParseToJson} 的区别：本类直接操作原始 JSON 数组元素并原样序列化，
 * 不经过 {@code HuaweiSportRecord} 的解析/重渲染，因此 {@code attribute} 原始字符串
 * 完全保留，实现「无损拆解」。</p>
 */
public class SplitToJson {

    public static void main(String[] args) throws Exception {
        // 输入 JSON 路径（华为健康导出整包，可通过参数覆盖）
        String inputJson = "input/real/huawei/motion path detail data.json";
        // 输出目录
        String outputDir = "output/real/json";

        if (args.length >= 1) inputJson = args[0];
        if (args.length >= 2) outputDir = args[1];

        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        File input = new File(inputJson);
        if (!input.exists()) {
            System.err.println("输入路径不存在: " + inputJson);
            return;
        }

        File[] files;
        if (input.isDirectory()) {
            // 目录模式：遍历所有 .json 文件，按文件名排序保证处理顺序稳定
            files = input.listFiles(f -> f.isFile() && f.getName().toLowerCase().endsWith(".json"));
            if (files == null || files.length == 0) {
                System.err.println("目录下没有找到 .json 文件: " + inputJson);
                return;
            }
            Arrays.sort(files);
        } else {
            files = new File[] { input };
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        Set<String> usedNames = new HashSet<>();
        int success = 0;
        int fail = 0;

        for (File file : files) {
            int[] result = splitFile(file, outDir, sdf, usedNames);
            success += result[0];
            fail += result[1];
        }

        System.out.println("拆解完成: 成功 " + success + " 条, 失败 " + fail + " 条");
    }

    /** 拆解单个 JSON 文件，返回 {成功条数, 失败条数}。 */
    private static int[] splitFile(File file, File outDir, SimpleDateFormat sdf, Set<String> usedNames) throws Exception {
        int success = 0;
        int fail = 0;

        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        Object parsed;
        try {
            parsed = JSON.parse(content, Feature.OrderedField);
        } catch (Exception e) {
            System.err.println("跳过（JSON 解析失败）: " + file.getName() + " - " + e);
            return new int[] { 0, 0 };
        }
        if (!(parsed instanceof JSONArray)) {
            System.err.println("跳过（顶层不是数组）: " + file.getName());
            return new int[] { 0, 0 };
        }

        JSONArray array = (JSONArray) parsed;
        if (array.isEmpty()) {
            System.err.println("跳过（空数组）: " + file.getName());
            return new int[] { 0, 0 };
        }

        System.out.println("处理 " + file.getName() + ": " + array.size() + " 条运动记录");

        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj == null) {
                fail++;
                System.err.println(file.getName() + " 第 " + i + " 条记录为空，跳过");
                continue;
            }

            try {
                long startTime = obj.getLongValue("startTime");
                // 旧版导出用 sportType，新版导出（type=30001）用 subType 存同一套运动类型编码
                Integer sportTypeObj = obj.getInteger("sportType");
                if (sportTypeObj == null) {
                    sportTypeObj = obj.getInteger("subType");
                }
                int sportType = sportTypeObj == null ? HuaweiSportType.UNKNOWN.getCode() : sportTypeObj;
                String typeName = HuaweiSportType.getByCode(sportType).getNameCn();

                String timeStr = sdf.format(new Date(startTime));
                // 多条记录可能同秒开始且类型相同，重名时追加序号避免互相覆盖
                String baseName = timeStr + "_" + typeName;
                String name = baseName + ".json";
                int dup = 1;
                while (!usedNames.add(name)) {
                    name = baseName + "_" + dup++ + ".json";
                }
                File outFile = new File(outDir, name);

                String json = JSON.toJSONString(obj, SerializerFeature.PrettyFormat);
                Files.write(outFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
                success++;
                System.out.println("已生成: " + name + " (sportType=" + sportType + ")");
            } catch (Exception e) {
                fail++;
                System.err.println("拆解失败: " + file.getName() + " 第 " + i + " 条 - " + e);
                if (fail <= 3) {
                    e.printStackTrace();
                }
            }
        }

        return new int[] { success, fail };
    }
}