package com.hpcalc.core.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * 计算历史。`mode` 存 [com.hpcalc.core.model.CalculatorMode] 的名字，便于按模式筛选。
 *
 * [pinned] 是 v2 新增的列：置顶的条目永远排在前面，删除历史时也不会被「清空」误伤
 * 以外的逻辑影响（清空仍然全清，这是明确的用户意图）。
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val expression: String,
    val result: String,
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * 必须显式声明 `defaultValue`：这样「新建表」的 DDL 与 [CalculatorDatabase.MIGRATION_1_2]
     * 迁移出来的 DDL 完全一致，Room 的升级校验才不会因为默认值差异抛异常。
     */
    @ColumnInfo(defaultValue = "0") val pinned: Boolean = false
)

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY pinned DESC, createdAt DESC LIMIT 100")
    fun observe(): Flow<List<HistoryEntity>>

    @Insert
    suspend fun insert(item: HistoryEntity)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE history SET pinned = :pinned WHERE id = :id")
    suspend fun setPinned(id: Long, pinned: Boolean)
}

@Database(entities = [HistoryEntity::class], version = 2, exportSchema = false)
abstract class CalculatorDatabase : RoomDatabase() {
    abstract fun historyDao(): HistoryDao

    companion object {
        /**
         * v1 → v2：给 `history` 补上 `pinned` 列。
         *
         * 刻意手写迁移而不是用 `fallbackToDestructiveMigration`：后者会直接删表重建，
         * 用户升级一次攒下的历史就没了。带默认值的 `NOT NULL` 列可以安全地加到非空表上，
         * 老行自动成为「未置顶」。
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE history ADD COLUMN pinned INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun create(context: Context): CalculatorDatabase =
            Room.databaseBuilder(context, CalculatorDatabase::class.java, "calculator.db")
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}