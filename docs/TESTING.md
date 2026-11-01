# Validation checklist — BTA 8.0.1

## Automated checks: executed

Both commands were executed successfully from the independent project directory:

```powershell
.\gradlew.bat build --console=plain --offline
.\gradlew.bat build --console=plain
```

Result: **BUILD SUCCESSFUL**; **1124 regression checks passed** on Java 17 with Gradle 9.3.1/Loom 1.15.5 and a Java 21 Gradle daemon.

The Java executable under `src/regression/java/dev/realseasons/RegressionTests.java` runs as `regressionTest`, which is part of `check`/`build`.

| Check | Result |
| --- | --- |
| Every day of leap year 2024 and non-leap year 2025 | Passed |
| All twelve month boundaries, including year rollover | Passed |
| February 29 | Passed |
| UTC vs America/Los_Angeles near March 1 | Passed |
| Reads on initialization and every twentieth tick only | Passed |
| Live November/December and February/March rollover | Passed |
| Backwards date adjustment | Passed |
| No effective update when date changes inside the same season | Passed |
| Separate per-world calendar state | Passed |
| 60 simulated game days cannot advance a fixed calendar season | Passed |
| Development file live update, invalid date and missing file | Passed |
| Java 17 Mixin bytecode | Passed |
| Exact injection selectors and shadow field descriptors in BTA JAR | Passed |
| Native SeasonManager inheritance; no world-time setters | Passed |
| Inherited standard WorldTypeOverworld generation/time methods | Passed |
| Native DimensionData WorldType key exists in read and write paths | Passed |
| Server super tick and singleplayer inherited tick contracts | Passed |

These are pure calendar and structural bytecode checks. They do not establish actual game loading, Mixin application, world generation or save/reload correctness.

## In-game checks: NOT YET EXECUTED

Use a disposable BTA **8.0.1** instance and back up all saves. Install the main JAR, not the sources JAR. Use the test-date file described in the README rather than changing Windows time.

### 1. Startup and selection

- [ ] Start with compatible Loader and Real Seasons only. No Mixin errors in the game log.
- [ ] World type list includes **Real Seasons** with its description.
- [ ] Standard extended/default, flat, winter and other original types remain available.
- [ ] The default selection has not changed to Real Seasons.

### 2. Creation, native effects and persistence

- [ ] Set test date to `2025-11-30`, create Real Seasons and check native autumn colors/effects.
- [ ] Save and quit; restart and reopen. It is still Real Seasons.
- [ ] Confirm native dimension metadata stores `WorldType = realseasons:overworld`.
- [ ] Change file to `2025-12-01` while playing. Season changes within approximately 20 ticks, without a restart.
- [ ] Loaded terrain colors refresh without waiting for the next in-game day.
- [ ] Confirm genuine native winter effects (temperature-related freezing/snow where applicable), not just a season label. Allow normal native block/weather update delays.
- [ ] Set dates to `2024-02-28`, `2024-02-29`, `2024-03-01`, `2025-05-31`, `2025-06-01`, `2025-08-31`, `2025-09-01`, `2025-11-30`, `2025-12-01`.
- [ ] Existing rain/weather is not forcibly reset at the boundary; future native weather selection reflects the season.
- [ ] Fix the file to a winter date, advance many game days and sleep repeatedly: no seasonal advancement.
- [ ] Check day/night, sleep and native crop/plant behavior remain operational.

### 3. Normal worlds and generation

- [ ] Create a standard extended world and verify its normal season cycle, unaffected by the date file.
- [ ] Open an existing normal save and verify it has not become Real Seasons.
- [ ] Save/reload both modes and switch between them repeatedly, including different test dates.
- [ ] Generate Real Seasons and standard extended worlds with identical seeds/settings. Compare terrain heights, biomes, caves and structures. Account for native seasonal decoration and time-dependent block updates, which are intended seasonal differences.
- [ ] Visit Nether/Drift and return. Their generation and native seasons remain unchanged; overworld calendar mode survives the round trip.
- [ ] With a backup only, remove the mod and inspect BTA's fallback. Do not overwrite the only calendar save while testing removal.

### 4. Multiplayer

- [ ] Install matching versions on server and two clients. Use a backup of a singleplayer Real Seasons save for the first server test.
- [ ] Server override = winter; client overrides = summer and autumn. Both clients receive winter.
- [ ] Test clients in different timezones. Their clocks do not select the season.
- [ ] Join/rejoin and return from Nether/Drift: calendar state arrives within 20 server ticks.
- [ ] Cross November/December using only the server test file while clients remain connected. Both update together.
- [ ] Verify a normal server world retains the native cycle and receives no calendar heartbeat.
- [ ] Verify reconnecting to a different server/save does not retain stale calendar state.
- [ ] Check logs for world-type registry mismatches and test alongside the intended mod list.

## Acceptance boundary

A usable release requires the startup, persistence, normal-world isolation, genuine native effects and time/weather/sleep tests above. Multiplayer must remain labeled unverified until its separate test is completed. A build alone is not an acceptance test.
