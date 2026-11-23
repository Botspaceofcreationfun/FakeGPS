package com.gpsfakepro.data

import android.content.Context

/**
 * Simple manual dependency container initialized once in [com.gpsfakepro.GpsFakeProApp].
 */
object AppContainer {
    lateinit var database: AppDatabase
    lateinit var locations: LocationsRepository

    fun init(context: Context) {
        database = AppDatabase.get(context)
        locations = LocationsRepository(database)
    }
}
