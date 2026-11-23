# GPS Fake Pro

An **ad-free, analytics-free Android mock-location simulator** for testing
location-dependent applications and exploring simulated routes on your own
device. Real Android app — Kotlin + Jetpack Compose + osmdroid (OpenStreetMap)
+ Room/DataStore. No Google Maps API key, no paid geocoding service, no account
registration, no ads, no tracking.

> GPS Fake Pro is a debugging tool. It uses Android's **official mock-location
> mechanism** (Developer options → "Select mock location app"). It does **not**
> hide the mock flag from `isFromMockProvider()`, does not use root, and does
> not attempt to defeat third-party location-integrity checks. Some apps can and
> will detect that the location is mocked — that is expected and honest
> behaviour.

---

## 1. Features

| Area | What you get |
|---|---|
| Map | Full-screen OpenStreetMap (osmdroid), tap-to-select, zoom/pan, centre controls, marker for the current simulated position |
| Selection | Tap the map, search by place name (network geocoder, with graceful fallback), or enter latitude/longitude/altitude manually |
| Fallback | Manual coordinates always work — even fully offline (the map may be blank without tiles, the app is still fully usable) |
| Fixed mode | Simulate one fixed position with configurable altitude, speed, bearing and reported horizontal accuracy |
| Route mode | Multi-waypoint straight-line routes, add/delete/reorder/edit waypoints, adjustable speed, optional pauses at waypoints, live progress, moving marker |
| Persistence | Favourite locations, saved routes, recent locations (Room); simulation defaults and language (DataStore) — all on-device |
| GPX | Import/export GPX 1.1 routes and locations; validation with clear errors; name conflicts are resolved by renaming the import, never by overwriting |
| Backgrounding | Foreground service keeps the simulation alive when the UI is backgrounded, with a persistent notification containing status and Pause/Resume/Stop controls |
| Languages | English and Chinese (simplified); switch in Settings, or follow the system language |
| Battery | Update interval configurable 0.5–60 s (default 1 s); higher intervals save battery |

**Scope honesty:** route legs are *straight lines* between waypoints — they do
not follow real roads, and the app does not claim otherwise. Road-following is
deliberately out of scope. GPS Fake Pro also does not claim to override every
location source, app or system service — it only feeds the provider selected
under Developer options.

---

## 2. Build it

Requirements: JDK 17, Android SDK (platform 35), Gradle 8.9 (wrapper included).

```bash
# Android Studio: File → Open → select the GPSFakePro folder → Run ▶
# or from the command line:
export JAVA_HOME=<path-to-JDK-17>
./gradlew :app:assembleDebug
# output: app/build/outputs/apk/debug/app-debug.apk
```

Project key facts:

| Setting | Value |
|---|---|
| applicationId | `com.gpsfakepro` |
| compileSdk / targetSdk | 35 (Android 15) |
| minSdk | 26 (Android 8.0) — covers ~97%+ of active devices; also the version where `Location.setSpeed()` became a first-class API |
| Kotlin / AGP / Gradle | 2.0.21 / 8.7.3 / 8.9 |
| Map | `org.osmdroid:osmdroid-android:6.1.20` (OpenStreetMap, no API key) |
| Persistence | Room 2.6.1 (entities) + DataStore 1.1.1 (preferences) |

`local.properties` contains `sdk.dir` — regenerate it on your machine
(Android Studio does this automatically).

---

## 3. Set up your device (one-time, ~1 minute)

The app injects locations through the **official** mock-location API
(`LocationManager.addTestProvider` / `setTestProviderLocation`). The system
only grants that permission to the app you select in Developer options:

1. Open **Settings → About phone**.
2. Tap **Build number** 7 times until "You are now a developer" appears.
3. Open **Settings → System → Developer options** (path varies by device).
4. Find **Select mock location app** and choose **GPS Fake Pro**.
5. Return to GPS Fake Pro and start a simulation.

Notes:

- Some manufacturers rename this setting ("Allow mock locations", "Mock
  location", "Set mock location app"). If it does not exist on your build,
  mock location is not supported there.
- On Android 13+, grant the notification permission when asked so the
  simulation status stays visible in the background.
- No other permission is required: the app never asks for your real location,
  storage, or contacts. It declares `ACCESS_MOCK_LOCATION` (granted to the
  selected mock app), `INTERNET` (map tiles + search), `FOREGROUND_SERVICE` +
  `FOREGROUND_SERVICE_SPECIAL_USE` (background simulation), and
  `POST_NOTIFICATIONS`.

---

## 4. Usage

### Fixed position
1. On the **Map** tab, tap a point (or search, or enter coordinates).
2. Expand the panel at the bottom → **Start fixed**.
3. While running you can change reported speed, bearing, altitude, accuracy
   and update interval live. **Stop** (red button in the panel header or the
   notification) ends the simulation cleanly.

### Route
1. **Routes** tab → **+** (new route) or open an existing one.
2. Add waypoints: *From current map selection* (select on the map first) or
   *manual coordinates*. Reorder with ↑/↓, edit or delete any waypoint.
3. Set speed and optional pause at each waypoint, then **Save** or **Start
   simulation**.
4. The map draws the full route (grey) and the travelled part (blue), the
   marker moves, and the panel shows progress % / distance / pause countdown.
   Pause / Resume / Restart / Stop behave as expected; when the route finishes,
   the last position continues to be reported until you stop or restart.

### GPX
- **Import**: Routes tab → **Import GPX…** → pick a `.gpx` file. Track
  (`trk/trkpt`) points are used first, then `rte/rtept`, then `wpt`. Invalid
  files and out-of-range coordinates produce specific error messages. If a
  route with the same name already exists, the import is renamed
  ("Name (2)") — **your existing route is never overwritten**.
- **Export**: per route or "Export all" (routes + favourite locations) via the
  system file picker.

---

## 5. Permissions & privacy

- **No** advertising, analytics, crash-reporting or tracking SDKs.
- **No** real-location permission — the app does not read your device location.
- **No** storage permission — everything stays in the app's private database.
- Internet is used only for OpenStreetMap tiles and the system geocoder.
- All favourites, routes and recents are stored locally (Room) and can be
  cleared from Settings.

OpenStreetMap tile usage follows the OSM Tile Usage Policy: the app identifies
itself with a descriptive user-agent, uses modest tile caches, and does **not**
attempt bulk/offline tile downloads. Large-scale tile harvesting is not
supported by design.

---

## 6. Architecture (brief)

```
UI (Compose)  ── observes ──►  SimulationStateHolder (StateFlow)
      │                               ▲
      │ commands (intents)            │ writes
      ▼                               │
LocationSimulationService ──► MockLocationProvider (LocationManager test provider)
      │
      └─ tick loop: RouteSimulator.advance(interval) → build Location →
                    setTestProviderLocation, every N ms
```

- `engine/` — pure Kotlin, JVM-unit-tested: `GeoMath` (haversine/bearing),
  `RouteSimulator` (waypoint progression, pauses, zero-length & invalid-point
  detection), `SimulationConfig`/`SimulationState`.
- `service/` — foreground service (`specialUse`), notification with status +
  Stop, in-process state holder.
- `gpx/` — parser (injected `XmlPullParser`) and exporter with escaping and
  validation, also unit-tested.
- `data/` — Room entities/DAOs/repository, DataStore settings.
- `ui/` — Map (osmdroid host + overlays), Saved locations, Routes + editor,
  Settings (language, defaults, mock-location help).

---

## 7. Acceptance checklist (as implemented)

- ✅ Generates supported mock-location updates via the native test-provider API
  (`addTestProvider` + `setTestProviderLocation` on the GPS provider).
- ✅ Visibly simulates a saved route: moving marker, route polyline, progress
  %, travelled distance, plausible bearing/speed/timestamps.
- ✅ Survives normal backgrounding: foreground service + persistent
  notification (status, Pause/Resume, prominent Stop) keep running.
- ✅ Stops cleanly: provider disabled + removed, notification dismissed,
  service stopped.
- ✅ Handles failures gracefully: mock app not selected → clear instruction
  banner; geocoding unavailable → manual-coordinate fallback; offline → banner;
  invalid routes/coordinates → specific errors; imported-name collision →
  auto-rename.

---

## 8. Known limitations

- Straight-line interpolation, not road-following.
- Works only on devices/builds where the Developer-options mock-location
  setting exists.
- Apps that check `isFromMockProvider()` will detect the mock — deliberately
  not concealed.
- Public OSM tile servers are used; offline use is limited to cached tiles plus
  full manual-coordinate functionality.

---

## License

Provided as-is for personal development and testing use (Apache-2.0).
