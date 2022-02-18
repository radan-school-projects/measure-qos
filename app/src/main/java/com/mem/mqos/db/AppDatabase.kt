package com.mem.mqos.db

import android.content.Context
import androidx.room.*

@Database(
  entities = [
    CommandEntity::class,
    PingSequenceResultEntity::class,
    PingFinalResultEntity::class
  ],
  version = 1
)
abstract class AppDatabase: RoomDatabase() {
  abstract fun commandDao(): CommandDao
  abstract fun pingSequenceResultDao(): PingSequenceResultDao
  abstract fun pingFinalResultDao(): PingFinalResultDao

  companion object {
    @Volatile private var instance: AppDatabase? = null
    private val LOCK = Any()

    operator fun invoke(context: Context)= instance ?: synchronized(LOCK){
      instance ?: buildDatabase(context).also { instance = it}
    }

    private fun buildDatabase(context: Context) = Room.databaseBuilder(context,
      AppDatabase::class.java, "todo-list.db")
      .build()
  }
}
