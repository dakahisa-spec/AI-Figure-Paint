package com.aifigurepaint.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface PaintDao {
    @Query("SELECT * FROM paints ORDER BY favorite DESC, owned DESC, COALESCE(lastUsedAt, 0) DESC, brand COLLATE NOCASE, productCode COLLATE NOCASE, name COLLATE NOCASE")
    fun observeAll(): Flow<List<PaintEntity>>

    @Insert suspend fun insert(paint: PaintEntity): Long
    @Update suspend fun update(paint: PaintEntity)
    @Delete suspend fun delete(paint: PaintEntity)
    @Query("UPDATE paints SET lastUsedAt = :usedAt, updatedAt = :usedAt WHERE id IN (:paintIds)")
    suspend fun markUsed(paintIds: List<Long>, usedAt: Long)
}

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ProjectEntity>>

    @Insert suspend fun insert(project: ProjectEntity): Long
    @Update suspend fun update(project: ProjectEntity)
    @Delete suspend fun delete(project: ProjectEntity)
}

@Dao
interface RecipeDao {
    @Query("SELECT * FROM mix_recipes ORDER BY favorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<MixRecipeEntity>>

    @Query(
        """
        SELECT r.id, r.name,
               COALESCE(NULLIF((
                   SELECT group_concat(prj.name, ' · ')
                   FROM project_recipe_refs ref
                   JOIN projects prj ON prj.id = ref.projectId
                   WHERE ref.recipeId = r.id
               ), ''), p.name) AS projectName,
               r.resultColorValue, r.baseTotalMl, r.updatedAt,
               CAST((SELECT COUNT(*) FROM mix_recipe_items ci WHERE ci.recipeId = r.id) AS INTEGER) AS itemCount,
               COALESCE((
                   SELECT group_concat(
                       COALESCE(NULLIF(pi.productCode, ''), pi.name) || ' ' ||
                       printf('%.2f ml', ri.baseAmountMl),
                       ' · '
                   )
                   FROM mix_recipe_items ri
                   JOIN paints pi ON pi.id = ri.paintId
                   WHERE ri.recipeId = r.id
               ), '') AS components
        FROM mix_recipes r
        LEFT JOIN projects p ON p.id = r.projectId
        ORDER BY r.updatedAt DESC
        """
    )
    fun observeCards(): Flow<List<RecipeCardRow>>

    @Query(
        """
        SELECT r.id, r.name,
               COALESCE(NULLIF((
                   SELECT group_concat(prj.name, ' · ')
                   FROM project_recipe_refs ref2
                   JOIN projects prj ON prj.id = ref2.projectId
                   WHERE ref2.recipeId = r.id
               ), ''), legacy.name) AS projectName,
               r.resultColorValue, r.baseTotalMl, r.updatedAt,
               CAST((SELECT COUNT(*) FROM mix_recipe_items ci WHERE ci.recipeId = r.id) AS INTEGER) AS itemCount,
               COALESCE((
                   SELECT group_concat(
                       COALESCE(NULLIF(pi.productCode, ''), pi.name) || ' ' ||
                       printf('%.2f ml', ri.baseAmountMl),
                       ' · '
                   )
                   FROM mix_recipe_items ri
                   JOIN paints pi ON pi.id = ri.paintId
                   WHERE ri.recipeId = r.id
               ), '') AS components
        FROM mix_recipes r
        LEFT JOIN projects legacy ON legacy.id = r.projectId
        WHERE EXISTS (
            SELECT 1 FROM project_recipe_refs ref
            WHERE ref.projectId = :projectId AND ref.recipeId = r.id
        ) OR r.projectId = :projectId
        ORDER BY r.updatedAt DESC
        """
    )
    fun observeCardsForProject(projectId: Long): Flow<List<RecipeCardRow>>

    @Query(
        """
        SELECT DISTINCT p.id, p.brand, p.series, p.productCode, p.name, p.koreanName, p.colorValue
        FROM paints p
        JOIN mix_recipe_items item ON item.paintId = p.id
        JOIN mix_recipes recipe ON recipe.id = item.recipeId
        LEFT JOIN project_recipe_refs ref ON ref.recipeId = recipe.id
        WHERE ref.projectId = :projectId OR recipe.projectId = :projectId
        ORDER BY p.brand COLLATE NOCASE, p.productCode COLLATE NOCASE, p.name COLLATE NOCASE
        """
    )
    fun observePaintsForProject(projectId: Long): Flow<List<ProjectPaintRow>>

    @Query(
        """
        SELECT DISTINCT r.id, r.name,
               COALESCE(NULLIF((
                   SELECT group_concat(prj.name, ' · ')
                   FROM project_recipe_refs ref
                   JOIN projects prj ON prj.id = ref.projectId
                   WHERE ref.recipeId = r.id
               ), ''), legacy.name) AS projectName,
               r.resultColorValue, r.baseTotalMl, r.updatedAt,
               CAST((SELECT COUNT(*) FROM mix_recipe_items ci WHERE ci.recipeId = r.id) AS INTEGER) AS itemCount,
               COALESCE((
                   SELECT group_concat(COALESCE(NULLIF(pi.productCode, ''), pi.name), ' · ')
                   FROM mix_recipe_items ri
                   JOIN paints pi ON pi.id = ri.paintId
                   WHERE ri.recipeId = r.id
               ), '') AS components
        FROM mix_recipes r
        JOIN mix_recipe_items item ON item.recipeId = r.id
        LEFT JOIN projects legacy ON legacy.id = r.projectId
        WHERE item.paintId = :paintId
        ORDER BY r.updatedAt DESC
        """
    )
    fun observeCardsForPaint(paintId: Long): Flow<List<RecipeCardRow>>

    @Query("SELECT recipeId FROM project_recipe_refs WHERE projectId = :projectId ORDER BY sortOrder, recipeId")
    fun observeRecipeIdsForProject(projectId: Long): Flow<List<Long>>

    @Query(
        """
        SELECT ri.id, ri.recipeId, ri.paintId, ri.baseAmountMl, ri.sortOrder,
               p.brand, p.series, p.productCode, p.name AS paintName,
               p.koreanName, p.colorValue
        FROM mix_recipe_items ri
        JOIN paints p ON p.id = ri.paintId
        WHERE ri.recipeId = :recipeId
        ORDER BY ri.sortOrder
        """
    )
    fun observeItems(recipeId: Long): Flow<List<RecipeItemRow>>

    @Insert suspend fun insertRecipe(recipe: MixRecipeEntity): Long
    @Update suspend fun updateRecipe(recipe: MixRecipeEntity)
    @Delete suspend fun deleteRecipe(recipe: MixRecipeEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertItems(items: List<MixRecipeItemEntity>)
    @Query("DELETE FROM mix_recipe_items WHERE recipeId = :recipeId")
    suspend fun deleteItems(recipeId: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjectRefs(refs: List<ProjectRecipeCrossRef>)
    @Query("DELETE FROM project_recipe_refs WHERE projectId = :projectId")
    suspend fun deleteProjectRefs(projectId: Long)
    @Query("DELETE FROM project_recipe_refs WHERE recipeId = :recipeId")
    suspend fun deleteRecipeRefs(recipeId: Long)
    @Query("UPDATE mix_recipes SET projectId = NULL WHERE projectId = :projectId")
    suspend fun clearLegacyProjectLinks(projectId: Long)
}

@Dao
interface PhotoDao {
    @Query("SELECT * FROM photos WHERE ownerType = :ownerType AND ownerId = :ownerId ORDER BY sortOrder, id")
    fun observe(ownerType: String, ownerId: Long): Flow<List<PhotoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Query("DELETE FROM photos WHERE ownerType = :ownerType AND ownerId = :ownerId")
    suspend fun deleteForOwner(ownerType: String, ownerId: Long)
}

@Dao
interface VersionDao {
    @Query("SELECT * FROM recipe_versions WHERE recipeId = :recipeId ORDER BY versionNumber DESC, createdAt DESC")
    fun observeForRecipe(recipeId: Long): Flow<List<RecipeVersionEntity>>

    @Query("SELECT COALESCE(MAX(versionNumber), 0) + 1 FROM recipe_versions WHERE recipeId = :recipeId")
    suspend fun nextVersionNumber(recipeId: Long): Int

    @Insert suspend fun insert(version: RecipeVersionEntity): Long
}

@Dao
interface TimelineDao {
    @Query("SELECT * FROM project_timeline_entries WHERE projectId = :projectId ORDER BY date DESC, createdAt DESC")
    fun observeForProject(projectId: Long): Flow<List<ProjectTimelineEntryEntity>>

    @Insert suspend fun insert(entry: ProjectTimelineEntryEntity): Long
    @Delete suspend fun delete(entry: ProjectTimelineEntryEntity)
}

@Database(
    entities = [
        PaintEntity::class,
        ProjectEntity::class,
        MixRecipeEntity::class,
        MixRecipeItemEntity::class,
        ProjectRecipeCrossRef::class,
        PhotoEntity::class,
        RecipeVersionEntity::class,
        ProjectTimelineEntryEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun paintDao(): PaintDao
    abstract fun projectDao(): ProjectDao
    abstract fun recipeDao(): RecipeDao
    abstract fun photoDao(): PhotoDao
    abstract fun versionDao(): VersionDao
    abstract fun timelineDao(): TimelineDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "ai_figure_paint.db",
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    val now = System.currentTimeMillis()
                    SeedData.paints.forEach { paint ->
                        db.execSQL(
                            """
                            INSERT INTO paints
                            (brand, series, productCode, name, koreanName, colorValue, owned, memo, createdAt, updatedAt)
                            VALUES (?, ?, ?, ?, ?, ?, 1, '', ?, ?)
                            """.trimIndent(),
                            arrayOf(
                                paint.brand,
                                paint.series,
                                paint.productCode,
                                paint.name,
                                paint.koreanName,
                                paint.colorValue,
                                now,
                                now,
                            ),
                        )
                    }
                }
            }).build().also { instance = it }
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE paints ADD COLUMN stockLevel INTEGER NOT NULL DEFAULT 3")
                db.execSQL("ALTER TABLE paints ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE paints ADD COLUMN lastUsedAt INTEGER")
                db.execSQL("UPDATE paints SET stockLevel = CASE WHEN owned = 1 THEN 3 ELSE 0 END")

                db.execSQL("ALTER TABLE projects ADD COLUMN modelName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE projects ADD COLUMN startDate INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE projects ADD COLUMN status TEXT NOT NULL DEFAULT 'PLANNED'")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS project_recipe_refs (
                        projectId INTEGER NOT NULL,
                        recipeId INTEGER NOT NULL,
                        label TEXT NOT NULL DEFAULT '',
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(projectId, recipeId),
                        FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(recipeId) REFERENCES mix_recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_project_recipe_refs_projectId ON project_recipe_refs(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_project_recipe_refs_recipeId ON project_recipe_refs(recipeId)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO project_recipe_refs(projectId, recipeId, label, sortOrder)
                    SELECT projectId, id, '', 0 FROM mix_recipes WHERE projectId IS NOT NULL
                    """.trimIndent(),
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS photos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        ownerType TEXT NOT NULL,
                        ownerId INTEGER NOT NULL,
                        uri TEXT NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_photos_ownerType_ownerId ON photos(ownerType, ownerId)")
                db.execSQL(
                    """
                    INSERT INTO photos(ownerType, ownerId, uri, sortOrder, createdAt)
                    SELECT 'PROJECT', id, photoUri, 0, createdAt FROM projects WHERE photoUri IS NOT NULL
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    INSERT INTO photos(ownerType, ownerId, uri, sortOrder, createdAt)
                    SELECT 'RECIPE', id, photoUri, 0, createdAt FROM mix_recipes WHERE photoUri IS NOT NULL
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE mix_recipes ADD COLUMN favorite INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recipe_versions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        recipeId INTEGER NOT NULL,
                        versionNumber INTEGER NOT NULL,
                        label TEXT NOT NULL,
                        snapshotName TEXT NOT NULL,
                        snapshotColorValue INTEGER NOT NULL,
                        snapshotTotalMl REAL NOT NULL,
                        ingredientSnapshot TEXT NOT NULL,
                        aiGenerated INTEGER NOT NULL,
                        sourcePrompt TEXT NOT NULL,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(recipeId) REFERENCES mix_recipes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_recipe_versions_recipeId ON recipe_versions(recipeId)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS project_timeline_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        projectId INTEGER NOT NULL,
                        date INTEGER NOT NULL,
                        stage TEXT NOT NULL,
                        memo TEXT NOT NULL,
                        photoUri TEXT,
                        recipeId INTEGER,
                        createdAt INTEGER NOT NULL,
                        FOREIGN KEY(projectId) REFERENCES projects(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(recipeId) REFERENCES mix_recipes(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_project_timeline_entries_projectId ON project_timeline_entries(projectId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_project_timeline_entries_recipeId ON project_timeline_entries(recipeId)")
            }
        }
    }
}
