package com.example.apktranslatorpro.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "translation_projects")
data class TranslationProjectEntity(
    @PrimaryKey val projectId: String,
    val projectName: String,
    val packageName: String,
    val apkFileName: String,
    val sourceApkUri: String,
    val sourceSha256: String,
    val sourceLangCode: String,
    val targetLangCode: String,
    val profileName: String = "Standart Uygulama",
    val serializedResourcesJson: String,
    val entryCount: Int,
    val translatedCount: Int,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "user_glossary")
data class UserGlossaryEntity(
    @PrimaryKey val sourceTerm: String,
    val preferredTranslation: String,
    val categoryNote: String = "Özel Sözlük",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM translation_projects ORDER BY updatedAtMillis DESC")
    fun observeProjects(): Flow<List<TranslationProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: TranslationProjectEntity)

    @Query("SELECT * FROM translation_projects WHERE projectId = :projectId LIMIT 1")
    suspend fun getProject(projectId: String): TranslationProjectEntity?

    @Query("DELETE FROM translation_projects WHERE projectId = :projectId")
    suspend fun deleteProject(projectId: String)

    @Query("SELECT * FROM user_glossary ORDER BY sourceTerm ASC")
    fun observeGlossary(): Flow<List<UserGlossaryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGlossary(entry: UserGlossaryEntity)

    @Query("DELETE FROM user_glossary WHERE sourceTerm = :sourceTerm")
    suspend fun deleteGlossary(sourceTerm: String)
}

@Database(
    entities = [TranslationProjectEntity::class, UserGlossaryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ProjectDatabase : RoomDatabase() {
    abstract fun projectDao(): ProjectDao

    companion object {
        @Volatile
        private var INSTANCE: ProjectDatabase? = null

        fun getInstance(context: Context): ProjectDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ProjectDatabase::class.java,
                    "apk_projects_and_glossary.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}

class ProjectRepository(private val dao: ProjectDao) {
    val projectsFlow: Flow<List<TranslationProjectEntity>> = dao.observeProjects()
    val glossaryFlow: Flow<List<UserGlossaryEntity>> = dao.observeGlossary()

    suspend fun saveProject(project: TranslationProjectEntity) = dao.upsertProject(project)
    suspend fun getProject(id: String): TranslationProjectEntity? = dao.getProject(id)
    suspend fun deleteProject(id: String) = dao.deleteProject(id)

    suspend fun saveGlossaryTerm(source: String, target: String, note: String = "Özel Sözlük") {
        if (source.isNotBlank() && target.isNotBlank()) {
            dao.upsertGlossary(UserGlossaryEntity(source.trim(), target.trim(), note.trim()))
        }
    }

    suspend fun deleteGlossaryTerm(source: String) = dao.deleteGlossary(source)
}
