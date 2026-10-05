# Configuration

The app has no settings that change how it behaves: no feature switches, no server address, no keys. Everything configurable is on the build side. This page lists those knobs, then the constants that look like knobs and are not.

## Where the files live

All build configuration is at the repository root, and two of the three files are never committed.

| File | Committed | Holds |
|---|---|---|
| `local.properties` | no | where the Android SDK is |
| `keystore.properties` | no | release signing |
| `gradle.properties` | yes | Gradle's memory and caching |

## `local.properties`

| Key | Default | What changes |
|---|---|---|
| `sdk.dir` | none | Where Gradle finds the SDK, and where the Makefile finds `adb` and the emulator. `make doctor` reports it missing |

## `keystore.properties`

Read by `app/build.gradle.kts`. When the file is absent, the release APK builds unsigned and nothing else changes. [BUILD.md](../BUILD.md) covers making a key.

| Key | What changes |
|---|---|
| `storeFile` | path to the keystore |
| `storePassword` | the keystore's password |
| `keyAlias` | which key in it |
| `keyPassword` | that key's password |

## `gradle.properties`

| Key | Value | What changes |
|---|---|---|
| `org.gradle.jvmargs` | `-Xmx4g` | Gradle's heap |
| `org.gradle.parallel` | `true` | builds modules in parallel |
| `org.gradle.caching` | `true` | reuses task outputs |

## Environment and system properties

| Name | Where | Default | What changes |
|---|---|---|---|
| `JAVA_HOME` | `Makefile` | the JDK 17 that `/usr/libexec/java_home -v 17` finds | which JDK every Make target hands Gradle |
| `traveler.schemaDir` | `app/build.gradle.kts`, read in `app/src/test/kotlin/dev/tlong/traveler/Fixtures.kt` | the repository's `schema/` | where the JVM tests find the shared fixtures |

## Command-line flags

| Command | Flag | What changes |
|---|---|---|
| `tools/validate_trip.py` | `--complete` | also requires stars, prices, priorities and booking details |
| `tools/emu/ui.py` | `tap`, `longdrag`, `scroll`, `shot`, `texts`, `back` | which action it takes on the emulator; `scroll` takes y coordinates |

The Make targets (`make help` lists them) take no options.

## Constants that are not configurable

Each is a value in the source. Changing one means editing that line and rebuilding.

<details>
<summary>Editing and storage (5)</summary>

| Constant | Value | Where |
|---|---|---|
| Undo depth per open trip | 50 edits | `data/TripSession.kt:102` |
| Snapshots kept per trip | 20 | `data/TripStore.kt:146` |
| Time a deleted trip is kept | 30 days | `data/TripStore.kt:147` |
| Trip format version read and written | 1 | `model/Trip.kt:16` |
| Database file name | `traveler.db` | `data/Database.kt` |

</details>

<details>
<summary>Planning rules (5)</summary>

| Constant | Value | Where |
|---|---|---|
| Upcoming trips window on the trip list | 30 days | `ui/trips/TripsScreen.kt:150` |
| Time a transfer blocks before departure | 90 minutes | `domain/Conflicts.kt:43` |
| Assumed length of an activity, booking or transfer with no end | 60 minutes | `domain/Conflicts.kt:24` |
| Shortest activity length used in checks | 15 minutes | `domain/Conflicts.kt:32` |
| Slot hours | morning 06–12, afternoon 12–17, evening 17–23:59 | `domain/TripCalendar.kt:100` |

</details>

<details>
<summary>Maps (8)</summary>

| Constant | Value | Where |
|---|---|---|
| Tile server | OpenFreeMap's `planet` tiles | `ui/map/AreaMap.kt:55` |
| Deepest zoom on stay maps | 13, about a quarter mile per pin | `ui/map/AreaMap.kt:58` |
| Shallowest zoom saved offline | 8 | `ui/map/OfflineMaps.kt:31` |
| Margin around a stay's places when saving | 2 km | `domain/MapAreas.kt:10` |
| Cache for tiles you have viewed | 100 MB | `ui/map/AreaMap.kt:105` |
| Distance at which pins merge | 30 dp | `ui/map/AreaMap.kt:192` |
| Trip picture quality | WebP at 80 | `ui/map/TripBackdrop.kt:96` |
| Country outline precision | 0.02°, about 2 km | `tools/make_basemap.py:20` |

</details>

<details>
<summary>Network and build (5)</summary>

| Constant | Value | Where |
|---|---|---|
| Trip image download timeouts | 8 s to connect, 15 s to read | `ui/common/Files.kt:72` |
| Oldest Android supported | API 29, Android 10 | `app/build.gradle.kts:24` |
| CPU types built | arm64-v8a and x86_64 | `app/build.gradle.kts:30` |
| Code shrinking in release | off | `app/build.gradle.kts:47` |
| Folders shipped as app assets | `schema/examples`, `prompts` | `app/build.gradle.kts` |

</details>

*Generated from 72c887b on 2026-10-05.*
