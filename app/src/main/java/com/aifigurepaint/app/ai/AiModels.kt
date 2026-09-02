package com.aifigurepaint.app.ai

enum class AiModelMode(
    val title: String,
    val description: String,
    val modelId: String?,
) {
    AUTO("자동 추천", "작업에 맞춰 Luna와 Terra 중 자동 선택", null),
    LUNA("Luna · 절약", "빠르고 저렴한 간단 작업용", "gpt-5.6-luna"),
    TERRA("Terra · 균형", "일반 조색과 사진 분석에 추천", "gpt-5.6-terra");

    companion object {
        fun fromStored(value: String?): AiModelMode = when (value) {
            "SOL" -> TERRA
            else -> entries.firstOrNull { it.name == value } ?: AUTO
        }
    }
}

enum class AiTaskType {
    PAINT_SCAN,
    SIMPLE_CHAT,
    COLOR_MIX,
    PHOTO_COLOR_MIX,
    PAINTING_ADVICE,
    RECIPE_ADJUST,
    TEST_PIECE_ADJUST,
    PARTS_COMPARE,
    PRODUCT_CODE_SEARCH,
    ORIGINAL_COLOR_MATCH,
}

data class AiModelSelection(
    val mode: AiModelMode,
    val taskType: AiTaskType,
    val modelId: String,
) {
    private val familyName: String
        get() = when (modelId) {
            AiModelRouter.LUNA_MODEL -> "Luna"
            else -> "Terra"
        }

    val resultLabel: String
        get() = if (mode == AiModelMode.AUTO) "자동 · $familyName" else "GPT-5.6 $familyName"
}

object AiModelRouter {
    const val LUNA_MODEL = "gpt-5.6-luna"
    const val TERRA_MODEL = "gpt-5.6-terra"

    fun resolve(
        taskType: AiTaskType,
        selectedMode: AiModelMode,
        highestQuality: Boolean = false,
    ): AiModelSelection {
        val modelId = selectedMode.modelId ?: when {
            highestQuality -> TERRA_MODEL
            taskType == AiTaskType.TEST_PIECE_ADJUST || taskType == AiTaskType.PARTS_COMPARE || taskType == AiTaskType.ORIGINAL_COLOR_MATCH -> TERRA_MODEL
            taskType == AiTaskType.PAINT_SCAN || taskType == AiTaskType.SIMPLE_CHAT || taskType == AiTaskType.PRODUCT_CODE_SEARCH -> LUNA_MODEL
            else -> TERRA_MODEL
        }
        return AiModelSelection(selectedMode, taskType, modelId)
    }
}
