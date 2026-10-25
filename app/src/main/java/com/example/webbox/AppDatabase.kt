package com.example.webbox

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ZoneEntity::class, WebsiteEntity::class, NotepadEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun appDao(): AppDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context, dbName: String = "public_system.db"): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    dbName
                )
                    // 核心优化：移除了 allowMainThreadQueries()，强制所有查询在协程(IO)中执行，彻底告别卡顿
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun closeDb() {
            INSTANCE?.close()
            INSTANCE = null
        }
    }
}