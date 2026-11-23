package com.gpsfakepro.data

import androidx.room.withTransaction
import com.gpsfakepro.engine.RoutePoint
import kotlinx.coroutines.flow.Flow

/**
 * Single entry point for all local persistence: favourite locations, routes,
 * waypoints and recent locations. All operations are local; nothing leaves the
 * device.
 */
class LocationsRepository(private val db: AppDatabase) {

    // ---- Saved locations -------------------------------------------------

    fun observeLocations(): Flow<List<SavedLocationEntity>> =
        db.savedLocationDao().observeAll()

    fun searchLocations(query: String): Flow<List<SavedLocationEntity>> =
        db.savedLocationDao().observeSearch(query)

    suspend fun addLocation(name: String, lat: Double, lon: Double, alt: Double?): Long =
        db.savedLocationDao().insert(
            SavedLocationEntity(name = name, latitude = lat, longitude = lon, altitude = alt)
        )

    suspend fun updateLocation(id: Long, name: String, lat: Double, lon: Double, alt: Double?) {
        db.savedLocationDao().update(
            SavedLocationEntity(id = id, name = name, latitude = lat, longitude = lon, altitude = alt)
        )
    }

    suspend fun deleteLocation(id: Long) {
        val entity = db.savedLocationDao().byId(id) ?: return
        db.savedLocationDao().delete(entity)
    }

    /** Duplicates a location as "<name> (copy)". Returns the new id. */
    suspend fun duplicateLocation(id: Long): Long {
        val entity = db.savedLocationDao().byId(id) ?: return -1L
        return db.savedLocationDao().insert(
            entity.copy(id = 0L, name = "${entity.name} (copy)")
        )
    }

    // ---- Routes ----------------------------------------------------------

    fun observeRoutes(): Flow<List<RouteEntity>> = db.routeDao().observeAll()

    fun searchRoutes(query: String): Flow<List<RouteEntity>> =
        db.routeDao().observeSearch(query)

    /** Routes with waypoint counts, for list screens. */
    fun observeRoutesWithCounts(query: String): Flow<List<RouteWithCount>> =
        db.routeDao().observeWithCounts(query)

    suspend fun routeWithWaypoints(id: Long): RouteWithWaypoints? {
        val route = db.routeDao().byId(id) ?: return null
        val waypoints = db.routeWaypointDao().forRoute(id)
        return RouteWithWaypoints(route, waypoints)
    }

    fun observeRouteWaypoints(id: Long): Flow<List<RouteWaypointEntity>> =
        db.routeWaypointDao().observeForRoute(id)

    /**
     * Inserts a new route, or updates an existing one (by [routeId]).
     * Waypoints are replaced atomically. Returns the route id.
     */
    suspend fun saveRoute(
        routeId: Long,
        name: String,
        waypoints: List<RoutePoint>,
        speedMps: Double,
        pauseEnabled: Boolean,
        pauseMs: Long
    ): Long = db.withTransaction {
        val id = if (routeId == 0L) {
            db.routeDao().insert(
                RouteEntity(
                    name = name,
                    speedMps = speedMps,
                    pauseEnabled = pauseEnabled,
                    pauseMs = pauseMs
                )
            )
        } else {
            val existing = db.routeDao().byId(routeId) ?: return@withTransaction routeId
            db.routeDao().update(
                existing.copy(
                    name = name,
                    speedMps = speedMps,
                    pauseEnabled = pauseEnabled,
                    pauseMs = pauseMs
                )
            )
            routeId
        }
        db.routeWaypointDao().deleteForRoute(id)
        val entities = waypoints.mapIndexed { index, p ->
            RouteWaypointEntity(
                routeId = id,
                orderIndex = index,
                latitude = p.latitude,
                longitude = p.longitude,
                altitude = p.altitude
            )
        }
        db.routeWaypointDao().insertAll(entities)
        id
    }

    suspend fun deleteRoute(id: Long) {
        val entity = db.routeDao().byId(id) ?: return
        db.withTransaction {
            db.routeDao().delete(entity)
            db.routeWaypointDao().deleteForRoute(id)
        }
    }

    /** Duplicates a route (waypoints included) as "<name> (copy)". */
    suspend fun duplicateRoute(id: Long): Long {
        val existing = routeWithWaypoints(id) ?: return -1L
        return db.withTransaction {
            val newId = db.routeDao().insert(
                existing.route.copy(id = 0L, name = "${existing.route.name} (copy)")
            )
            db.routeWaypointDao().insertAll(
                existing.waypoints.map { it.copy(routeId = newId) }
            )
            newId
        }
    }

    /**
     * Returns a route name that does not collide with an existing one. Imported
     * routes are never silently merged or overwritten: if [baseName] already
     * exists, " (2)", " (3)", … is appended.
     */
    suspend fun uniqueRouteName(baseName: String): String {
        var candidate = baseName.trim()
        if (candidate.isEmpty()) candidate = "Imported route"
        var suffix = 2
        while (db.routeDao().findByName(candidate) != null) {
            candidate = "$baseName ($suffix)"
            suffix++
        }
        return candidate
    }

    suspend fun routeNameExists(name: String): Boolean =
        db.routeDao().findByName(name) != null

    // ---- Recent locations ------------------------------------------------

    suspend fun addRecent(lat: Double, lon: Double) {
        db.recentLocationDao().insert(
            RecentLocationEntity(latitude = lat, longitude = lon)
        )
        db.recentLocationDao().trim(RECENT_KEEP)
    }

    fun observeRecent(): Flow<List<RecentLocationEntity>> =
        db.recentLocationDao().observeRecent(RECENT_KEEP)

    suspend fun latestRecent(): RecentLocationEntity? = db.recentLocationDao().latest()

    suspend fun clearRecent() = db.recentLocationDao().clear()

    companion object {
        const val RECENT_KEEP = 50
    }
}
