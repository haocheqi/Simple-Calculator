package com.example.calculator.data

import android.content.Context
import androidx.room.Database
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="history")
data class HistoryEntity(@androidx.room.PrimaryKey(autoGenerate=true) val id:Long=0,val mode:String,val expression:String,val result:String,val createdAt:Long=System.currentTimeMillis())
@Dao interface HistoryDao { @Query("SELECT * FROM history ORDER BY createdAt DESC LIMIT 100") fun observe():Flow<List<HistoryEntity>>; @Insert suspend fun insert(item:HistoryEntity); @Query("DELETE FROM history") suspend fun clear() }
@Database(entities=[HistoryEntity::class],version=1,exportSchema=false)
abstract class CalculatorDatabase:RoomDatabase(){abstract fun historyDao():HistoryDao;companion object{fun create(c:Context)=Room.databaseBuilder(c,CalculatorDatabase::class.java,"calculator.db").build()}}
