package me.frankhbu.fitconverter.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import me.frankhbu.fitconverter.model.huawei.HuaweiAttribute;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 可读化入口：把华为运动数据 JSON 的 {@code attribute} 字段展开成人类友好的多行形式。
 *
 * <p>原始 {@code attribute} 是一个巨大的单行字符串，内部用 {@code \n} 分隔轨迹点、
 * 用 {@code &&} 连接标记块、SIMPLIFY 块是紧凑 JSON。</p>
 *
 * <p>本类输出一个「合法 JSON 主体 + 末尾可读化区块」的混合文件：</p>
 * <ul>
 *   <li>JSON 主体：移除了 {@code attribute} 字段，其余字段原样保留，仍是合法 JSON；</li>
 *   <li>文件末尾：追加一段 {@code attribute} 的可读化文本，用真实换行展示——DETAIL 逐点一行、SIMPLIFY 缩进展开。</li>
 * </ul>
 *
 * <p>这样既保留了机器可解析的 JSON，又把 attribute 以肉眼可读的多行形式呈现，避免被转义成 {@code \n}、{@code \t} 字面量。</p>
 */
public class ReadableToJson {

    private static final String SECTION_MARK = "========== attribute 可读化 ==========";

    public static void main(String[] args) throws Exception {
        // 默认：处理样本目录（一个文件一条记录）
        String source = "input/sample/huawei";
        String outputDir = "output/sample/huawei_readable";

        if (args.length >= 1) source = args[0];
        if (args.length >= 2) outputDir = args[1];

        File src = new File(source);
        File out = new File(outputDir);
        if (!out.exists()) out.mkdirs();

        if (src.isDirectory()) {
            File[] files = src.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
            if (files == null || files.length == 0) {
                System.err.println("未在目录 " + src.getAbsolutePath() + " 找到任何 .json 文件");
                return;
            }
            java.util.Arrays.sort(files);
            int success = 0;
            int fail = 0;
            for (File file : files) {
                try {
                    processFile(file, out);
                    success++;
                } catch (Exception e) {
                    fail++;
                    System.err.println("可读化失败: " + file.getName() + " - " + e);
                }
            }
            System.out.println("可读化完成: 共 " + files.length + " 个文件, 成功 " + success + ", 失败 " + fail);
        } else {
            processFile(src, out);
            System.out.println("可读化完成: " + src.getName());
        }
    }

    /** 处理单个 JSON 文件：顶层可能是单条记录（对象）或记录数组 */
    private static void processFile(File file, File outDir) throws Exception {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        Object parsed = JSON.parse(content, Feature.OrderedField);

        List<String> readableAttributes = new ArrayList<>();
        Object jsonBody;

        if (parsed instanceof JSONArray) {
            JSONArray array = (JSONArray) parsed;
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj != null) {
                    readableAttributes.add(extractReadable(obj, "记录 " + (i + 1)));
                }
            }
            jsonBody = array;
        } else if (parsed instanceof JSONObject) {
            JSONObject obj = (JSONObject) parsed;
            readableAttributes.add(extractReadable(obj, null));
            jsonBody = obj;
        } else {
            System.err.println("跳过（顶层既非对象也非数组）: " + file.getName());
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(JSON.toJSONString(jsonBody, SerializerFeature.PrettyFormat));
        if (!readableAttributes.isEmpty()) {
            sb.append("\n\n").append(SECTION_MARK).append("\n");
            for (String ra : readableAttributes) {
                sb.append(ra).append("\n");
            }
        }

        File outFile = new File(outDir, file.getName());
        Files.write(outFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("已输出: " + outFile.getName());
    }

    /**
     * 从一条记录中取出 attribute，移除该字段，并生成可读化文本。
     *
     * @param label 记录标注（数组形态时用于区分，如 "记录 1"）；单记录时传 null。
     * @return 该记录的可读化 attribute 文本；无 attribute 时返回 null。
     */
    private static String extractReadable(JSONObject obj, String label) {
        String attribute = obj.getString("attribute");
        if (attribute == null || attribute.isEmpty()) {
            return null;
        }
        // 从 JSON 主体中移除 attribute，保持主体简洁、合法
        obj.remove("attribute");

        HuaweiAttribute parsed = HuaweiAttribute.parse(attribute);
        String readable = parsed.toReadableString();

        if (label == null) {
            return readable;
        }
        return "--- " + label + " ---\n" + readable;
    }
}