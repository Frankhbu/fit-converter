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
import java.util.Date;

/**
 * 拆解入口：把华为健康导出整包的 {@code motion path detail data.json}
 * （顶层为数组，内含多条运动记录）拆解为「每个活动一个 JSON 文件」，
 * 默认写入 {@code output/real/json/}，文件名为 {@code 开始时间_运动类型中文名.json}。
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

        File file = new File(inputJson);
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);

        Object parsed = JSON.parse(content, Feature.OrderedField);
        if (!(parsed instanceof JSONArray)) {
            System.err.println("输入 JSON 顶层不是数组，无法拆解: " + inputJson);
            return;
        }

        JSONArray array = (JSONArray) parsed;
        System.out.println("共解析到 " + array.size() + " 条运动记录");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd_HHmmss");
        int success = 0;
        int fail = 0;

        for (int i = 0; i < array.size(); i++) {
            JSONObject obj = array.getJSONObject(i);
            if (obj == null) {
                fail++;
                System.err.println("第 " + i + " 条记录为空，跳过");
                continue;
            }

            try {
                long startTime = obj.getLongValue("startTime");
                int sportType = obj.getIntValue("sportType");
                String typeName = HuaweiSportType.getByCode(sportType).getNameCn();

                String timeStr = sdf.format(new Date(startTime));
                String name = timeStr + "_" + typeName + ".json";
                File outFile = new File(outDir, name);

                String json = JSON.toJSONString(obj, SerializerFeature.PrettyFormat);
                Files.write(outFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
                success++;
                System.out.println("已生成: " + name + " (sportType=" + sportType + ")");
            } catch (Exception e) {
                fail++;
                System.err.println("拆解失败: 第 " + i + " 条 - " + e);
                if (fail <= 3) {
                    e.printStackTrace();
                }
            }
        }

        System.out.println("拆解完成: 成功 " + success + " 条, 失败 " + fail + " 条");
    }
}