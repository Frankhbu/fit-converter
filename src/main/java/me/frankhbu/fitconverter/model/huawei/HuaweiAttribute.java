package me.frankhbu.fitconverter.model.huawei;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 华为运动记录 attribute 字段的结构化模型。
 *
 * <p>attribute 是一个由若干「标记块」用 {@code &&} 连接而成的字符串，形如：</p>
 * <pre>
 * HW_EXT_TRACK_DETAIL@is<逐行轨迹点>&&HW_EXT_TRACK_SIMPLIFY@is{...JSON...}
 * </pre>
 *
 * <p>本类不预设块的种类与数量：解析时按出现顺序记录每个 {@code HW_XXX@is} 标记与其内容，
 * 内容能识别就转成结构化对象（轨迹点列表 / SIMPLIFY 统计），识别不了就原样保留字符串。
 * {@link #toString()} 按同样顺序拼回，从而使「解析 → 还原」无损。</p>
 */
public class HuaweiAttribute {

    /** 块之间的分隔符 */
    public static final String BLOCK_SEPARATOR = "&&";
    /** 逐点轨迹块标记 */
    public static final String DETAIL_MARKER = "HW_EXT_TRACK_DETAIL@is";
    /** 汇总统计块标记 */
    public static final String SIMPLIFY_MARKER = "HW_EXT_TRACK_SIMPLIFY@is";

    /** attribute 中的一个标记块 */
    public static class Block {
        /** 块标记，如 {@code HW_EXT_TRACK_DETAIL@is} */
        private final String marker;
        /** 块内容：{@code List<TrackPoint>} / {@link HuaweiSimplifyData} / 原始 {@link String} */
        private Object content;
        /** 内容是否以换行结尾（逐行块还原时用） */
        private boolean trailingNewline;

        public Block(String marker, Object content) {
            this.marker = marker;
            this.content = content;
        }

        public String getMarker() { return marker; }
        public Object getContent() { return content; }
        public void setContent(Object content) { this.content = content; }
        public boolean isTrailingNewline() { return trailingNewline; }
        public void setTrailingNewline(boolean trailingNewline) { this.trailingNewline = trailingNewline; }

        /** 还原本块内容文本 */
        @SuppressWarnings("unchecked")
        public String render() {
            if (content == null) return "";
            if (content instanceof List) {
                StringBuilder sb = new StringBuilder();
                List<?> list = (List<?>) content;
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append('\n');
                    sb.append(String.valueOf(list.get(i)));
                }
                if (trailingNewline) sb.append('\n');
                return sb.toString();
            }
            return String.valueOf(content);
        }

        /** 渲染为「人类可读」的块内容（详见 {@link HuaweiAttribute#toReadableString()}） */
        @SuppressWarnings("unchecked")
        public String renderReadable() {
            if (content == null) return "";
            // 逐点轨迹：每点一行（与 render 一致，但确保末尾换行）
            if (content instanceof List) {
                StringBuilder sb = new StringBuilder();
                List<?> list = (List<?>) content;
                for (int i = 0; i < list.size(); i++) {
                    sb.append(String.valueOf(list.get(i)));
                    if (i < list.size() - 1 || trailingNewline) sb.append('\n');
                }
                return sb.toString();
            }
            // 汇总统计：JSON 缩进格式化（tab 换成 2 空格，读起来更自然）
            if (content instanceof HuaweiSimplifyData) {
                HuaweiSimplifyData d = (HuaweiSimplifyData) content;
                String pretty = JSON.toJSONString(d.getRaw(), SerializerFeature.PrettyFormat);
                return pretty.replace("\t", "  ");
            }
            return String.valueOf(content);
        }
    }

    /** 按原始顺序保存的块列表 */
    private final List<Block> blocks = new ArrayList<>();

    /** 块标记的通用形态：{@code HW_XXX@is} */
    private static final Pattern MARKER_PATTERN = Pattern.compile("HW_[A-Za-z0-9_]+@is");

    /**
     * 解析 attribute 字符串。
     *
     * <p>流程完全通用，不依赖任何具体标记名或字段名：</p>
     * <ol>
     *   <li>用 {@code HW_XXX@is} 定位所有块标记，块内容 = 本标记之后到下一个标记之前（去掉结尾的 {@code &&}）；</li>
     *   <li>内容以 <code>{</code> 开头且可被解析为 JSON 对象 → 转成 {@link HuaweiSimplifyData}（保序、保字面类型）；</li>
     *   <li>否则按 {@code \n} 逐行解析为 {@link TrackPoint}；</li>
     *   <li>两者都不适用 → 原样保留字符串。</li>
     * </ol>
     * <p>任何一步无法保证「还原无损」时，都退化为原样保留文本。</p>
     */
    public static HuaweiAttribute parse(String attribute) {
        HuaweiAttribute attr = new HuaweiAttribute();
        if (attribute == null || attribute.isEmpty()) {
            return attr;
        }

        List<int[]> marks = new ArrayList<>();
        Matcher m = MARKER_PATTERN.matcher(attribute);
        while (m.find()) {
            marks.add(new int[]{m.start(), m.end()});
        }

        // 没有标记，或第一个标记不在开头 → 无法可靠拆块，整串原样保留
        if (marks.isEmpty() || marks.get(0)[0] != 0) {
            attr.addBlock(new Block("", attribute));
            return attr;
        }

        List<Block> parsed = new ArrayList<>();
        for (int i = 0; i < marks.size(); i++) {
            String marker = attribute.substring(marks.get(i)[0], marks.get(i)[1]);
            int contentStart = marks.get(i)[1];
            int contentEnd = (i + 1 < marks.size()) ? marks.get(i + 1)[0] : attribute.length();
            String content = attribute.substring(contentStart, contentEnd);
            if (i + 1 < marks.size()) {
                // 块之间必须由 && 分隔，否则拆分不可逆
                if (!content.endsWith(BLOCK_SEPARATOR)) {
                    attr.addBlock(new Block("", attribute));
                    return attr;
                }
                content = content.substring(0, content.length() - BLOCK_SEPARATOR.length());
            }
            parsed.add(buildBlock(marker, content));
        }
        for (Block b : parsed) {
            attr.addBlock(b);
        }
        return attr;
    }

    /** 根据内容形态构造块 */
    private static Block buildBlock(String marker, String content) {
        // 形态一：JSON 对象
        String trimmed = content.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            try {
                JSONObject raw = JSON.parseObject(content, Feature.OrderedField);
                if (raw != null) {
                    return new Block(marker, new HuaweiSimplifyData(raw));
                }
            } catch (Exception ignore) {
                // 落到原样保留
            }
            return new Block(marker, content);
        }

        // 形态二：逐行 k=v; 轨迹点
        String body = content;
        boolean trailingNewline = body.endsWith("\n");
        if (trailingNewline) {
            body = body.substring(0, body.length() - 1);
        }
        if (!body.isEmpty()) {
            String[] lines = body.split("\n", -1);
            List<TrackPoint> points = new ArrayList<>(lines.length);
            boolean ok = true;
            for (String line : lines) {
                TrackPoint p = TrackPoint.parseLine(line);
                if (p == null) { ok = false; break; }
                points.add(p);
            }
            if (ok) {
                Block b = new Block(marker, points);
                b.setTrailingNewline(trailingNewline);
                return b;
            }
        }

        // 形态三：原样保留
        return new Block(marker, content);
    }

    /** 追加一个块 */
    public void addBlock(Block block) {
        if (block != null) blocks.add(block);
    }

    /** 全部块（按原始顺序） */
    public List<Block> getBlocks() {
        return blocks;
    }

    /** 按标记查找第一个块，找不到返回 null */
    public Block findBlock(String marker) {
        for (Block b : blocks) {
            if (marker.equals(b.getMarker())) return b;
        }
        return null;
    }

    // ==================== 结构化读取视图 ====================

    /** 逐点轨迹列表（DETAIL 块），没有则返回空列表 */
    @SuppressWarnings("unchecked")
    public List<TrackPoint> getTrackPoints() {
        Block b = findBlock(DETAIL_MARKER);
        if (b != null && b.getContent() instanceof List) {
            return (List<TrackPoint>) b.getContent();
        }
        return new ArrayList<>();
    }

    /** 汇总统计（SIMPLIFY 块），没有则返回 null */
    public HuaweiSimplifyData getSimplifyData() {
        Block b = findBlock(SIMPLIFY_MARKER);
        if (b != null && b.getContent() instanceof HuaweiSimplifyData) {
            return (HuaweiSimplifyData) b.getContent();
        }
        return null;
    }

    /** 设置逐点轨迹（没有 DETAIL 块时自动创建） */
    public void setTrackPoints(List<TrackPoint> points) {
        Block b = findBlock(DETAIL_MARKER);
        if (b == null) {
            b = new Block(DETAIL_MARKER, points);
            b.setTrailingNewline(true);
            blocks.add(0, b);
        } else {
            b.setContent(points);
        }
    }

    /** 设置汇总统计（没有 SIMPLIFY 块时自动创建） */
    public void setSimplifyData(HuaweiSimplifyData data) {
        Block b = findBlock(SIMPLIFY_MARKER);
        if (b == null) {
            blocks.add(new Block(SIMPLIFY_MARKER, data));
        } else {
            b.setContent(data);
        }
    }

    /** 块数量 */
    public int blockCount() {
        return blocks.size();
    }

    /**
     * 还原为原始 attribute 字符串：各块按原始顺序拼接，块间以 {@code &&} 连接。
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) sb.append(BLOCK_SEPARATOR);
            Block b = blocks.get(i);
            sb.append(b.getMarker()).append(b.render());
        }
        return sb.toString();
    }

    /**
     * 渲染为「人类可读」的多行文本：每个标记块独占一行，块内容展开。
     *
     * <p>与 {@link #toString()} 的区别：</p>
     * <ul>
     *   <li>标记（{@code HW_XXX@is}）后换行，内容另起一行；</li>
     *   <li>逐点轨迹（DETAIL）保持每点一行，用真实换行分隔；</li>
     *   <li>汇总统计（SIMPLIFY）的 JSON 用缩进格式（PrettyFormat）展示；</li>
     *   <li>块之间仍以 {@code &&} 分隔，但 {@code &&} 独占一行，便于阅读。</li>
     * </ul>
     */
    public String toReadableString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) {
                sb.append(BLOCK_SEPARATOR).append('\n');
            }
            Block b = blocks.get(i);
            sb.append(b.getMarker()).append('\n').append(b.renderReadable());
        }
        return sb.toString();
    }
}