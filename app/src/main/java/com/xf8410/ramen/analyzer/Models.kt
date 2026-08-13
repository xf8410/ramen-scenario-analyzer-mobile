package com.xf8410.ramen.analyzer

import com.google.gson.annotations.SerializedName

/**
 * hlpatch /summary 端点返回的 JSON 数据模型
 * 只包含拉面杯分析需要的字段
 */
data class SummaryResponse(
    @SerializedName("scenario") val scenario: String? = null,
    @SerializedName("raw_total_turn_num") val turnNum: Int = 0,
    @SerializedName("chara_info") val charaInfo: CharaInfo? = null,
    @SerializedName("ramen") val ramen: RamenData? = null,
    @SerializedName("trainings") val trainings: List<TrainingInfo>? = null,
)

data class CharaInfo(
    @SerializedName("speed") val speed: Int = 0,
    @SerializedName("stamina") val stamina: Int = 0,
    @SerializedName("power") val power: Int = 0,
    @SerializedName("guts") val guts: Int = 0,
    @SerializedName("wiz") val wiz: Int = 0,
    @SerializedName("vital") val vital: Int = 0,
    @SerializedName("max_vital") val maxVital: Int = 0,
    @SerializedName("motivation") val motivation: Int = 0,
    @SerializedName("skill_point") val skillPoint: Int = 0,
)

data class RamenData(
    @SerializedName("ramen_values") val values: Map<String, Int>? = null,
    @SerializedName("feeling_info") val feelingInfo: List<FeelingItem>? = null,
    @SerializedName("feeling_turn_info") val feelingTurnInfo: List<FeelingTurnItem>? = null,
    @SerializedName("feeling_reduce_turn_info") val feelingReduceTurnInfo: List<FeelingReduceItem>? = null,
    @SerializedName("command_feeling_info") val commandFeelingInfo: List<CommandFeelingItem>? = null,
    @SerializedName("active_effects") val activeEffects: List<ActiveEffect>? = null,
    @SerializedName("uraf_effect") val urafEffect: UrafEffect? = null,
    @SerializedName("selected_region_ids") val selectedRegionIds: List<Int>? = null,
    @SerializedName("all_selected_region_ids") val allSelectedRegionIds: List<Int>? = null,
) {
    val checkpointPt: Int get() = values?.get("CheckPointPt") ?: 0
    val expectedCheckpointPt: Int get() = values?.get("ExpectedCheckPointPt") ?: 0
    val specialFeelingNum: Int get() = values?.get("SpecialFeelingNum") ?: 0
    val recommendType: Int get() = values?.get("RecommendType") ?: 0
}

data class FeelingItem(
    @SerializedName("feeling_id") val feelingId: Int = 0,
    @SerializedName("remaining") val remaining: Int = 0,
)

data class FeelingTurnItem(
    @SerializedName("feeling_id") val feelingId: Int = 0,
    @SerializedName("remaining") val remaining: Int = 0,
)

data class FeelingReduceItem(
    @SerializedName("feeling_id") val feelingId: Int = 0,
    @SerializedName("remaining") val remaining: Int = 0,
)

data class CommandFeelingItem(
    @SerializedName("CommandType") val commandType: Int = 0,
    @SerializedName("CommandId") val commandId: Int = 0,
    @SerializedName("FeelingId") val feelingId: Int = 0,
)

data class ActiveEffect(
    @SerializedName("EffectCategory") val effectCategory: Int = 0,
    @SerializedName("EffectId") val effectId: Int = 0,
    @SerializedName("EffectValue") val effectValue: Int = 0,
)

data class UrafEffect(
    @SerializedName("UrafEffectType") val type: Int = 0,
    @SerializedName("UrafEffectState") val state: Int = 0,
)

data class TrainingInfo(
    @SerializedName("train_type") val trainType: Int = 0,
    @SerializedName("speed") val speed: Int = 0,
    @SerializedName("stamina") val stamina: Int = 0,
    @SerializedName("power") val power: Int = 0,
    @SerializedName("guts") val guts: Int = 0,
    @SerializedName("wiz") val wiz: Int = 0,
    @SerializedName("is_enable") val isEnable: Int = 0,
)
