package com.gpsfakepro.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        SavedLocationEntity::class,
        RouteEntity::class,
        RouteWaypointEntity::class,
        RecentLocationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun savedLocationDao(): SavedLocationDao
    abstract fun routeDao(): RouteDao
    abstract fun routeWaypointDao(): RouteWaypointDao
    abstract fun recentLocationDao(): RecentLocationDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gps_fake_pro.db"
                ).build().also { instance = it }
            }
    }
}
