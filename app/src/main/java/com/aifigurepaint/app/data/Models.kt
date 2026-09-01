package com.aifigurepaint.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "paints")
data class PaintEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val brand: String,
    val series: String,
    val productCode: String?,
    val name: String,
    val koreanName: String,
    val colorValue: Int,
    val owned: Boolean = true,
    @ColumnInfo(defaultValue = "3") val stockLevel: Int = StockLevel.MOST,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    val lastUsedAt: Long? = null,
    val memo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    @ColumnInfo(defaultValue = "''") val modelName: String = "",
    val memo: String = "",
    @ColumnInfo(defaultValue = "0") val startDate: Long = 0,
    @ColumnInfo(defaultValue = "'PLANNED'") val status: String = ProjectStatus.PLANNED,
    @ColumnInfo(defaultValue = "'MECHANIC'") val projectType: String = ProjectType.MECHANIC,
    val photoUri: String? = null,
    val partsBaselinePhotoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "part_comparisons",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class PartComparisonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val comparisonDate: Long,
    val baselinePhotoUri: String,
    val currentPhotoUri: String,
    val changedCount: Int,
    val missingCount: Int,
    val movedCount: Int,
    val summary: String,
    val findings: String,
    val modelLabel: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "original_color_plans",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId")],
)
data class OriginalColorPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val identifiedName: String,
    val workTitle: String,
    val versionName: String,
    val referenceTitle: String,
    val referenceType: String,
    val referenceUrl: String,
    val official: Boolean,
    val partsJson: String,
    val modelLabel: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "project_recipe_refs",
    primaryKeys = ["projectId", "recipeId"],
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MixRecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("projectId"), Index("recipeId")],
)
data class ProjectRecipeCrossRef(
    val projectId: Long,
    val recipeId: Long,
    @ColumnInfo(defaultValue = "''") val label: String = "",
    @ColumnInfo(defaultValue = "0") val sortOrder: Int = 0,
)

@Entity(
    tableName = "photos",
    indices = [Index(value = ["ownerType", "ownerId"])],
)
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerType: String,
    val ownerId: Long,
    val uri: String,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "recipe_versions",
    foreignKeys = [
        ForeignKey(
            entity = MixRecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId")],
)
data class RecipeVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val versionNumber: Int,
    val label: String,
    val snapshotName: String,
    val snapshotColorValue: Int,
    val snapshotTotalMl: Double,
    val ingredientSnapshot: String,
    val aiGenerated: Boolean = false,
    val sourcePrompt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "test_results",
    foreignKeys = [
        ForeignKey(
            entity = MixRecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = RecipeVersionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeVersionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("recipeId"), Index("recipeVersionId")],
)
data class TestResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val recipeVersionId: Long,
    val testDate: Long,
    val evaluations: String = "",
    val memo: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "project_timeline_entries",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MixRecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("projectId"), Index("recipeId")],
)
data class ProjectTimelineEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long,
    val date: Long,
    val stage: String,
    val memo: String = "",
    val photoUri: String? = null,
    val recipeId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "mix_recipes",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("projectId")],
)
data class MixRecipeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val projectId: Long? = null,
    val name: String,
    val baseTotalMl: Double,
    val memo: String = "",
    val resultColorValue: Int,
    @ColumnInfo(defaultValue = "0") val favorite: Boolean = false,
    val photoUri: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "mix_recipe_items",
    foreignKeys = [
        ForeignKey(
            entity = MixRecipeEntity::class,
            parentColumns = ["id"],
            childColumns = ["recipeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PaintEntity::class,
            parentColumns = ["id"],
            childColumns = ["paintId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("recipeId"), Index("paintId")],
)
data class MixRecipeItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recipeId: Long,
    val paintId: Long,
    val baseAmountMl: Double,
    val sortOrder: Int,
)

data class RecipeItemRow(
    val id: Long,
    val recipeId: Long,
    val paintId: Long,
    val baseAmountMl: Double,
    val sortOrder: Int,
    val brand: String,
    val series: String,
    val productCode: String?,
    @ColumnInfo(name = "paintName") val paintName: String,
    val koreanName: String,
    val colorValue: Int,
)

data class RecipeCardRow(
    val id: Long,
    val name: String,
    val projectName: String?,
    val resultColorValue: Int,
    val components: String,
    val itemCount: Int,
    val baseTotalMl: Double,
    val updatedAt: Long,
)

data class ProjectPaintRow(
    val id: Long,
    val brand: String,
    val series: String,
    val productCode: String?,
    val name: String,
    val koreanName: String,
    val colorValue: Int,
)

object StockLevel {
    const val EMPTY = 0
    const val LOW = 1
    const val HALF = 2
    const val MOST = 3
    const val NEW = 4

    val entries = listOf(NEW, MOST, HALF, LOW, EMPTY)

    fun label(value: Int): String = when (value) {
        NEW -> "새 제품"
        MOST -> "많이 남음"
        HALF -> "절반"
        LOW -> "얼마 안 남음"
        else -> "소진"
    }

    fun percentLabel(value: Int): String = when (value) {
        NEW -> "약 100%"
        MOST -> "약 75%"
        HALF -> "약 50%"
        LOW -> "약 20%"
        else -> "0%"
    }
}

object ProjectStatus {
    const val PLANNED = "PLANNED"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val COMPLETED = "COMPLETED"
    val entries = listOf(PLANNED, IN_PROGRESS, COMPLETED)

    fun label(value: String): String = when (value) {
        IN_PROGRESS -> "진행 중"
        COMPLETED -> "완료"
        else -> "예정"
    }
}

object ProjectType {
    const val FIGURE = "FIGURE"
    const val MECHANIC = "MECHANIC"
    const val MILITARY = "MILITARY"
    const val AUTO = "AUTO"
    val entries = listOf(MECHANIC, MILITARY, AUTO)

    fun label(value: String): String = when (value) {
        MILITARY -> "밀리터리"
        AUTO -> "오토"
        else -> "메카닉"
    }

    fun badge(value: String): String = when (value) {
        MILITARY -> "Military"
        AUTO -> "Auto"
        else -> "Mechanic"
    }
}

object PhotoOwner {
    const val PROJECT = "PROJECT"
    const val RECIPE = "RECIPE"
    const val TEST_RESULT = "TEST_RESULT"
}

object TestEvaluation {
    const val MATCH = "MATCH"
    const val TOO_LIGHT = "TOO_LIGHT"
    const val TOO_DARK = "TOO_DARK"
    const val TOO_RED = "TOO_RED"
    const val TOO_YELLOW = "TOO_YELLOW"
    const val TOO_BLUE = "TOO_BLUE"
    const val TOO_PURPLE = "TOO_PURPLE"
    const val TOO_SATURATED = "TOO_SATURATED"
    const val TOO_DESATURATED = "TOO_DESATURATED"

    val entries = listOf(MATCH, TOO_LIGHT, TOO_DARK, TOO_RED, TOO_YELLOW, TOO_BLUE, TOO_PURPLE, TOO_SATURATED, TOO_DESATURATED)

    fun label(value: String): String = when (value) {
        MATCH -> "딱 맞음"
        TOO_LIGHT -> "너무 밝음"
        TOO_DARK -> "너무 어두움"
        TOO_RED -> "너무 붉음"
        TOO_YELLOW -> "너무 노랑"
        TOO_BLUE -> "너무 파랑"
        TOO_PURPLE -> "너무 보라"
        TOO_SATURATED -> "채도 높음"
        TOO_DESATURATED -> "채도 낮음"
        else -> value
    }
}

object ProjectStage {
    const val PREPARATION = "PREPARATION"
    const val SURFACE = "SURFACE"
    const val PRIMER = "PRIMER"
    const val BASE = "BASE"
    const val MIXING = "MIXING"
    const val SHADING = "SHADING"
    const val MASKING = "MASKING"
    const val DETAIL = "DETAIL"
    const val DECAL = "DECAL"
    const val FINISH = "FINISH"
    const val COMPLETE = "COMPLETE"

    val entries = listOf(PREPARATION, SURFACE, PRIMER, BASE, MIXING, SHADING, MASKING, DETAIL, DECAL, FINISH, COMPLETE)

    fun label(value: String): String = when (value) {
        SURFACE -> "표면 정리"
        PRIMER -> "서페이서"
        BASE -> "베이스"
        MIXING -> "조색"
        SHADING -> "명암"
        MASKING -> "마스킹"
        DETAIL -> "부분도색"
        DECAL -> "데칼"
        FINISH -> "마감"
        COMPLETE -> "완성"
        else -> "준비"
    }
}

data class SeedPaint(
    val brand: String,
    val series: String,
    val productCode: String?,
    val name: String,
    val koreanName: String = "",
    val colorValue: Int,
)

internal fun rgb(hex: Long): Int = (0xFF000000L or hex).toInt()

object SeedData {
    val paints = listOf(
        SeedPaint("Mr.Color", "Mr.Color", "C1", "White", "화이트", rgb(0xF4F4F1)),
        SeedPaint("Mr.Color", "Mr.Color", "C2", "Black", "블랙", rgb(0x161719)),
        SeedPaint("Mr.Color", "Mr.Color", "C4", "Yellow", "옐로우", rgb(0xF3CF28)),
        SeedPaint("Mr.Color", "Mr.Color", "C8", "Silver", "실버", rgb(0xBFC3C4)),
        SeedPaint("Mr.Color", "Mr.Color", "C13", "Neutral Gray", "뉴트럴 그레이", rgb(0x8A8B87)),
        SeedPaint("Mr.Color", "Mr.Color", "C33", "Flat Black", "플랫 블랙", rgb(0x1F2020)),
        SeedPaint("Mr.Color", "Mr.Color", "C36", "RLM74 Gray Green", "그레이 그린", rgb(0x4E5850)),
        SeedPaint("Mr.Color", "Mr.Color", "C39", "Dark Yellow", "다크 옐로우", rgb(0xA4934E)),
        SeedPaint("Mr.Color", "Mr.Color", "C41", "Red Brown", "레드 브라운", rgb(0x6D342B)),
        SeedPaint("Mr.Color", "Mr.Color", "C43", "Wood Brown", "우드 브라운", rgb(0x8C5D3D)),
        SeedPaint("Mr.Color", "Mr.Color", "C45", "Sail Color", "세일 컬러", rgb(0xE5C9A5)),
        SeedPaint("Mr.Color", "Mr.Color", "C50", "Clear Blue", "클리어 블루", rgb(0x2D65C4)),
        SeedPaint("Mr.Color", "Mr.Color", "C57", "Metallic Violet", "메탈릭 바이올렛", rgb(0x6D4C8A)),
        SeedPaint("Mr.Color", "Mr.Color", "C58", "Orange Yellow", "오렌지 옐로우", rgb(0xED9428)),
        SeedPaint("Mr.Color", "Mr.Color", "C61", "Burnt Iron", "번트 아이언", rgb(0x514B47)),
        SeedPaint("Mr.Color", "Mr.Color", "C69", "Grand Prix White", "그랑프리 화이트", rgb(0xF2F1E9)),
        SeedPaint("Mr.Color", "Mr.Color", "C71", "Midnight Blue", "미드나이트 블루", rgb(0x17213C)),
        SeedPaint("Mr.Color", "Mr.Color", "C76", "Metallic Blue", "메탈릭 블루", rgb(0x315B88)),
        SeedPaint("Mr.Color", "Mr.Color", "C92", "Semi-Gloss Black", "세미글로스 블랙", rgb(0x222426)),
        SeedPaint("Mr.Color", "Mr.Color", "C100", "Red", "레드", rgb(0xB71F2C)),
        SeedPaint("Mr.Color", "Mr.Color", "C104", "RLM76 Light Violet Gray", "", rgb(0xB4C1BD)),
        SeedPaint("Mr.Color", "Mr.Color", "C114", "RLM66 Black Gray", "", rgb(0x343A3C)),
        SeedPaint("Mr.Color", "Mr.Color", "C125", "Cowling Black", "카울링 블랙", rgb(0x20252C)),
        SeedPaint("Mr.Color", "Mr.Color", "C137", "Tire Black", "타이어 블랙", rgb(0x303333)),
        SeedPaint("Mr.Color", "Mr.Color", "C218", "Aluminum", "알루미늄", rgb(0xBDC2C2)),
        SeedPaint("Mr.Color", "Mr.Color", "C318", "Radome", "라돔", rgb(0xD8CBA5)),
        SeedPaint("Mr.Color", "Mr.Color", "C325", "Gray FS26440", "", rgb(0xA6A8A2)),
        SeedPaint("Mr.Color", "Mr.Color", "C326", "Blue FS15044", "", rgb(0x203C5A)),

        SeedPaint("Mr.Color", "Mr.Color GX", "GX2", "Ueno Black", "우이노 블랙", rgb(0x111213)),
        SeedPaint("Mr.Color", "Mr.Color GX", "GX106", "Clear Red", "클리어 레드", rgb(0xB3192C)),
        SeedPaint("Mr.Color", "Mr.Color GX", "GX112", "Super Clear III UV Cut", "", rgb(0xE8EEF0)),
        SeedPaint("Mr.Color", "Mr.Color GX", "GX113", "Super Clear III Semi-Gloss", "", rgb(0xDDE3E5)),
        SeedPaint("Mr.Color", "Mr.Color GX", "GX114", "Super Smooth Clear Flat", "", rgb(0xD7DDDE)),
        SeedPaint("Mr.Color", "Mr.Color GX", "GX201", "Metal Black", "메탈 블랙", rgb(0x373A3C)),
        SeedPaint("Mr.Color", "Mr.Color GX", "GX210", "Metallic Purple", "메탈릭 퍼플", rgb(0x684E80)),

        SeedPaint("Mr.Color", "Super Metallic 2", "SM201", "Super Fine Silver 2", "슈퍼 파인 실버 2", rgb(0xD2D4D4)),
        SeedPaint("Mr.Color", "Super Metallic 2", "SM204", "Super Stainless 2", "슈퍼 스테인리스 2", rgb(0xA5A9A8)),
        SeedPaint("Mr.Color", "Super Metallic 2", "SM205", "Super Titanium 2", "슈퍼 티타늄 2", rgb(0x8D9291)),
        SeedPaint("Mr.Color", "Super Metallic 2", "SM206", "Super Chrome Silver 2", "슈퍼 크롬 실버 2", rgb(0xE0E2E2)),

        SeedPaint("Mr.Color", "Gundam Color", "UG05", "MS Gray", "MS 그레이", rgb(0x777D80)),
        SeedPaint("Mr.Color", "Gundam Color", "UG09", "Zeon's MS Gray", "지온 MS 그레이", rgb(0x6C736D)),
        SeedPaint("Mr.Color", "Gundam Color", "UG12", "MS Sazabi Red", "MS 사자비 레드", rgb(0xA9212B)),
        SeedPaint("Mr.Color", "Gundam Color", "UG17", "Titans Blue", "티탄즈 블루", rgb(0x1E2B48)),

        SeedPaint("Mr.Color", "LASCIVUS", "CL101", "LASCIVUS Aura Blond", "블론드", rgb(0xE2BF7E)),
        SeedPaint("Mr.Color", "LASCIVUS", "CL106", "LASCIVUS Aura", "", rgb(0xE2C2B8)),

        SeedPaint("Gaia Notes", "Gaia Color", "045", "Clear Yellow", "클리어 옐로우", rgb(0xF0C324)),
        SeedPaint("Gaia Notes", "Gaia Color", "050", "Clear White", "클리어 화이트", rgb(0xF0F2EE)),
        SeedPaint("Gaia Notes", "Gaia Color", "124", "Star Bright Brass", "스타 브라이트 브라스", rgb(0xB89445)),
        SeedPaint("Gaia Notes", "Premium", null, "Premium Glass Pearl", "프리미엄 글라스 펄", rgb(0xDDD8D2)),

        SeedPaint("Finisher's", "Finisher's Color", "F006", "Foundation White", "파운데이션 화이트", rgb(0xF6F5EF)),
        SeedPaint("Finisher's", "Finisher's Color", null, "Silk Red", "실크 레드", rgb(0xB32135)),
    )
}
