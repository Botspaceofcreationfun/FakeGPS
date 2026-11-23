package com.gpsfakepro.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "saved_locations")
data class SavedLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val speedMps: Double = 5.0,
    val pauseEnabled: Boolean = false,
    val pauseMs: Long = 0L
)

@Entity(tableName = "route_waypoints", primaryKeys = ["routeId", "orderIndex"])
data class RouteWaypointEntity(
    val routeId: Long,
    val orderIndex: Int,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double? = null
)

@Entity(tableName = "recent_locations")
data class RecentLocationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val latitude: Double,
    val longitude: Double,
    val createdAt: Long = System.currentTimeMillis()
)

/** A route together with its ordered waypoints. */
data class RouteWithWaypoints(
    @Embedded val route: RouteEntity,
    @Relation(parentColumn = "id", entityColumn = "routeId")
    val waypoints: List<RouteWaypointEntity>
)
