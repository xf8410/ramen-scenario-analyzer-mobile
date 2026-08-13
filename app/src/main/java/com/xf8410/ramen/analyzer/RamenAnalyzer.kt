package com.xf8410.ramen.analyzer

/**
 * 拉面杯状态分析器
 *
 * 从 /summary 返回的 SummaryResponse 中提取拉面杯专用状态，
 * 转换为浮窗可显示的文本。
 *
 * 分析逻辑参考 URA-Plugins/OnsenScenarioAnalyzer Handler.cs 的结构：
 * - 素材统计
 * - 盛況度档位判定
 * - 地区效果
 * - ActiveEffect 分类
 * - URA 决赛状态
 *
 * 拉面杯机制数据来源：
 * - MDB: single_mode_14_* 表
 * - 运行时: hlpatch /summary 端点
 * - 证据: umamusume-scenario-mechanics/scenarios/14_ramen/
 */
object RamenAnalyzer {

    // ===== 素材类型 =====
    // FeelingId: 1=麺(Noodle), 2=湯(Soup), 3=配(Topping)
    private val SOZAI_NAMES = arrayOf("", "麺", "湯", "配")

    // ===== 盛況度 Pt 档位 [MDB check_point_pt_effect] =====
    private val PT_TIERS = arrayOf(
        Triple(0, 249, "0档"),
        Triple(250, 499, "1档"),
        Triple(500, 999, "2档"),
        Triple(1000, 1499, "3档"),
        Triple(1500, 1999, "4档"),
        Triple(2000, 2499, "5档"),
        Triple(2500, 2999, "6档"),
        Triple(3000, 3499, "7档"),
        Triple(3500, 3999, "8档"),
        Triple(4000, 4999, "9档"),
        Triple(5000, 99999, "10档"),
    )
    private val PT_TIER_TRAIN_PCT = intArrayOf(0, 3, 5, 8, 10, 12, 14, 16, 18, 20, 20)
    private val PT_TIER_TOKUYI = intArrayOf(50, 55, 60, 63, 65, 68, 70, 73, 75, 78, 80)
    private val PT_TIER_HINT_PCT = intArrayOf(0, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120)

    // ===== effect_category [证据: effect_category_and_support_type_gate_formula.md] =====
    // 0=Basic, 1=Region, 2=URAF Common, 3=URAF Unique
    private val EFFECT_CATEGORY_NAMES = arrayOf("Basic", "Region", "URAF共通", "URAF固有")

    // ===== 地区名称 =====
    private val REGION_NAMES = mapOf(
        1 to "札幌", 2 to "函館", 3 to "新潟", 4 to "福島", 5 to "東京",
        6 to "中山", 7 to "中京", 8 to "京都", 9 to "阪神", 10 to "小倉",
        11 to "札幌", 12 to "函館", 13 to "新潟", 14 to "福島", 15 to "東京",
        16 to "中山", 17 to "中京", 18 to "京都", 19 to "阪神", 20 to "小倉",
    )

    /**
     * 分析完整的拉面杯状态
     */
    fun analyze(summary: SummaryResponse): AnalysisResult {
        val ramen = summary.ramen
        val chara = summary.charaInfo

        // 素材统计
        val sozaiCounts = IntArray(4) // index 1=麺, 2=湯, 3=配
        val sozaiTotal: Int
        if (ramen?.feelingInfo != null) {
            for (item in ramen.feelingInfo) {
                if (item.feelingId in 1..3) {
                    sozaiCounts[item.feelingId]++
                }
            }
            sozaiTotal = ramen.feelingInfo.size
        } else {
            sozaiTotal = 0
        }

        // 盛況度档位
        val pt = ramen?.checkpointPt ?: 0
        val tier = ptTier(pt)
        val trainPct = PT_TIER_TRAIN_PCT[tier]
        val tokuyi = PT_TIER_TOKUYI[tier]
        val hintPct = PT_TIER_HINT_PCT[tier]

        // 隠し味
        val kakushimi = ramen?.specialFeelingNum ?: 0

        // 地区
        val regions = ramen?.selectedRegionIds ?: emptyList()
        val regionNames = regions.mapNotNull { REGION_NAMES[it] }

        // ActiveEffect 分类
        val effectsByCategory = mutableMapOf<Int, MutableList<ActiveEffect>>()
        ramen?.activeEffects?.forEach { eff ->
            effectsByCategory.getOrPut(eff.effectCategory) { mutableListOf() }.add(eff)
        }

        // URA 决赛
        val uraf = ramen?.urafEffect

        return AnalysisResult(
            turn = summary.turnNum,
            speed = chara?.speed ?: 0,
            stamina = chara?.stamina ?: 0,
            power = chara?.power ?: 0,
            guts = chara?.guts ?: 0,
            wiz = chara?.wiz ?: 0,
            vital = chara?.vital ?: 0,
            maxVital = chara?.maxVital ?: 0,
            motivation = chara?.motivation ?: 3,
            skillPoint = chara?.skillPoint ?: 0,
            sozaiMen = sozaiCounts[1],
            sozaiSoup = sozaiCounts[2],
            sozaiTopping = sozaiCounts[3],
            sozaiTotal = sozaiTotal,
            kakushimi = kakushimi,
            checkpointPt = pt,
            expectedCheckpointPt = ramen?.expectedCheckpointPt ?: 0,
            ptTier = tier,
            ptTierName = PT_TIERS[tier].third,
            trainPct = trainPct,
            tokuyi = tokuyi,
            hintPct = hintPct,
            regionNames = regionNames,
            activeEffects = effectsByCategory,
            urafType = uraf?.type ?: 0,
            urafState = uraf?.state ?: 0,
            recommendType = ramen?.recommendType ?: 0,
        )
    }

    private fun ptTier(pt: Int): Int {
        for (i in PT_TIERS.indices) {
            if (pt >= PT_TIERS[i].first && pt <= PT_TIERS[i].second) return i
        }
        return 0
    }

    data class AnalysisResult(
        val turn: Int,
        val speed: Int, val stamina: Int, val power: Int, val guts: Int, val wiz: Int,
        val vital: Int, val maxVital: Int,
        val motivation: Int,
        val skillPoint: Int,
        val sozaiMen: Int, val sozaiSoup: Int, val sozaiTopping: Int, val sozaiTotal: Int,
        val kakushimi: Int,
        val checkpointPt: Int,
        val expectedCheckpointPt: Int,
        val ptTier: Int,
        val ptTierName: String,
        val trainPct: Int,
        val tokuyi: Int,
        val hintPct: Int,
        val regionNames: List<String>,
        val activeEffects: Map<Int, List<ActiveEffect>>,
        val urafType: Int,
        val urafState: Int,
        val recommendType: Int,
    ) {
        /** 转为浮窗多行文本 */
        fun toDisplayText(): String {
            val sb = StringBuilder()
            sb.appendLine("===== 拉面杯分析 =====")

            // 五维
            sb.appendLine("五维: 速${speed} 耐${stamina} 力${power} 根${guts} 智${wiz}")

            // 体力/干劲
            val motivName = when (motivation) {
                5 -> "絶好調"; 4 -> "好調"; 3 -> "普通"; 2 -> "不調"; 1 -> "絶不調"; else -> "?"
            }
            sb.appendLine("体力: $vital/$maxVital  干劲: $motivName")

            // 素材
            sb.appendLine("素材: 麺$sozaiMen 湯$sozaiSoup 配$sozaiTopping (計${sozaiTotal}/10)  隠し味:$kakushimi/4")

            // 盛況度
            sb.appendLine("盛況度: ${checkpointPt}pt [${ptTierName}] 効果${trainPct}% 得意率${tokuyi} 灵感${hintPct}%")
            if (expectedCheckpointPt > 0 && expectedCheckpointPt != checkpointPt) {
                sb.appendLine("  予想: ${expectedCheckpointPt}pt")
            }

            // 地区
            if (regionNames.isNotEmpty()) {
                sb.appendLine("地区: ${regionNames.joinToString("/")}")
            }

            // ActiveEffect
            if (activeEffects.isNotEmpty()) {
                sb.appendLine("効果:")
                for ((cat, effects) in activeEffects) {
                    val catName = if (cat in EFFECT_CATEGORY_NAMES.indices) EFFECT_CATEGORY_NAMES[cat] else "cat$cat"
                    val summary = effects.joinToString(", ") { "id${it.effectId}:${it.effectValue}" }
                    sb.appendLine("  [$catName] $summary")
                }
            }

            // URA 决赛
            if (urafType != 0 || urafState != 0) {
                sb.appendLine("URAF: type=$urafType state=$urafState")
            }

            return sb.toString().trimEnd()
        }
    }
}
