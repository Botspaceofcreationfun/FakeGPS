package com.gpsfakepro.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedLocationDao {
    @Insert
    suspend fun insert(location: SavedLocationEntity): Long

    @Update
    suspend fun update(location: SavedLocationEntity)

    @Delete
    suspend fun delete(location: SavedLocationEntity)

    @Query("SELECT * FROM saved_locations ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<SavedLocationEntity>>

    @Query(
        "SELECT * FROM saved_locations WHERE (:query = '' OR name LIKE '%' || :query || '%') " +
            "ORDER BY name COLLATE NOCASE"
    )
    fun observeSearch(query: String): Flow<List<SavedLocationEntity>>

    @Query("SELECT * FROM saved_locations WHERE id = :id")
    suspend fun byId(id: Long): SavedLocationEntity?
}

@Dao
interface RouteDao {
    @Insert
    suspend fun insert(route: RouteEntity): Long

    @Update
    suspend fun update(route: RouteEntity)

    @Delete
    suspend fun delete(route: RouteEntity)

    @Query("SELECT * FROM routes ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RouteEntity>>

    @Query(
        "SELECT * FROM routes WHERE (:query = '' OR name LIKE '%' || :query || '%') " +
            "ORDER BY name COLLATE NOCASE"
    )
    fun observeSearch(query: String): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id")
    suspend fun byId(id: Long): RouteEntity?

    @Query("SELECT * FROM routes WHERE lower(name) = lower(:name)")
    suspend fun findByName(name: String): RouteEntity?

    /** Routes with their waypoint counts, for list display. */
    @Query(
        "SELECT routes.*, COUNT(route_waypoints.routeId) AS waypointCount FROM routes " +
            "LEFT JOIN route_waypoints ON routes.id = route_waypoints.routeId " +
            "WHERE (:query = '' OR routes.name LIKE '%' || :query || '%') " +
            "GROUP BY routes.id ORDER BY routes.name COLLATE NOCASE"
    )
    fun observeWithCounts(query: String): Flow<List<RouteWithCount>>

    @Query(
        "SELECT routes.*, COUNT(route_waypoints.routeId) AS waypointCount FROM routes " +
            "LEFT JOIN route_waypoints ON routes.id = route_waypoints.routeId " +
            "GROUP BY routes.id ORDER BY routes.name COLLATE NOCASE"
    )
    fun observeAllWithCounts(): Flow<List<RouteWithCount>>
}

data class RouteWithCount(
    @Embedded val route: RouteEntity,
    val waypointCount: Int
)

@Dao
interface RouteWaypointDao {
    @Insert(onConflict = androidx.room.OnConflictStrategy.REPLACE)
    suspend fun insertAll(waypoints: List<RouteWaypointEntity>)

    @Query("DELETE FROM route_waypoints WHERE routeId = :routeId")
    suspend fun deleteForRoute(routeId: Long)

    @Query("SELECT * FROM route_waypoints WHERE routeId = :routeId ORDER BY orderIndex")
    fun observeForRoute(routeId: Long): Flow<List<RouteWaypointEntity>>

    @Query("SELECT * FROM route_waypoints WHERE routeId = :routeId ORDER BY orderIndex")
    suspend fun forRoute(routeId: Long): List<RouteWaypointEntity>
}

@Dao
interface RecentLocationDao {
    @Insert
    suspend fun insert(location: RecentLocationEntity)

    @Query("SELECT * FROM recent_locations ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<RecentLocationEntity>>

    @Query("SELECT * FROM recent_locations ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(): RecentLocationEntity?

    @Query(
        "DELETE FROM recent_locations WHERE id NOT IN " +
            "(SELECT id FROM recent_locations ORDER BY createdAt DESC LIMIT :limit)"
    )
    suspend fun trim(limit: Int)

    @Query("DELETE FROM recent_locations")
    suspend fun clear()
}
