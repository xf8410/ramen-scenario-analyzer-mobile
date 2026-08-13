# ramen-scenario-analyzer-mobile

赛马娘拉面杯（scenario 14）剧本分析器 — Android 手机版。

通过 [hlpatch](https://github.com/xf8410/hlpatch) SO 插件的 HTTP 端点（`http://127.0.0.1:18765/summary`）实时读取游戏运行时状态，解析拉面杯专用字段（素材/试吃会/盛況度/RMJ/URA决赛），以浮窗形式显示训练面板。

## 上游仓库与致谢

本项目的分析逻辑基于以下开源项目改写：

- **[URA-Plugins/OnsenScenarioAnalyzer](https://github.com/URA-Plugins/OnsenScenarioAnalyzer)**（作者：[EtherealAO](https://github.com/EtherealAO) / [xulai1001](https://github.com/xulai1001)）
  - 温泉杯剧本分析器的 Handler.cs 逻辑结构是本项目的参考基础
  - 原项目为 C# / .NET 10 / Spectre.Console 终端 UI，运行在 Windows 端 URA 主程序内
  - 本项目将其改写为 Kotlin / Android 浮窗 UI，数据来源从 MITM 协议代理改为 hlpatch IL2CPP 内存读取

- **[URA-Plugins](https://github.com/URA-Plugins)** 组织 — URA 插件框架及各剧本 Analyzer 插件

## 与原项目的区别

| 维度 | OnsenScenarioAnalyzer (原) | 本项目 |
|------|---------------------------|--------|
| 平台 | Windows 桌面 | Android 手机 |
| 语言 | C# .NET 10 | Kotlin |
| 数据来源 | URA MITM 代理反序列化协议 | hlpatch SO 端点内存读取 |
| UI | Spectre.Console 终端表格 | Android 浮窗 |
| 剧本 | 温泉杯 (scenario 12) | 拉面杯 (scenario 14) |
| 依赖 | UmamusumeResponseAnalyzer + EventLoggerPlugin | 无外部依赖，仅 OkHttp + Gson |

## 数据来源

```
游戏进程 (IL2CPP)
  ↓ hlpatch hook
http://127.0.0.1:18765/summary
  ↓ JSON
本应用解析 ramen 字段
  ↓
浮窗显示训练面板
```

`/summary` 端点返回的拉面杯专用字段：

- `ramen_values`: CheckPointPt / ExpectedCheckPointPt / SpecialFeelingNum / RecommendType
- `feeling_info[]`: 素材槽 (FeelingId / Remaining)
- `feeling_turn_info[]`: 素材获得回合
- `feeling_reduce_turn_info[]`: 素材回合缩减
- `command_feeling_info[]`: 训练指令素材消耗
- `active_effects[]`: ActiveEffect (EffectCategory / EffectId / EffectValue)
- `uraf_effect`: URA 决赛效果 (UrafEffectType / UrafEffectState)
- `selected_region_ids`: 已选地区
- `all_selected_region_ids`: 全部已选地区

## 构建

```bash
./gradlew assembleDebug
```

输出: `app/build/outputs/apk/debug/app-debug.apk`

## 许可证

GPL-3.0，与上游 URA-Plugins 项目一致。
