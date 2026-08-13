# hlpatch /summary 拉面杯字段字典

> 本文档解释 `/summary` 端点返回的每个字段的含义、数据类型、取值范围和业务语义。
> 证据来源：hlpatch IL2CPP 内存读取 + umamusume-scenario-mechanics 逆向文档。

## 完整响应结构

```
/summary → JSON
├── scenario                    # 当前剧本名 ("Ramen" = 拉面杯)
├── raw_total_turn_num          # 游戏内部回合号 (1-based, 1~78)
├── chara_info                  # 角色通用状态
│   ├── speed / stamina / power / guts / wiz   # 五维属性
│   ├── vital / max_vital                      # 体力 / 体力上限
│   ├── motivation                             # 干劲 (1=绝不调 ~ 5=绝好调)
│   └── skill_point                            # 技能点
├── ramen                       # 拉面杯专用数据 (仅 scenario_id=14 时存在)
│   ├── ramen_values            # 标量字段 (ObscuredInt getter 结果)
│   ├── feeling_info[]          # 素材槽
│   ├── feeling_turn_info[]     # 素材获得回合
│   ├── feeling_reduce_turn_info[]  # 素材回合缩减
│   ├── command_feeling_info[]  # 训练指令素材消耗
│   ├── active_effects[]        # 持续效果
│   ├── uraf_effect             # URA 决赛效果
│   ├── selected_region_ids     # 当前年已选地区
│   └── all_selected_region_ids # 全部三年已选地区
└── trainings[]                 # 训练预览信息
```

---

## 1. scenario

| 项 | 值 |
|---|---|
| 类型 | string |
| 拉面杯值 | `"Ramen"` |
| 来源 | hlpatch 根据 scenario_id 映射 |

判断当前是否在拉面杯剧本：`scenario == "Ramen"`

---

## 2. raw_total_turn_num

| 项 | 值 |
|---|---|
| 类型 | int |
| 范围 | 1~78 (1-based) |
| 含义 | 游戏内部总回合数 |

关键回合：
- **turn 2**: 第1年地区选择 (4月后半)
- **turn 24**: 第1年 RMJ 结算 (12月后半)
- **turn 26**: 第2年地区选择
- **turn 48**: 第2年 RMJ 结算
- **turn 50**: 第3年地区选择
- **turn 72**: 第3年 RMJ 结算 (最终)

---

## 3. chara_info

| 字段 | 类型 | 含义 | 范围 |
|------|------|------|------|
| speed | int | 速度 | 1~1200+ |
| stamina | int | 耐力 | 1~1200+ |
| power | int | 力量 | 1~1200+ |
| guts | int | 根性 | 1~1200+ |
| wiz | int | 智力 | 1~1200+ |
| vital | int | 当前体力 | 0~max_vital |
| max_vital | int | 体力上限 | 70~120 |
| motivation | int | 干劲 | 1=绝不调, 2=不调, 3=普通, 4=好调, 5=绝好调 |
| skill_point | int | 技能点 | 0~ |

拉面杯五维上限增量：速+950/耐+600/力+500/根+500/智+600 [MDB]

---

## 4. ramen.ramen_values

一个 key-value map，包含以下 ObscuredInt getter 的值：

| Key (getter名) | 含义 | 范围 | 说明 |
|----------------|------|------|------|
| `CheckPointPt` | 盛況度累计Pt | 0~9999 | 当前热度分数 |
| `ExpectedCheckPointPt` | 预计盛況度Pt | 0~9999 | 候选操作后的预览值 |
| `SpecialFeelingNum` | 隠し味(万能替代素材)数量 | 0~4 | 上限4 [截图确认] |
| `RecommendType` | 推荐类型 | 0~? | UI推荐操作类型 |

### 盛況度Pt档位 [MDB check_point_pt_effect]

| 档位 | Pt范围 | 训练效果% | 得意率 | 灵感发生率% |
|------|--------|----------|--------|------------|
| 0 | 0-249 | 0% | 50 | 0% |
| 1 | 250-499 | 3% | 55 | 30% |
| 2 | 500-999 | 5% | 60 | 40% |
| 3 | 1000-1499 | 8% | 63 | 50% |
| 4 | 1500-1999 | 10% | 65 | 60% |
| 5 | 2000-2499 | 12% | 68 | 70% |
| 6 | 2500-2999 | 14% | 70 | 80% |
| 7 | 3000-3499 | 16% | 73 | 90% |
| 8 | 3500-3999 | 18% | 75 | 100% |
| 9 | 4000-4999 | 20% | 78 | 110% |
| 10 | 5000+ | 20% | 80 | 120% |

### RMJ 结算阈值 [MDB check_point]

| 年 | 回合 | 成功线 | 大成功线 |
|----|------|--------|---------|
| 1 | turn 24 | 1500 | 无 (0) |
| 2 | turn 48 | 3000 | 无 (0) |
| 3 | turn 72 | 3500 | 5000 |

### 试吃会Pt公式 [实机确认: 300×110%=330]

```
获得Pt = 基础值 × (100 + feeling_bonus)%
基础值: 第1年300 / 第2年400 / 第3年500 [MDB check_point_pt.gain_pt]
feeling_bonus: 按本次实际使用资源总数(含隠し味代用)分档
  0份  → +0%
  1-2份 → +3%
  3-4份 → +5%
  5-10份 → +10%
```

---

## 5. ramen.feeling_info[]

素材槽，数组，最多10个元素 [代码确认: FeelingInfoArray长度10]。

| 字段 | 类型 | 含义 |
|------|------|------|
| feeling_id | int | 素材类型: 1=麺(Noodle), 2=湯(Soup), 3=配(Topping) |
| remaining | int | 剩余数量 (通常为1, 每槽1个素材) |

### 素材机制

- 3种进度条：麺/湯/配，满7格=1素材入槽 [MDB acquisition_rules]
- 溢出不结转 [MDB: overflow_progress_carried=false]
- 10槽共享FIFO：满了之后新素材顶掉最旧的 [代码确认]
- 隠し味(万能替代)不在槽里，单独用 `SpecialFeelingNum` 计数，上限4

---

## 6. ramen.feeling_turn_info[]

素材获得回合表，表示接下来哪些回合会获得素材。

| 字段 | 类型 | 含义 |
|------|------|------|
| feeling_id | int | 将获得的素材类型 |
| remaining | int | 距离获得的剩余回合数 |

---

## 7. ramen.feeling_reduce_turn_info[]

素材回合缩减，减少获得素材所需回合数的效果。

| 字段 | 类型 | 含义 |
|------|------|------|
| feeling_id | int | 被缩减的素材类型 |
| remaining | int | 剩余缩减次数/量 |

---

## 8. ramen.command_feeling_info[]

训练指令的素材消耗信息。

| 字段 | 类型 | 含义 |
|------|------|------|
| CommandType | int | 训练类型 (0=速, 1=耐, 2=力, 3=根, 4=智) |
| CommandId | int | 指令ID |
| FeelingId | int | 消耗的素材类型 |

---

## 9. ramen.active_effects[]

当前生效的持续效果列表。

| 字段 | 类型 | 含义 |
|------|------|------|
| EffectCategory | int | 效果来源类别 (见下表) |
| EffectId | int | 效果ID |
| EffectValue | int | 效果数值 |

### EffectCategory 枚举 [证据: effect_category_and_support_type_gate_formula.md, IL2CPP 方法体确认]

| 值 | 名称 | 含义 |
|----|------|------|
| 0 | Basic | 基础效果 (试吃会基础效果) |
| 1 | Region | 地区效果 |
| 2 | URAF Common | URA 决赛共通效果 |
| 3 | URAF Unique | URA 决赛固有效果 |

### EffectType 枚举 (EffectId 关联的 effect_type)

| effect_type | 含义 |
|-------------|------|
| 2 | 属性训练效果% |
| 4 | 友情% |
| 15 | 绊 |
| 19 | 灵感获取次数 |
| 20 | 额外人头 (地区拉人) |
| 21 | SP训练效果% |
| 22 | 失败率下降 |

---

## 10. ramen.uraf_effect

URA 决赛效果。

| 字段 | 类型 | 含义 |
|------|------|------|
| UrafEffectType | int | URA效果类型 (0=未激活) |
| UrafEffectState | int | URA效果状态 (0=未激活) |

**[UNKNOWN]** UrafEffectType 和 UrafEffectState 的具体枚举映射未完全闭合。
需要决赛回合的实机观测确认。

---

## 11. ramen.selected_region_ids / all_selected_region_ids

| 字段 | 类型 | 含义 |
|------|------|------|
| selected_region_ids | int[] | 当前年已选的3个地区ID |
| all_selected_region_ids | int[] | 三年全部已选地区ID |

### 地区ID表 [MDB region_select + region_effect]

**第1年 (Junior, region_id 1-5):**

| ID | 名称 | 追加训练 |
|----|------|---------|
| 1 | 札幌 | 速度 |
| 2 | 函館 | 耐力 |
| 3 | 新潟 | 力量 |
| 4 | 福島 | 根性 |
| 5 | 東京 | 智力 |

**第2年 (Classic, region_id 6-10):**

| ID | 名称 | 追加训练 |
|----|------|---------|
| 6 | 中山 | **全部5项各+1人** |
| 7 | 中京 | 力量、根性 |
| 8 | 京都 | 耐力、根性 |
| 9 | 阪神 | 耐力、力量 |
| 10 | 小倉 | 智力 |

**第3年 (Senior, region_id 11-20):**

| ID | 名称 | 追加训练 |
|----|------|---------|
| 11 | 札幌 | 速度 |
| 12 | 函館 | 耐力 |
| 13 | 新潟 | 力量 |
| 14 | 福島 | 根性 |
| 15 | 東京 | 智力 |
| 16 | 中山 | 速度、力量、智力 |
| 17 | 中京 | 速度、力量、根性 |
| 18 | 京都 | 速度、耐力、智力 |
| 19 | 阪神 | 速度、耐力、力量 |
| 20 | 小倉 | 速度、根性、智力 |

证据: `region_support_roster_extra_appearance_matrix.md`

---

## 12. trainings[]

训练预览信息，每个训练类型一项。

| 字段 | 类型 | 含义 |
|------|------|------|
| train_type | int | 0=速, 1=耐, 2=力, 3=根, 4=智, 5=外出, 6=休息, 7=比赛 |
| speed | int | 本次训练速度增量预览 |
| stamina | int | 本次训练耐力增量预览 |
| power | int | 本次训练力量增量预览 |
| guts | int | 本次训练根性增量预览 |
| wiz | int | 本次训练智力增量预览 |
| is_enable | int | 是否可选 (1=可选, 0=不可选) |

---

## 快速参考：关键回合数据采集清单

| 回合 | 事件 | 需要观测的字段 |
|------|------|--------------|
| 2 | 第1年地区选择 | selected_region_ids |
| 24 | 第1年RMJ | checkpoint_pt (前后diff), CheckPointInfoArray |
| 26 | 第2年地区选择 | selected_region_ids |
| 48 | 第2年RMJ | checkpoint_pt, CheckPointInfoArray |
| 50 | 第3年地区选择 | selected_region_ids |
| 72 | 第3年RMJ | checkpoint_pt, CheckPointInfoArray |
| 任意 | 试吃会 | checkpoint_pt (前后diff), LastTastingInfo |
| 任意 | 训练 | feeling_info (前后diff), trainings |
| 决赛 | URA决赛选择 | uraf_effect, active_effects |
