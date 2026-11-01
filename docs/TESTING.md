# Validation checklist — BTA 8.0.1

## Automated checks: passed

Both commands passed from the independent project directory, in this order:

```sh
./gradlew.bat regressionTest --console=plain --offline
./gradlew.bat build --console=plain
```

Each reported `PASS: 930 regression checks` and `BUILD SUCCESSFUL`. The full build regenerated `build/libs/real-seasons-0.0.2+8.0.1.jar` and `build/libs/real-seasons-0.0.2+8.0.1-sources.jar`.

The Java executable under `src/regression/java/dev/realseasons/RegressionTests.java` runs as `regressionTest`, which is part of `check`/`build`.

| Automated check | Result |
| --- | --- |
| Every day of leap year 2024 and non-leap year 2025; month boundaries, year rollover and February 29 | Passed |
| UTC vs America/Los_Angeles near March 1; development file updates, invalid/missing file | Passed |
| Creation/reload reads once; lazy initial-night detection, including winter after tick `0` | Passed |
| Load immediately before sunset: first tick detects night; exact sunset and duplicate checks | Passed |
| Seasonal-night predicate boundaries; winter night spans tick `0`; moving sunset after a season change causes no second read | Passed |
| All-day/dawn, frozen time and within-night rewinds cause no further reads | Passed |
| Same-season date still marks the night checked | Passed |
| Sleep/skips into daytime wait for the next observed night; no morning update except initial load | Passed |
| Multi-day/backwards jumps into another night read once; skipped nights are not replayed | Passed |
| Live leap-date/month rollovers and backwards real-date edits apply at the next eligible night | Passed |
| Fresh reload at the same world time reads the new date; per-world isolation | Passed |
| Fixed calendar season over 60 simulated days: exactly 61 reads including initialization | Passed |
| Noon-anchored night-index arithmetic, including negative indices and indices beyond `int` range | Passed |
| ASM: unchanged `WorldMixin` HEAD selector `updateSeasonAndLight()V` | Passed |
| ASM: `WorldType.getTimeOfDay(World, long, float)` exists and calls `getDayLengthTicks` | Passed |
| ASM: calendar manager calls native `getTimeOfDay`, not `isDaytime` / `getSkyDarken` | Passed |
| ASM: `World.tick` order is `updateSleepingPlayers` → `LevelData.setWorldTime` → `updateSeasonAndLight` | Passed |
| ASM: `LevelData` initialized before `SeasonManager.fromConfig` | Passed |
| Java 17 Mixin bytecode, shadow fields, native manager inheritance and no world-time setters | Passed |
| Inherited standard generation/time methods, native WorldType persistence paths and tick inheritance contracts | Passed |

The `nightChangesAndIsolation` and `seasonalNightBoundaries` tests exercise calendar state with injected night predicates. These and the structural bytecode checks do not apply Mixins or launch the game. Native solar-noon invariance and rendering behavior are bytecode-inspected only, not automatically runtime-tested. Normal-world runtime behavior, remote-client authority and the cached every-20-tick heartbeat are unchanged and code-inspected only; they are not automated runtime passes. Actual loading, generation, save/reload and multiplayer remain untested in-game.

## In-game checks: NOT YET EXECUTED

Use a disposable BTA **8.0.1** instance and back up all saves. Install the main JAR, not the sources JAR. Use the test-date file described in the README rather than changing Windows time.

### 1. Startup and selection

- [ ] Start with compatible Loader and Real Seasons only. No Mixin errors in the game log.
- [ ] World type list includes **Real Seasons** with its description.
- [ ] Standard extended/default, flat, winter and other original types remain available.
- [ ] The default selection has not changed to Real Seasons.

### 2. Creation, native effects and persistence

- [ ] Set test date to `2025-11-30`, create Real Seasons and check native autumn colors/effects.
- [ ] Save and quit; change the file to `2025-12-01`, then reopen. It is still Real Seasons and uses the fresh winter date immediately on load.
- [ ] Confirm native dimension metadata stores `WorldType = realseasons:overworld`.
- [ ] Edit the file across a season boundary during daytime. The season stays cached until native sunset, then applies without restarting. Repeat in winter and summer: sunset follows native solar time, not fixed tick `13000` or storm darkening.
- [ ] Edit during an already checked night: no second update across tick `0`, or if the previous season change moved sunset. Reload at night reads once and does not resample that loaded night.
- [ ] Edit, then sleep into morning or skip into daytime: no update until the next observed night. Check dawn does not trigger an update.
- [ ] Jump forward several days or backwards into another night: sample once without replaying skipped nights. Use regression checks for exact read counts.
- [ ] Freeze world time while ticks continue: no further date reads. Resuming into the same checked night must not resample.
- [ ] Loaded terrain colors refresh when the newly sampled effective season changes.
- [ ] Confirm genuine native winter effects (temperature-related freezing/snow where applicable), not just a season label. Allow normal native block/weather update delays.
- [ ] Set dates to `2024-02-28`, `2024-02-29`, `2024-03-01`, `2025-05-31`, `2025-06-01`, `2025-08-31`, `2025-09-01`, `2025-11-30`, `2025-12-01`. After each edit, reach a new observed native night or reload; edits alone must not apply.
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
- [ ] Join/rejoin and return from Nether/Drift: cached calendar state arrives within 20 server ticks, including while server world time is frozen.
- [ ] Change only the server test file from November to December while clients remain connected. Heartbeats retain cached state until the next eligible server night or reload, then both clients receive the new season. Sleeping into morning must not apply the edit.
- [ ] Verify a normal server world retains the native cycle and receives no calendar heartbeat.
- [ ] Verify reconnecting to a different server/save does not retain stale calendar state.
- [ ] Check logs for world-type registry mismatches and test alongside the intended mod list.

## Acceptance boundary

A usable release requires the startup, persistence, normal-world isolation, genuine native effects and time/weather/sleep tests above. Multiplayer must remain labeled unverified until its separate test is completed. A build alone is not an acceptance test.
