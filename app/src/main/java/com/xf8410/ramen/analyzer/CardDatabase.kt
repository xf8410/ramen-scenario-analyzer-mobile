package com.xf8410.ramen.analyzer

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * 支援卡数据库 — 从 assets/cardDB.json 加载静态卡数据
 *
 * cardDB.json 来源：UmaAi/db/cardDB.json（从游戏 MDB support_card_data 导出）
 *
 * ⚠️ 命名陷阱：cardDB 的 cardType 与 MDB 的 support_card_type 不同！
 *   cardDB.cardType:       0=速,1=耐,2=力,3=根,4=智,5=友,6=团（训练类型）
 *   MDB.support_card_type: 1=普通,2=友人,3=团体（卡类别）
 *
 * cardValue 字段安全：只有 5 个字段全卡必有（bonus, filled, hintBonus, hintLevel, initialBonus），
 * 其余 12 个字段（youQing, ganJing, deYiLv, initialJiBan, hintProbIncrease, saiHou,
 * xunLian, wizVitalBonus, eventEffectUp, eventRecoveryAmountUp, failRateDrop, vitalCostDrop）
 * 全可选，必须用 getOptionalInt() 取值。
 */
class CardDatabase private constructor(private val cards: Map<Int, CardData>) {

    data class CardData(
        val cardId: Int,
        val cardName: String,
        val rarity: Int,          // 1=R, 2=SR, 3=SSR
        val cardType: Int,        // cardDB 训练类型: 0=速,1=耐,2=力,3=根,4=智,5=友,6=团
        val cardValues: List<CardValue>,
    )

    data class CardValue(
        val filled: Boolean,
        val bonus: IntArray,       // 6元组: 速耐力根智+技能pt
        val initialBonus: IntArray,
        val hintBonus: IntArray,
        val hintLevel: Int,
        // 可选字段（用安全取值）
        val youQing: Float,        // 友情加成
        val ganJing: Int,          // 干劲加成
        val deYiLv: Int,           // 得意率
        val initialJiBan: Int,     // 初始羁绊
        val hintProbIncrease: Int, // 灵感概率
        val saiHou: Int,           // 赛后加成
        val xunLian: Int,          // 训练加成
        val wizVitalBonus: Int,    // 智力体力加成
        val eventEffectUp: Int,
        val eventRecoveryAmountUp: Int,
        val failRateDrop: Int,
        val vitalCostDrop: Int,
    )

    /** 按突破等级获取 cardValue（0=未突, 4=满突） */
    fun getCardValue(cardId: Int, limitBreak: Int = 4): CardValue? {
        val card = cards[cardId] ?: return null
        val idx = limitBreak.coerceIn(0, 4)
        return card.cardValues.getOrNull(idx)
    }

    fun getCardName(cardId: Int): String? = cards[cardId]?.cardName

    fun getCardRarity(cardId: Int): Int = cards[cardId]?.rarity ?: 0

    /** cardDB 训练类型 → MDB support_card_type 映射 */
    fun getSupportCardType(cardId: Int): Int {
        val card = cards[cardId] ?: return 0
        return when (card.cardType) {
            5 -> 2   // 友人 → MDB type 2
            6 -> 3   // 团体 → MDB type 3
            else -> 1 // 速耐力根智 → MDB type 1 (普通)
        }
    }

    companion object {
        @Volatile
        private var instance: CardDatabase? = null

        fun getInstance(context: Context): CardDatabase? {
            if (instance != null) return instance
            synchronized(this) {
                if (instance != null) return instance
                instance = loadFromAssets(context)
                return instance
            }
        }

        private fun loadFromAssets(context: Context): CardDatabase? {
            return try {
                val json = context.assets.open("cardDB.json").bufferedReader().use { it.readText() }
                val root = JsonParser.parseString(json).asJsonObject
                val gson = Gson()
                val cards = mutableMapOf<Int, CardData>()
                for ((key, value) in root.entrySet()) {
                    val cardId = key.toIntOrNull() ?: continue
                    val cardObj = value.asJsonObject
                    val cardName = cardObj.get("cardName")?.asString ?: "?"
                    val rarity = cardObj.get("rarity")?.asInt ?: 0
                    val cardType = cardObj.get("cardType")?.asInt ?: 0
                    val cardValueArr = cardObj.getAsJsonArray("cardValue") ?: continue
                    val cardValues = cardValueArr.map { cv ->
                        val o = cv.asJsonObject
                        CardValue(
                            filled = o.get("filled")?.asBoolean ?: false,
                            bonus = readIntArray(o, "bonus", 6),
                            initialBonus = readIntArray(o, "initialBonus", 6),
                            hintBonus = readIntArray(o, "hintBonus", 6),
                            hintLevel = o.get("hintLevel")?.asInt ?: 0,
                            youQing = o.get("youQing")?.asFloat ?: 0f,
                            ganJing = o.get("ganJing")?.asInt ?: 0,
                            deYiLv = o.get("deYiLv")?.asInt ?: 0,
                            initialJiBan = o.get("initialJiBan")?.asInt ?: 0,
                            hintProbIncrease = o.get("hintProbIncrease")?.asInt ?: 0,
                            saiHou = o.get("saiHou")?.asInt ?: 0,
                            xunLian = o.get("xunLian")?.asInt ?: 0,
                            wizVitalBonus = o.get("wizVitalBonus")?.asInt ?: 0,
                            eventEffectUp = o.get("eventEffectUp")?.asInt ?: 0,
                            eventRecoveryAmountUp = o.get("eventRecoveryAmountUp")?.asInt ?: 0,
                            failRateDrop = o.get("failRateDrop")?.asInt ?: 0,
                            vitalCostDrop = o.get("vitalCostDrop")?.asInt ?: 0,
                        )
                    }
                    cards[cardId] = CardData(cardId, cardName, rarity, cardType, cardValues)
                }
                CardDatabase(cards)
            } catch (e: Exception) {
                null
            }
        }

        private fun readIntArray(obj: JsonObject, key: String, size: Int): IntArray {
            val arr = obj.getAsJsonArray(key) ?: return IntArray(size)
            return IntArray(size) { i -> arr.getOrNull(i)?.asInt ?: 0 }
        }
    }
}
