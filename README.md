# fit-converter

把华为运动健康导出的 JSON 数据，转换为佳明（Garmin）设备可识别的 FIT（`.fit`）活动文件，便于将华为侧的运动记录导入佳明生态。

## 功能特性

- 解析华为健康 App / Huawei Health Kit 导出的原始 JSON（整包导出或单活动 JSON）
- 按华为运动类型 → 佳明 FIT 活动类型自动映射，覆盖跑步、骑行、步行、游泳、力量训练等
- 生成符合官方 FIT 规范的 `.fit` 文件（基于 Garmin FIT Java SDK）
- 提供解析 / 拆解 / 可读化 / 无损校验等子命令，配套 FitCSVTool 对照脚本
- 内置 GCJ-02 → WGS-84 坐标转换
- 提供零依赖 Web 界面（Node 本地服务 + 浏览器），可视化完成整包导出转换

## 环境要求

- Java 8 及以上
- Maven 3（FIT SDK 通过 Maven 中央仓库依赖 `com.garmin:fit`，无需手动下载）
- Node.js（可选，仅 Web 界面需要，零 npm 依赖）

## 快速开始

```bash
mvn clean package
java -jar target/fit-converter.jar --help
```

### 子命令

| 子命令 | 说明 |
| --- | --- |
| `convert [源目录] [输出目录]` | 华为单活动样本 JSON → FIT（默认 `input/sample/huawei` → `output/sample/garmin_fit`） |
| `export [源文件] [输出目录]` | 华为健康导出整包 JSON → FIT（默认输出 `output/real/fit`，文件名 `开始时间_运动类型.fit`） |
| `split [源文件] [输出目录]` | 华为健康导出整包 JSON → 拆解为每活动一个 JSON（默认输出 `output/real/json`） |
| `parse [源目录] [输出目录]` | 华为 JSON → 解析后重新序列化（用于无损校验） |
| `readable [源目录] [输出目录]` | 华为 JSON → `attribute` 字段可读化 |
| `compare [源目录] [输出目录]` | 原始 JSON 与重序列化结果做字段级无损校验 |

各子命令的源/输出路径均可通过参数覆盖，典型流程：

1. 从华为运动健康 App 导出原始数据，拆解成每活动一个 JSON，放入 `input/real/huawei/`
2. 运行 `java -jar fit-converter.jar convert input/real/huawei output/real/fit` 生成 `.fit` 文件

## Web 界面

提供浏览器可视化操作入口，对应 `export` 子命令（整包导出 JSON → FIT）：

```bash
cd ui
npm start        # 零 npm 依赖，直接运行 server.js
```

浏览器访问 **http://127.0.0.1:8123**，操作步骤：

1. **选源文件**：点击「浏览…」弹出系统原生文件选择器，选中华为导出的整包 JSON（如 `motion path detail data.json`）。文件会自动上传并保存到 `input/real/huawei/`，路径回填到输入框（也可手动粘贴路径）
2. **选输出目录**：默认 `output/real/fit`，可点击「浏览…」在目录树中切换，或手动输入
3. **开始转换**：点击后日志面板实时滚动输出（含每条记录的生成结果与成功/失败统计）
4. **下载结果**：下方「输出文件」列表列出生成的 `.fit` 文件，点击即可下载

说明：浏览器出于安全限制拿不到本地文件的绝对路径，因此页面采用「原生选择器 + 上传保存」的方式供 Java 侧使用；不打包为桌面应用（如 Electron）是因为 macOS 企业管控机的安全策略会拦截未公证的二进制，本地 HTTP 服务零依赖且不受影响。

## 目录约定

出于隐私考虑，仓库**不包含任何真实运动数据**，`input/` 与 `output/` 需自行准备：

```
fit-converter/
├── pom.xml                  # Maven 配置（shade 打包可执行 fat jar）
├── libs/                    # FitCSVTool.jar（tools 脚本使用）
├── src/main/java/me/frankhbu/fitconverter/
│   ├── convert/             # JSON → FIT 核心转换逻辑
│   ├── model/huawei/        # 华为导出数据模型
│   ├── parser/              # JSON 解析 / 拆解 / 可读化
│   ├── compare/             # 无损校验
│   └── Main.java            # 子命令总入口
├── input/                   # 输入数据（自备，real/ 与 sample/ 按需创建）
├── output/                  # 转换产物（已 gitignore）
├── ui/                      # Web 界面（server.js + 静态页面，零依赖）
├── doc/                     # 格式与对照文档（见下）
└── tools/                   # 辅助脚本（FIT 导出 CSV、合法性校验等）
```

## 文档

- [FIT 文件结构说明](doc/FIT文件结构说明.md)
- [佳明运动类型对照表](doc/佳明运动类型对照表.md)
- [华为原始数据说明](doc/华为原始数据说明.md)
- [工程目录与数据流说明](doc/工程目录与数据流说明.md)

## 致谢

- [Garmin FIT Java SDK](https://github.com/garmin/fit-java-sdk)：FIT 编解码与 FitCSVTool
- 华为运动健康数据导出文档（`doc/华为原始数据说明.md` 中有字段级说明）

## License

[MIT](LICENSE)
