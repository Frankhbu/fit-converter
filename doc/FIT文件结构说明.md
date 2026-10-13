# FIT 文件结构说明

本文档基于真实样例整理，说明 FIT（Flexible and Interoperable Data Transfer）文件各部分的含义。

所有字段示例均来自以下真实文件，未凭空编造（出于隐私考虑，样例文件与转换产物未随仓库发布）：

| 样例 | 路径 | 来源 |
| --- | --- | --- |
| 佳明 FR945 跑步 | `input/sample/garmin/*_ACTIVITY_fr945.fit` | 佳明手表原始导出 |
| 佳明 Enduro2 | `input/sample/garmin/*_ACTIVITY_enduro2.fit` | 佳明手表原始导出 |
| 本项目转换产物 | `output/real/fit/20231229_084253_户外跑步.fit` | 华为数据经 fit-converter 转换 |

查看 FIT 内容的方法（把二进制解码成 CSV）：

```bash
java -jar libs/FitCSVTool.jar -b 某个文件.fit 输出.csv
```

## 一、物理结构：三段式

一个 FIT 文件 = **文件头** + **数据区** + **文件 CRC**。

以本项目产物的前 16 字节为例（`xxd` 实测）：

```
00000000: 0e20 a652 60e8 0000 2e46 4954 f15d 4000
          │  │  │       │         │         │
          │  │  │       │         │         └─ 数据区起始
          │  │  │       │         └─ Header CRC (0x5df1)
          │  │  │       └─ ".FIT" 文件类型标识（ASCII）
          │  │  └─ 数据区长度 0x0000e860 = 59488 字节（小端）
          │  └─ Profile 版本 0x52a6 = 21158 → 21.158
          └─ Header 长度 0x0e = 14；下一字节 0x20 = 32 → 协议版本 2.0
```

校验：14（头）+ 59488（数据）+ 2（文件 CRC）= 59504 字节，与文件实际大小一致。

对比佳明 FR945 样例头部 `0e10 9408 3845 0000 2e46 4954`：头长同为 14，但协议版本是 `0x10` = 1.0、Profile 版本 `0x0894` = 21.96。**说明协议/Profile 版本允许不同，解码器向下兼容。**

## 二、数据区：定义消息 + 数据消息

数据区不是固定表格，而是由一连串「记录」组成，每条记录属于两类之一：

| 类型 | 作用 |
| --- | --- |
| **Definition Message**（定义消息） | 声明「接下来某个编号的消息长什么样」：包含哪些字段、每个字段的字节数与类型 |
| **Data Message**（数据消息） | 按前面声明的格式，实际携带一行数据 |

两者通过 **Local Message Type（本地消息编号，0~15）** 关联。所以同一个文件里编号 `2` 可能先代表 `record`，后面重新定义后又代表别的消息——**编号是可复用的槽位，不是消息身份**。

这正是 FitCSVTool 导出 CSV 的列含义：

```
Type, Local Number, Message, Field 1, Value 1, Units 1, Field 2, Value 2, Units 2, ...
```

- `Type`：`Definition` 或 `Data`
- `Local Number`：本地消息编号
- `Message`：消息名（如 `record`、`session`）
- 之后是**不定长的 (字段名, 值, 单位) 三元组**，一行能排到上百组

> 统计消息条数时必须过滤 `Type == "Data"`，否则定义行会被重复计入。

## 三、消息层级与顺序

一次运动的典型组织方式（从小到大）：

```
record（秒级采样点）
  └─ lap / set（圈 / 组）
       └─ session（一节运动）
            └─ activity（整个文件，num_sessions 声明含几节）
```

本项目产物的实际写入顺序：`file_id` → `device_info` → `file_creator` → `sport` → `activity` →（跳绳额外：`developer_data_id` → `field_description` × 2）→ `session` → `lap`（或 `set`）× N → `event(start)` → `record` × N → `event(stop)`。

> 自定义字段的两条声明消息**必须写在引用它们的 `session` 之前**，否则解码器读到 session 时还不知道这两个字段的类型与长度。

> FIT 并不强制「汇总消息必须在采样点之后」。把 `session` / `lap` 提前写，解码器一样能正确解析；但 `file_id` 必须是第一条数据消息。

实测消息条数（仅 Data 行）：

| 消息 | 本项目产物（户外跑步） | 佳明 FR945 | 佳明 Enduro2 |
| --- | --- | --- | --- |
| `record` | 1922 | 122 | 3330 |
| `lap` | 3 | 4 | 11 |
| `event` | 2 | 3 | 4 |
| `session` | 1 | 1 | 1 |
| `activity` | 1 | 1 | 1 |
| `gps_metadata` | — | 154 | 4256 |
| `unknown` | — | 613 | 5507 |

## 四、核心消息逐个说明

### 1. `file_id` —— 文件身份证（必需，通常是第一条）

本项目产物实测：

| 字段 | 值 | 含义 |
| --- | --- | --- |
| `type` | `4` | 文件类型，4 = activity（运动文件） |
| `manufacturer` | `255` | 厂商，255 = development（自研/第三方工具） |
| `serial_number` | `784533586` | 设备序列号 |
| `time_created`| `1072744973` | 文件创建时间（FIT 纪元秒） |

佳明 FR945 同一消息多一个 `garmin_product = 3441`，`manufacturer = 1`（Garmin）。

### 2. `file_creator` —— 生成方软件版本

只有 `software_version`（本项目 `100`，佳明 `1300`）。

### 3. `device_info` —— 采集设备信息

本项目产物：`timestamp`、`device_index=0`（creator）、`source_type=5`（local）、`manufacturer=255`、`serial_number`、`product_name="Huawei Health"`、`software_version=1.0`。

佳明手表会写多条（FR945 共 14 条），分别描述手表本体、GPS 芯片、光学心率等各个子设备。

### 4. `sport` —— 运动类型

| 字段 | 本项目产物 | 佳明 FR945 |
| --- | --- | --- |
| `sport` | `1`（running） | `1` |
| `sub_sport` | `0`（generic） | `0` |
| `name` | `"户外跑步"` | `"跑步"` |

### 5. `event` —— 计时事件（开始/暂停/结束）

`event=0` 表示 timer 事件，配合 `event_type` 区分动作：

| 实测 | `event` | `event_type` | 含义 |
| --- | --- | --- | --- |
| 第 1 条 | `0` | `0` | timer **start** |
| 第 2 条 | `0` | `4` | timer **stop_all** |

佳明设备在中途暂停、按圈等时刻会写入更多 event。

### 6. `record` —— 秒级采样点（文件体积的主体）

每秒一条，是轨迹与各项指标的载体。本项目产物实测（有 GPS 的点）：

| 字段 | 实测值 | 单位 | 含义 |
| --- | --- | --- | --- |
| `timestamp` | `1072745047` | s | 采样时刻（FIT 纪元秒） |
| `position_lat` | `474848590` | semicircles | 纬度（半圆制，见第五节） |
| `position_long` | `1390801106` | semicircles | 经度 |
| `heart_rate` | `75` | bpm | 心率 |
| `enhanced_speed` | `0.0` | m/s | 速度 |
| `enhanced_altitude` | `54.0` | m | 海拔 |
| `distance` | `0.0` | m | **累计**距离（非本点增量） |
| `activity_type` | `1` | | 该点的运动类型 |

佳明 FR945 的 `record` 还有 `cadence`（步频 rpm）、`fractional_cadence`（步频小数部分）、`temperature`（℃）。

> 没有 GPS 的点不写 `position_*`，其余指标照写——这就是华为「室内跑步」能有完整 record 却没有轨迹的原因。

### 7. `lap` —— 圈（分段汇总）

本项目产物实测字段（取 `sportType_118_越野跑.fit` 的第 0 圈）：

| 字段 | 实测值 | 含义 |
| --- | --- | --- |
| `message_index` | `0` | 第几圈（从 0 开始） |
| `start_time` / `timestamp` | `1082593195` / `1082593906` | 圈的开始 / 结束时刻 |
| `total_elapsed_time` / `total_timer_time` | `711.0` / `711.0` s | 墙上时间 / 计时器时间（暂停不计） |
| `total_distance` | `1000.0` m | 本圈距离 |
| `total_calories` | `127` kcal | 本圈卡路里 |
| `avg_heart_rate` / `max_heart_rate` | `154` / `168` bpm | 平均 / 最大心率 |
| `total_ascent` / `total_descent` | `106` / `35` m | 累计爬升 / 下降 |
| `enhanced_min_altitude` / `enhanced_max_altitude` | `100.4` / `202.6` m | 最低 / 最高海拔 |
| `enhanced_avg_speed` / `enhanced_max_speed` | `1.406` / `4.0` m/s | 平均 / 最大速度 |
| `avg_running_cadence` / `max_running_cadence` | `64` / `102` rpm | 平均 / 最大步频（**单腿**） |
| `end_position_lat/long` | | 圈结束位置 |
| `lap_trigger` | `2` | 分圈触发原因（2 = distance，即按距离自动分圈） |
| `event` / `event_type` | `9` / `1` | 9 = lap，1 = stop |
| `intensity` | `0` | 0 = active（活动圈，非休息） |

自动分圈距离与佳明 Auto Lap 默认值保持一致：骑行每 5 km 一圈，其余运动每 1 km 一圈。每个完整圈的 `total_distance` 为整数的分圈距离（如骑行 `5000.0`），与佳明原生 FIT 一致；末段不足一圈仍单独成一圈，距离为总距离减去已完成的整圈距离，各圈距离之和等于 `session.total_distance`。

`total_timer_time` 直接取华为 `partTimeMap` 的累计用时之差（设备自身的净计时），`total_elapsed_time` 取本圈 record 时间戳跨度（墙钟）。`enhanced_avg_speed` = 本圈距离 ÷ 本圈 `total_timer_time`，与华为 App 的分段配速口径完全一致（骑行样本三圈实测 `3.043 / 2.168 / 1.9` m/s = 10.96 / 7.81 / 6.84 km/h，与华为原生显示一致）。

> **华为原始分段数据的两个字段**：
>
> - `partTimeMap`：key = 公里刻度（`"5.0"`），value = 到该刻度的累计净计时秒（骑行样本 `5.0:1643`、`10.0:3949`、`15.0:6581`）。差分即为每圈用时，本项目直接采用。
> - `paceMap`：key = `距离刻度（0.01 km 为单位）` 拼接 `累计秒（室内运动恒为 0）`，value = 该公里的配速（秒/公里）。例如户外步行 `"40001474": 379` 表示 4.00 km 处累计 1474 s、第 4 公里配速 379 s/km。整公里段的 value 恰好等于 `partTimeMap` 的相邻差值。
> - 末段不足一公里时 `paceMap` 给的是**折算到整公里的配速**，而非实际用时：室内步行末段 0.37 km、`"93700000": 405` → 实际用时 0.37 × 405 ≈ 150 s，与本项目用 `totalTime − 上一刻度累计` 算出的 150 s 一致；越野跑末段 0.46 km、配速 378 → 174 s，同样与实测的 174 s 吻合。
>
> 因此两者信息等价，转换只用 `partTimeMap`，末圈用总计时减去上一刻度累计，无需解析 `paceMap` 的编码 key。

**各运动类型的分段数据可得性**（10 个样本实测）：有距离的运动（骑行、户外跑步、户外步行、室内跑步、室内步行、越野跑）`partTimeMap` / `paceMap` 均为华为原生数据，分段用时不需要重算；无距离的运动（爬楼、跳绳、力量训练、自由训练）两个字段都是空 `{}`，华为本身就没有分段，产物按整段只写 1 个 lap（实测跳绳 `total_timer_time=418`、力量训练 `3005`，即全程总计时），不构造任何伪分段。

`enhanced_max_speed` 取圈内 `record` 速度的最大值。注意不能用 `record` 逐点速度的算术平均来算均速——逐点平均会漏掉停驻与低速样本，使均速系统性虚高（骑行样本实测会从 8.2 km/h 虚高到 13 km/h 以上）。无距离的运动（爬楼、力量训练等）不写速度字段。

**圈的 `total_ascent` / `total_descent` 按比例重标定**：华为只给整段的累计爬升/下降（`creepingWave` / `mTotalDescent`），不给分圈值。若各圈直接用圈内 `alti` 逐点差分自算，各圈之和会与 `session` 的权威总量不等（自算会把气压计噪声累积进去，实测越野跑自算之和比权威值高出数十米）。本项目的做法是：先算各圈的自算量作为**权重**，再按权重把 `session` 的权威总量按**最大余数法**分配到各圈，保证 `Σ lap.total_ascent ≡ session.total_ascent`（下降同理）。

权重的自算有两个必须守住的细节，否则分圈结果会畸形：

1. **逐点差分必须以浮点全程累加后再取整**。相邻秒的海拔差通常只有 0.1~0.4 m，若逐点 `Math.round` 会被全部舍成 0，平缓路段的自算爬升就恒等于 0，权重随之失真。骑行样本曾因此把 147 m 爬升几乎全压在第 3 圈（`0 / 3 / 145`），修正后为 `30 / 41 / 76`，与该段轨迹「前两圈在 26~33 m 间起伏、第三圈一路爬到 80.2 m」的实际地形吻合。
2. **余量用最大余数法分配，而非全部计入末圈**。按小数部分从大到小补齐剩余的整米，既保证求和精确，又不会把舍入残差堆到某一圈。

10 个样本实测 `Σ lap` 与 `session` 全部精确相等：骑行 3 圈之和 = 147 m / 100 m、越野跑 37 圈之和 = 1990 m / 1946 m、户外跑步 22 圈之和 = 116 m / 144 m、户外步行 10 圈之和 = 7 m / 8 m；`session` 本身没有累计爬升的运动（爬楼、室内跑步、跳绳）各圈也一律不写。

力量训练/自由训练这类按「组」计的运动，佳明用 `set` 而非 `lap` 组织，本项目也遵循该约定。

### 8. `session` —— 一节运动的总汇总

字段与 `lap` 大面积重合，但多了全局性字段。本项目产物实测特有部分：

| 字段 | 实测值 | 含义 |
| --- | --- | --- |
| `sport` / `sub_sport` | `1` / `0` | 运动类型 |
| `sport_profile_name` | `"户外跑步"` | 运动名称 |
| `first_lap_index` / `num_laps` | `0` / `3` | 本节包含的圈范围 |
| `total_strides` | `2015` cycles | 总步数（双脚一个循环计 1） |
| `start_position_lat/long`、`end_position_lat/long` | | 起 / 止坐标 |
| `nec_lat/long`、`swc_lat/long` | | 轨迹外接矩形的**东北角**与**西南角**（地图自动缩放用） |
| `trigger` | `0` | 0 = activity_end |
| `event` / `event_type` | `8` / `1` | 8 = session，1 = stop |

**`session` 汇总指标一律优先取华为设备端的权威统计，不由 record 序列重算**（重算结果与华为 App 显示不一致，是此前累计爬升对不上的根因）。来源对照（均在 `HW_EXT_TRACK_SIMPLIFY` 统计块中）：

| FIT 字段 | 华为原始字段 | 换算 |
| --- | --- | --- |
| `total_ascent` / `total_descent` | `creepingWave` / `mTotalDescent` | 分米 → 米（÷10），取下整；小数部分写入 `total_fractional_ascent/descent` |
| `avg_heart_rate` / `max_heart_rate` / `min_heart_rate` | `avgHeartRate` / `maxHeartRate` / `minHeartRate` | 原值，bpm |
| `enhanced_min_altitude` / `enhanced_max_altitude` | `mMinAlti` / `mMaxAlti` | 原值，米 |
| `avg_running_cadence` / `max_running_cadence` | `avgStepRate` / `bestStepRate` | 跑走类 ÷2 取单腿，骑行/跳绳原值 |
| `total_distance` / `total_calories` / `total_timer_time` | `totalDistance` / `totalCalories` / `totalTime` | 米 / 焦→卡 / 毫秒→秒 |
| `total_cycles`（跳绳） | `mExtendTrackDataMap.skipNum` | 跳绳总个数，原值 |
| `avg_cadence`（跳绳） | `mExtendTrackDataMap.skipSpeed` | `avgStepRate=0` 时改取，次/分原值 |

统计块中该字段缺失（为 `0` 或 `-1`）时才回落为对 record 原始序列的严格聚合；若连 record 通道也没有，则不写该字段。实测：骑行样本无 `cad` 通道且 `avgStepRate=0`，产物就不写步频；爬楼/室内跑步/跳绳的 `creepingWave=0`，产物不写累计爬升。

**海拔统计要识别「未采集哨兵」**：华为在没有气压/海拔数据时，把 `mMinAlti` 置为 `Float.MAX_VALUE`（`3.4028235E38`）、`mMaxAlti` 置为 `Float.MIN_VALUE`（`1.4E-45`）——这是「求极值前的初值从未被更新过」的典型残留，而非真实海拔。若原样换算会得到 `enhanced_max_altitude=0.0` 这种凭空捏造的值。故本项目在取用前判断 `min > max` 即视为无效，两个字段一律不写。跳绳样本（手机记录、无海拔通道）据此不再出现任何 `enhanced_*_altitude`，其余 9 个样本的海拔字段不受影响。

> `creepingWave`/`mTotalDescent` 的分米单位由恒等式 `(creepingWave − mTotalDescent) / 10 ≈ 末海拔 − 首海拔` 验证（越野跑样本 `(19902 − 19467)/10 = 43.5` m，与 `alti` 通道首末海拔差 `164.4 − 120.9 = 43.5` m 完全吻合）。

**累计爬升的整数与小数拆分**：`total_ascent`/`total_descent` 是整米字段（uint16, scale 1），FIT 为此配了 `total_fractional_ascent`/`total_fractional_descent`（uint8, scale 100）承载小数。故换算时取**下整**而非四舍五入，余下的小数写入 fractional 字段，避免 `1477` 分米被写成 `148` m 而与华为 App 差 1 米。实测产物：

| 样本 | 原始（分米） | `total_ascent` + fractional | `total_descent` + fractional |
| --- | --- | --- | --- |
| 骑行 | 1477 / 1004 | 147 + 0.70 | 100 + 0.40 |
| 越野跑 | 19902 / 19467 | 1990 + 0.20 | 1946 + 0.70 |
| 户外跑步 | 1167 / 1447 | 116 + 0.70 | 144 + 0.70 |
| 户外步行 | 75 / 84 | 7 + 0.50 | 8 + 0.40 |

佳明 FR945 的 `session` 还有本项目源数据不具备、因此**刻意不写**的字段：`total_training_effect`（训练效果）、`total_anaerobic_training_effect`、`training_load_peak`（训练负荷）、`avg/max/min_temperature`、`workout_feel`（主观感受）、`workout_rpe`（主观强度）。

**跳绳样例：源数据决定哪些字段能写。** 跳绳样本取 2024-02-25 的一次记录（`dataId=2554`、`deviceCode=1179403427` 为手机侧记录），源数据特点是 `HW_EXT_TRACK_DETAIL@is0`——**没有任何逐点明细**，心率/海拔/速度通道全缺，`hasTrackPoint=false`，因此产物 `record` 条数为 0，全部信息都浓缩在 `session` 与唯一的 1 个 `lap` 里。实测产物：

| FIT 字段 | 实测值 | 华为来源 | 说明 |
| --- | --- | --- | --- |
| `sport` / `sport_profile_name` | `84` / `"跳绳"` | `sportType=283` | 84 = jump_rope |
| `total_timer_time` / `total_elapsed_time` | `418.0` s | `totalTime=418000` | 毫秒 → 秒，约 7 分钟 |
| `total_cycles` | `1037` | `mExtendTrackDataMap.skipNum` | 跳绳个数，设备端权威值 |
| `total_calories` | `86` kcal | `totalCalories=86000` | 焦 → 卡（÷1000） |
| `avg_cadence` | `149` | `mExtendTrackDataMap.skipSpeed` | 次/分；`avgStepRate=0` 故改取此字段 |
| `interrupt_times`（自定义） | `2` | `mExtendTrackDataMap.interruptTimes` | 中断次数，见下节 |
| `max_consecutive_jumps`（自定义） | `382` | `mExtendTrackDataMap.maxSkippingTimes` | 最大连跳次数，见下节 |
| `total_distance` | `0.0` m | `totalDistance=0` | 跳绳无位移 |
| `num_laps` | `1` | `partTimeMap={}` | 无分段，整段 1 圈 |

几条「不写」的判断同样是源数据决定的：

- **心率一律留空**。该记录 `avgHeartRate` / `maxHeartRate` / `minHeartRate` 全为 `0`，且无 record 通道可聚合，故产物中**没有任何心率字段**。不从同日的日常心率等其他数据源跨源补齐——那不是这次运动实测的心率，写进去就是造假。
- **海拔不写**，`mMinAlti` / `mMaxAlti` 是上文所述的未采集哨兵。
- **最大频率不写**。`maxSkippingTimes` 是最大连跳「次数」，与「次/分」的频率不同量纲，不能充当 `max_cadence`；源数据没有最大频率，就不写。
- **累计爬升/下降不写**，`creepingWave` / `mTotalDescent` 均为 `0.0`。

`total_cycles=1037` 与 `totalSteps=1037` 一致，可交叉验证 `skipNum` 即跳绳总个数；`1037 / (418/60) ≈ 148.9` 次/分，与 `skipSpeed=149` 吻合，说明两者口径一致（`skipSpeed` 为全程均频）。

### 9. `activity` —— 文件级汇总

佳明文件中它通常写在末尾收口，本项目产物写在 `sport` 之后（位置不影响解析）。

| 字段 | 实测值 | 含义 |
| --- | --- | --- |
| `timestamp` | `1072744973` | 时刻（FIT 纪元秒，UTC） |
| `local_timestamp` | `1072773773` | **同一时刻的本地时间**，同为 FIT 纪元秒 |
| `total_timer_time` | `1921.0` s | 总计时时间 |
| `num_sessions` | `1` | 本文件含几节运动 |
| `type` | `0` | 0 = manual |
| `event` / `event_type` | `26` / `1` | 26 = activity，1 = stop |

> `local_timestamp - timestamp = 28800` 秒 = 8 小时，即东八区偏移。佳明 FR945 样例同样是 `1090187940 - 1090159140 = 28800`。**这是判断时区字段是否写对的最快方法。**

### 10. `developer_data_id` / `field_description` —— 自定义字段（Developer Fields）

FIT 允许第三方在标准 Profile 之外扩展字段，机制是两条「声明消息」+ 挂在宿主消息上的「开发者字段」：

- `developer_data_id`：声明一个开发者命名空间。`application_id`（16 字节 UUID，本项目固定常量）+ `developer_data_index`（本项目恒为 `0`）+ `application_version`（`100`，与 `file_creator.software_version` 一致）。
- `field_description`：声明该命名空间下的某个字段长什么样。`developer_data_index=0`、`field_definition_number`（命名空间内编号）、`fit_base_type_id`（实测 `132` = uint16）、`field_name`、`units`、`native_mesg_num=18`（= `session`，声明该字段挂在 session 上）。

本项目仅在跳绳上使用，用来承载标准 Profile 没有对应字段、但对跳绳很关键的两个指标：

| `field_definition_number` | `field_name` | `units` | 实测值 | 华为来源 |
| --- | --- | --- | --- | --- |
| `0` | `interrupt_times` | `times` | `2` | `mExtendTrackDataMap.interruptTimes` |
| `1` | `max_consecutive_jumps` | `counts` | `382` | `mExtendTrackDataMap.maxSkippingTimes` |

两者都是跳绳的核心体验指标（中断了几次、最长连跳多少个），塞进任何标准字段都会曲解语义，故走自定义字段。中断次数允许为 `0`（「一次没断」本身就是有效信息），与其余「0 即缺失」的统计字段判定口径不同。

> **`application_id` 的 16 个字节必须全部 ≤ `0x7F`**。FIT 的 `byte` 基础类型以 `0xFF` 为无效值，且 SDK 按有符号字节处理，高位为 1 的字节在解码端会退化成 `255`，导致写入与读出的 UUID 不一致。本项目的常量全部取低位字节，实测往返一致（`127|60|26|38|27|4|76|88|15|33|93|110|10|51|124|17`）。

> 解码兼容性：不认识这两个字段的软件会按 `field_description` 的长度声明安全跳过，不影响 session 其余字段的解析；FitCSVTool 则会直接按 `field_name` 把它们解出来。

## 五、关键编码约定

这几条是读写 FIT 最容易踩坑的地方：

### 1. 时间：FIT 纪元，不是 Unix 纪元

FIT 的 `date_time` 从 **1989-12-31 00:00:00 UTC** 起算，与 Unix 纪元相差 **631065600 秒**：

```
Unix 秒 = FIT 秒 + 631065600
```

验证：本项目产物 `timestamp = 1072744973` → `1072744973 + 631065600 = 1703810573` → UTC `2023-12-29 00:42:53` → 东八区 `08:42:53`，与文件名 `20231229_084253` 吻合。

### 2. 坐标：半圆制（semicircles）

经纬度不存浮点度数，而存 32 位整数：

```
度 = semicircles × (180 / 2^31)
```

验证：`474848590 × 180 / 2^31 = 39.8057...`（纬度），`1390801106 × 180 / 2^31 = 116.5519...`（经度）。

> **坐标系必须纠偏**：华为原始轨迹（lbs 点的 `lat` / `lon`）在中国大陆是 **GCJ-02 火星坐标**，而 FIT 的 `position_lat/long` 约定为 **WGS-84**。若只做「度 → 半圆」换算而不纠偏，轨迹在佳明底图上会整体平移约 500 m。
>
> 本项目在 `CoordinateConverter` 中用 GCJ-02 正向偏移公式的二分迭代逆解（精度优于 0.5 m）完成 GCJ-02 → WGS-84，再转半圆；纬度与经度的偏移互相耦合，故只提供成对接口 `toSemicircles(lat, lon)`。境外坐标（经度 < 72.004 或 > 137.8347，纬度 < 0.8293 或 > 55.8271）本身就是 WGS-84，跳过纠偏。
>
> 实测纠偏量（北京地区首个轨迹点，`tools/check_gcj_shift.py`）：户外跑步 510.9 m、骑行 508.7 m、越野跑 562.5 m，方向均为西偏南约 255°，与「GCJ-02 相对 WGS-84 向东北偏移」的已知规律一致。

### 3. `enhanced_*` 前缀

`speed` / `altitude` 有普通版和 `enhanced_` 版两套字段。普通版精度/量程受限（如 `altitude` 为 16 位），`enhanced_` 版是 32 位扩展。**现代设备优先写 `enhanced_*`**，本项目与佳明样例均如此。

### 4. 步频与 `fractional_cadence`

`cadence` 是整数 rpm，小数部分放在 `fractional_cadence`（实测佳明有 `0.875`、`0.3984375` 这类值）。另外 FIT 的跑步步频按**单腿**计，而华为原始数据是**双脚**步频，转换时需除以 2，余数 0.5 落到 `fractional_cadence`。

### 5. 无效值

FIT 用「全 1」表示字段无效（uint8 为 `0xFF`，uint32 为 `0xFFFFFFFF`）。所以**不写某字段**与**写入无效值**在语义上等价，缺失指标直接不写即可。

## 六、佳明设备文件里的其他消息

真实手表文件比转换产物丰富得多，以下消息在佳明样例中出现、但本项目源数据不具备，因此产物中没有：

| 消息 | FR945 条数 | Enduro2 条数 | 含义 |
| --- | --- | --- | --- |
| `gps_metadata` | 154 | 4256 | 高频 GPS 元数据（含速度分量、定位精度等） |
| `timestamp_correlation` | — | 417 | 设备时钟与 UTC 的校正关系 |
| `time_in_zone` | 5 | 71 | 各心率/功率区间的停留时长 |
| `split` / `split_summary` | 4 / 3 | 59 / 4 | 分段（如爬升段、下降段）及其汇总 |
| `user_profile` | 1 | 1 | 用户画像（身高体重、最大心率等） |
| `zones_target` | 1 | 1 | 心率/功率区间阈值设定 |
| `device_settings` | 1 | 1 | 设备设置 |
| `training_file` | 1 | 1 | 关联的训练文件 |
| `workout` / `workout_step` | 1 / 3 | — | 训练计划及其步骤 |
| `exercise_title` | 1 | — | 动作名称（力量训练用） |
| `unknown` | 613 | 5507 | **当前 Profile 未定义的消息**，多为佳明私有扩展 |

关于 `unknown`：FitCSVTool 能正确跳过并保持文件可解析，说明 FIT 的「定义消息」机制天然支持**前向兼容**——解码器不认识的消息也知道它有多长，可以安全跳过。这也意味着佳明文件里有相当比例的数据是私有的、公开 Profile 无法解读的。

## 七、本项目产物与真实设备文件的差距

| 维度 | 结论 |
| --- | --- |
| 结构合法性 | 226 个产物全部通过 FitCSVTool 解码，无 warning/error |
| 已覆盖消息 | `file_id`、`file_creator`、`device_info`、`sport`、`activity`、`event`、`record`、`lap`/`set`、`session`、`developer_data_id`/`field_description`（跳绳） |
| 未覆盖消息 | `user_profile`、`zones_target`、`time_in_zone`、`split`、`gps_metadata` 等 |
| 未覆盖原因 | 华为原始数据**不含**用户画像、心率区间阈值、GPS 精度等信息。按「不造假」原则，缺失指标一律不写，而非填默认值 |
