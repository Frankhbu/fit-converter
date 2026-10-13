package me.frankhbu.fitconverter.parser;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import me.frankhbu.fitconverter.model.huawei.HuaweiSportRecord;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * 读取华为运动数据 JSON（motion path detail data.json 或从中摘出的单条样本），
 * 解析成 {@link HuaweiSportRecord} 列表。
 *
 * <p><b>不硬编码任何字段名</b>：顶层字段由 fastjson 按属性名自动映射；
 * {@code attribute} 在 {@link HuaweiSportRecord#setAttribute(String)} 内部被解析成结构化对象。
 * 解析时启用 {@link Feature#OrderedField}，使未被强类型覆盖的 JSON 子对象保持原始键顺序。</p>
 */
public class JsonParser {

    /** 一个文件的解析结果 */
    public static class Parsed {
        /** 原始文件顶层是否为数组 */
        private final boolean topLevelArray;
        /** 解析出的记录 */
        private final List<HuaweiSportRecord> records;

        public Parsed(boolean topLevelArray, List<HuaweiSportRecord> records) {
            this.topLevelArray = topLevelArray;
            this.records = records;
        }

        public boolean isTopLevelArray() { return topLevelArray; }
        public List<HuaweiSportRecord> getRecords() { return records; }
    }

    /** 读取并解析文件，保留「顶层是对象还是数组」的信息 */
    public Parsed parseFile(String path) throws IOException {
        File file = new File(path);
        String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        return parseContent(content);
    }

    /** 读取并解析文件，只要记录列表（兼容原有调用） */
    public List<HuaweiSportRecord> parse(String path) throws IOException {
        return parseFile(path).getRecords();
    }

    /** 解析 JSON 文本：顶层可以是单条记录（对象）或记录数组 */
    public Parsed parseContent(String content) {
        List<HuaweiSportRecord> records = new ArrayList<>();
        if (content == null || content.trim().isEmpty()) {
            return new Parsed(false, records);
        }

        Object parsed = JSON.parse(content, Feature.OrderedField);
        boolean topLevelArray = parsed instanceof JSONArray;

        if (topLevelArray) {
            JSONArray array = (JSONArray) parsed;
            for (int i = 0; i < array.size(); i++) {
                JSONObject obj = array.getJSONObject(i);
                if (obj != null) {
                    records.add(toRecord(obj));
                }
            }
        } else if (parsed instanceof JSONObject) {
            records.add(toRecord((JSONObject) parsed));
        }

        return new Parsed(topLevelArray, records);
    }

    /** JSON 对象 → 运动记录（字段由 fastjson 自动映射，attribute 在 setter 内解析） */
    private HuaweiSportRecord toRecord(JSONObject obj) {
        return obj.toJavaObject(HuaweiSportRecord.class);
    }
}