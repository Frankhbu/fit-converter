package me.frankhbu.fitconverter;

import me.frankhbu.fitconverter.compare.CompareJson;
import me.frankhbu.fitconverter.convert.ConvertExportJson;
import me.frankhbu.fitconverter.convert.ConvertToFit;
import me.frankhbu.fitconverter.parser.ParseToJson;
import me.frankhbu.fitconverter.parser.ReadableToJson;
import me.frankhbu.fitconverter.parser.SplitToJson;

import java.util.Arrays;

/**
 * 总入口：按子命令分发到各功能模块。
 *
 * <pre>
 * 用法: java -jar fit-converter.jar &lt;子命令&gt; [参数...]
 *
 *   convert  [源目录] [输出目录]   华为样本 JSON → FIT        (默认 input/sample/huawei → output/sample/garmin_fit)
 *   export   [源文件] [输出目录]   华为健康导出整包 JSON → FIT        (默认 input/real/huawei/... → output/real/fit)
 *   parse    [源目录] [输出目录]   华为 JSON → 解析后重新序列化 (默认 input/sample/huawei → output/sample/huawei_json)
 *   split    [源文件] [输出目录]   华为健康导出整包 JSON → 拆解为每活动一个 JSON (默认 output/real/json)
 *   compare  [源目录] [输出目录]   原始 JSON 与重序列化结果做无损校验
 * </pre>
 *
 * <p>各子命令的实现类同时保留了自己的 {@code main} 方法，可单独调用。</p>
 */
public class Main {

    /** 子命令及其用途说明，用于打印帮助 */
    private static final String[][] USAGE = {
            {"convert", "华为样本 JSON → FIT（默认 input/sample/huawei → output/sample/garmin_fit）"},
            {"export", "华为健康导出整包 JSON → FIT（默认输出 output/real/fit，文件名为 开始时间_运动类型.fit）"},
            {"parse", "华为 JSON → 解析后重新序列化（默认 input/sample/huawei → output/sample/huawei_json）"},
            {"split", "华为健康导出整包 JSON → 拆解为每活动一个 JSON（默认输出 output/real/json）"},
            {"readable", "华为 JSON → attribute 字段可读化（默认 input/sample/huawei → output/sample/huawei_readable）"},
            {"compare", "原始 JSON 与重序列化结果做无损校验"},
    };

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            printUsage(null);
            return;
        }

        String command = args[0].toLowerCase();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);

        switch (command) {
            case "convert":
                ConvertToFit.main(rest);
                break;
            case "export":
                ConvertExportJson.main(rest);
                break;
            case "parse":
                ParseToJson.main(rest);
                break;
            case "split":
                SplitToJson.main(rest);
                break;
            case "readable":
                ReadableToJson.main(rest);
                break;
            case "compare":
                CompareJson.main(rest);
                break;
            case "-h":
            case "--help":
            case "help":
                printUsage(null);
                break;
            default:
                printUsage(command);
                break;
        }
    }

    private static void printUsage(String unknownCommand) {
        if (unknownCommand != null) {
            System.err.println("未知子命令: " + unknownCommand);
        }
        System.out.println("用法: java -jar fit-converter.jar <子命令> [参数...]");
        System.out.println();
        for (String[] item : USAGE) {
            System.out.printf("  %-9s %s%n", item[0], item[1]);
        }
    }
}