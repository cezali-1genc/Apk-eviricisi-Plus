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

@Entity(tableName = "translation_cache")
data class TranslationCacheEntity(
    @PrimaryKey val cacheKey: String,
    val sourceLang: String,
    val targetLang: String,
    val sourceText: String,
    val translatedText: String,
    val engineTag: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "job_history")
data class JobHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val apkFileName: String,
    val packageName: String,
    val sourceLang: String,
    val targetLang: String,
    val translatedCount: Int,
    val inputSha256: String,
    val outputSha256: String,
    val savedLocation: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface TranslationCacheDao {
    @Query("SELECT * FROM translation_cache WHERE cacheKey = :cacheKey LIMIT 1")
    suspend fun getByKey(cacheKey: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: TranslationCacheEntity)

    @Query("SELECT COUNT(*) FROM translation_cache")
    fun observeCacheCount(): Flow<Int>

    @Query("DELETE FROM translation_cache")
    suspend fun clearAll()
}

@Dao
interface JobHistoryDao {
    @Query("SELECT * FROM job_history ORDER BY timestamp DESC LIMIT 30")
    fun observeRecentJobs(): Flow<List<JobHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJob(job: JobHistoryEntity)

    @Query("DELETE FROM job_history")
    suspend fun clearHistory()
}

@Database(
    entities = [TranslationCacheEntity::class, JobHistoryEntity::class],
    version = 1,
    exportSchema = false
)
abstract class TranslationCacheDatabase : RoomDatabase() {
    abstract fun translationCacheDao(): TranslationCacheDao
    abstract fun jobHistoryDao(): JobHistoryDao

    companion object {
        @Volatile
        private var INSTANCE: TranslationCacheDatabase? = null

        fun getInstance(context: Context): TranslationCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TranslationCacheDatabase::class.java,
                    "apk_translator_pro_cache.db"
                ).fallbackToDestructiveMigration(dropAllTables = true)
                    .build().also { INSTANCE = it }
            }
        }
    }
}
