package me.frankhbu.fitconverter.compare;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;

import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 通用 JSON 无损校验：递归对比 input/sample/huawei/ 原始文件与
 * output/sample/huawei_json/ 重新序列化的结果。
 *
 * <p>本类完全工作在 JSON 语义层面，不认识任何业务字段名：</p>
 * <ul>
 *   <li>对象：按 key 并集比较，忽略 key 顺序，缺失 / 多余的 key 单独报告；</li>
 *   <li>数组：长度先比较，再按下标逐项递归；</li>
 *   <li>数字：用 {@link BigDecimal} 做数值语义比较（1 与 1.0 视为相等）；</li>
 *   <li>字符串：严格相等；不等时定位首个差异字符下标并打印上下文；</li>
 *   <li>null / 布尔：直接比较。</li>
 * </ul>
 */
public class CompareJson {

    /** 每个文件最多打印的差异条数 */
    private static final int MAX_DIFF_PER_FILE = 20;
    /** 字符串差异上下文半径 */
    private static final int CONTEXT_RADIUS = 60;

    public static void main(String[] args) throws Exception {
        String sourceDir = args.length >= 1 ? args[0] : "input/sample/huawei";
        String outputDir = args.length >= 2 ? args[1] : "output/sample/huawei_json";

        File src = new File(sourceDir);
        File out = new File(outputDir);

        File[] files = src.listFiles((dir, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            System.err.println("未在目录 " + src.getAbsolutePath() + " 找到任何 .json 文件");
            return;
        }
        java.util.Arrays.sort(files);

        int same = 0;
        int diff = 0;
        int missing = 0;

        for (File file : files) {
            File target = new File(out, file.getName());
            if (!target.exists()) {
                missing++;
                System.out.println("[缺失] " + file.getName() + " 未生成对应输出文件");
                continue;
            }

            Object expected = readJson(file);
            Object actual = readJson(target);

            List<String> diffs = new ArrayList<>();
            compare("$", expected, actual, diffs);

            if (diffs.isEmpty()) {
                same++;
                System.out.println("[一致] " + file.getName());
            } else {
                diff++;
                System.out.println("[差异] " + file.getName() + " 共 " + diffs.size() + " 处");
                int limit = Math.min(diffs.size(), MAX_DIFF_PER_FILE);
                for (int i = 0; i < limit; i++) {
                    System.out.println("    " + diffs.get(i));
                }
                if (diffs.size() > limit) {
                    System.out.println("    ... 其余 " + (diffs.size() - limit) + " 处已省略");
                }
            }
        }

        System.out.println();
        System.out.println("对比完成: 一致 " + same + " 个, 有差异 " + diff + " 个, 输出缺失 " + missing + " 个");
    }

    private static Object readJson(File file) throws Exception {
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return JSON.parse(content, Feature.OrderedField);
    }

    /** 递归对比任意 JSON 节点 */
    private static void compare(String path, Object expected, Object actual, List<String> diffs) {
        if (diffs.size() >= MAX_DIFF_PER_FILE * 5) {
            return; // 差异过多，提前截断避免无意义遍历
        }

        if (expected ==null && actual == null) {
            return;
        }
        if (expected == null || actual == null) {
            diffs.add(path + ": 原始=" + brief(expected) + " 实际=" + brief(actual));
            return;
        }

        if (expected instanceof JSONObject && actual instanceof JSONObject) {
            compareObject(path, (JSONObject) expected, (JSONObject) actual, diffs);
            return;
        }
        if (expected instanceof JSONArray && actual instanceof JSONArray) {
            compareArray(path, (JSONArray) expected, (JSONArray) actual, diffs);
            return;
        }
        if (isNumber(expected) && isNumber(actual)) {
            BigDecimal a = toDecimal(expected);
            BigDecimal b = toDecimal(actual);
            if (a.compareTo(b) != 0) {
                diffs.add(path + ": 数值不同 原始=" + expected + " 实际=" + actual);
            }
            return;
        }
        if (expected instanceof String && actual instanceof String) {
            compareString(path, (String) expected, (String) actual, diffs);
            return;
        }
     if (!expected.getClass().equals(actual.getClass())) {
            diffs.add(path + ": 类型不同 原始=" + typeName(expected) + " 实际=" + typeName(actual));
            return;
        }
        if (!expected.equals(actual)) {
            diffs.add(path + ": 原始=" + brief(expected) + " 实际=" + brief(actual));
        }
    }

    private static void compareObject(String path, JSONObject expected, JSONObject actual, List<String> diffs) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(expected.keySet());
        keys.addAll(actual.keySet());
        for (String key : keys) {
            String child = path + "." + key;
            boolean inExpected = expected.containsKey(key);
            boolean inActual = actual.containsKey(key);
            if (inExpected && !inActual) {
                diffs.add(child + ": 输出缺少该字段 (原始值=" + brief(expected.get(key)) + ")");
            } else if (!inExpected && inActual) {
                diffs.add(child + ": 输出多出该字段 (输出值=" + brief(actual.get(key)) + ")");
            } else {
                compare(child, expected.get(key), actual.get(key), diffs);
            }
        }
    }

    private static void compareArray(String path, JSONArray expected, JSONArray actual, List<String> diffs) {
        if (expected.size() != actual.size()) {
            diffs.add(path + ": 数组长度不同 原始=" + expected.size() + " 实际=" + actual.size());
        }
        int n = Math.min(expected.size(), actual.size());
        for (int i = 0; i < n; i++) {
            compare(path + "[" + i + "]", expected.get(i), actual.get(i), diffs);
        }
    }

    private static void compareString(String path, String expected, String actual, List<String> diffs) {
        if (expected.equals(actual)) {
            return;
        }
        // 字符串可能内嵌 JSON（如华为 attribute 字段 "HW_EXT_...@is{...json...}"），
        // 先尝试提取内嵌 JSON 并按 JSON 语义比较，这样内嵌的
        // "3.4028235E38" vs "3.4028235E+38" 会被解析为 BigDecimal 并判为数值相等。
        Object expJson = extractEmbeddedJson(expected);
        Object actJson = extractEmbeddedJson(actual);
        if (expJson != null && actJson != null) {
            compare(path, expJson, actJson, diffs);
            return;
        }
        int idx = firstDiffIndex(expected, actual);
        StringBuilder sb = new StringBuilder();
        sb.append(path).append(": 字符串不同, 首个差异位于下标 ").append(idx)
          .append(" (原始长度=").append(expected.length())
          .append(", 实际长度=").append(actual.length()).append(")");
        sb.append("\n        原始: ").append(context(expected, idx));
        sb.append("\n        实际: ").append(context(actual, idx));
        diffs.add(sb.toString());
    }

    /**
     * 尝试提取字符串中内嵌的 JSON 对象或数组并解析。
     * 支持「整个字符串就是 JSON」以及「前缀标记 + 内嵌 JSON」（如华为
     * attribute 字段 "HW_EXT_...@is{...json...}"）两种场景。
     * 解析失败或找不到 JSON 时返回 null。
     */
    private static Object extractEmbeddedJson(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        int n = t.length();
        for (int i = 0; i < n; i++) {
            char c = t.charAt(i);
            if (c != '{' && c != '[') {
                continue;
            }
            try {
                Object o = JSON.parse(t.substring(i), Feature.OrderedField);
                if (o instanceof JSONObject || o instanceof JSONArray) {
                    return o;
                }
            } catch (Exception e) {
                // 该位置不是合法 JSON 起点，继续向后扫描
            }
        }
        return null;
    }

    private static int firstDiffIndex(String a, String b) {
        int n = Math.min(a.length(), b.length());
        for (int i = 0; i < n; i++) {
            if (a.charAt(i) != b.charAt(i)) {
                return i;
            }
        }
        return n;
    }

    private static String context(String s, int idx) {
        int from = Math.max(0, idx - CONTEXT_RADIUS);
        int to = Math.min(s.length(), idx + CONTEXT_RADIUS);
        String seg = s.substring(from, to).replace("\n", "\\n").replace("\r", "\\r");
        return (from > 0 ? "..." : "") + seg + (to < s.length() ? "..." : "");
    }

    private static boolean isNumber(Object o) {
        return o instanceof Number;
    }

    private static BigDecimal toDecimal(Object o) {
        if (o instanceof BigDecimal) return (BigDecimal) o;
        return new BigDecimal(o.toString());
    }

    private static String typeName(Object o) {
        if (o == null) return "null";
        if (o instanceof JSONObject) return "object";
        if (o instanceof JSONArray) return "array";
        if (o instanceof String) return "string";
        if (o instanceof Number) return "number";
        if (o instanceof Boolean) return "boolean";
        return o.getClass().getSimpleName();
    }

    private static String brief(Object o) {
        if (o == null) return "null";
        String s = String.valueOf(o).replace("\n", "\\n");
        return s.length() > 120 ? s.substring(0, 120) + "..." : s;
    }
}