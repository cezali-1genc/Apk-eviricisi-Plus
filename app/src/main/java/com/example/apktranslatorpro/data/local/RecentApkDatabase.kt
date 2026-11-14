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

@Entity(tableName = "recent_apks")
data class RecentApkEntity(
    @PrimaryKey val sha256Hex: String,
    val fileName: String,
    val originalUriOrPath: String,
    val packageName: String,
    val appLabel: String,
    val fileSizeBytes: Long,
    val outputPath: String? = null,
    val lastAction: String = "İncelendi",
    val modifiedAtMillis: Long = System.currentTimeMillis()
)

@Dao
interface RecentApkDao {
    @Query("SELECT * FROM recent_apks ORDER BY modifiedAtMillis DESC LIMIT 50")
    fun observeRecentApks(): Flow<List<RecentApkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentApk(entity: RecentApkEntity)

    @Query("DELETE FROM recent_apks WHERE sha256Hex = :sha256Hex")
    suspend fun deleteBySha256(sha256Hex: String)

    @Query("DELETE FROM recent_apks")
    suspend fun clearAll()
}

@Database(
    entities = [RecentApkEntity::class],
    version = 1,
    exportSchema = false
)
abstract class RecentApkDatabase : RoomDatabase() {
    abstract fun recentApkDao(): RecentApkDao

    companion object {
        @Volatile
        private var INSTANCE: RecentApkDatabase? = null

        fun getInstance(context: Context): RecentApkDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    RecentApkDatabase::class.java,
                    "recent_apk_history.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class RecentApkRepository(private val dao: RecentApkDao) {
    val recentApksFlow: Flow<List<RecentApkEntity>> = dao.observeRecentApks()

    suspend fun recordApk(entity: RecentApkEntity) = dao.upsertRecentApk(entity)

    suspend fun deleteApk(sha256Hex: String) = dao.deleteBySha256(sha256Hex)

    suspend fun clearAll() = dao.clearAll()
}
