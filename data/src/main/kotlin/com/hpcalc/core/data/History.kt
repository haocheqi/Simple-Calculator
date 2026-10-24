package com.hpcalc.core.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 计算历史。`mode` 存 [com.hpcalc.core.model.CalculatorMode] 的名字，便于按模式筛选。
 *
 * 阶段 3 会升到 version 2：新增 `pinned` 列与 `(mode, createdAt)` 索引，并配套真实迁移。
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val expression: String,
    val result: String,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT 100")
    fun observe(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(item: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()
}

@Database(entities = [HistoryEntity::class], version = 1, exportSchema = false)
abstract class CalculatorDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): CalculatorDatabase =
            Room.databaseBuilder(context, CalculatorDatabase::class.java, "calculator.db").build()
    }
}