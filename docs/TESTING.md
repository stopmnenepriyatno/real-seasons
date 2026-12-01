# Validation checklist — BTA 8.0.1

## Automated checks: passed

Both commands passed from the independent project directory, in this order:

```sh
./gradlew.bat regressionTest --console=plain --offline
./gradlew.bat build --console=plain
```

Each run passed **893 regression checks** on Java 17 with Gradle 9.3.1/Loom 1.15.5 and a Java 21 Gradle daemon. The full build regenerated the main and sources JARs.

The Java executable under `src/regression/java/dev/realseasons/RegressionTests.java` runs as `regressionTest`, which is part of `check`/`build`.

| Automated check | Result |
| --- | --- |
| Every day of leap year 2024 and non-leap year 2025; all month boundaries, year rollover and February 29 | Passed |
| UTC vs America/Los_Angeles near March 1; development file updates, invalid date and missing file | Passed |
| Initial mid-day state reads the injectable date provider once | Passed |
| Exact `24000` boundary reads once; duplicate call does not reread | Passed |
| Entire same day and frozen world time cause zero further reads | Passed |
| Same-season date still caches the new observed game day | Passed |
| Sleep skipping a day boundary and forward multi-day skips each read once; skipped days are not replayed | Passed |
| Backwards time changes rebase the day; within-day rewind does not read | Passed |
| Live leap-date/month rollovers and backwards real-date changes apply at the next game-day read | Passed |
| Fresh reload state at the same world time reads the new date; per-world isolation | Passed |
| Fixed calendar season over 60 simulated days: exactly 61 reads including initialization | Passed |
| Negative world time uses `Math.floorDiv(worldTime, 24000L)`; day index beyond `int` range | Passed |
| ASM: current `WorldMixin` HEAD selector `updateSeasonAndLight()V` exists in the BTA JAR | Passed |
| ASM: `World.tick` order is `updateSleepingPlayers` → `LevelData.setWorldTime` → `updateSeasonAndLight` | Passed |
| ASM: `LevelData` initialized before `SeasonManager.fromConfig` | Passed |
| Java 17 Mixin bytecode, shadow fields, native manager inheritance and no world-time setters | Passed |
| Inherited standard generation/time methods, native WorldType persistence paths and tick inheritance contracts | Passed |

These are pure calendar and structural bytecode checks, not Mixin transformation or game-launch tests. Normal-world runtime behavior, remote-client authority and the cached every-20-tick heartbeat are unchanged and code-inspected only; they are not automated runtime passes. Actual loading, generation, save/reload and multiplayer remain untested in-game.

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
- [ ] Change the file across a season boundary while playing. The current game day retains its cached season; the next Minecraft day or sleep across a day boundary applies the new date without a restart.
- [ ] Repeat with a forward multi-day time skip and a backwards day reset. Each resamples the current file once without replaying skipped days; verify exact read counts with regression checks above.
- [ ] Edit the file, then change time only within the same game day: no calendar update. Freeze world time while ticks continue: no calendar update until a day change or reload.
- [ ] Loaded terrain colors refresh when the newly sampled effective season changes.
- [ ] Confirm genuine native winter effects (temperature-related freezing/snow where applicable), not just a season label. Allow normal native block/weather update delays.
- [ ] Set dates to `2024-02-28`, `2024-02-29`, `2024-03-01`, `2025-05-31`, `2025-06-01`, `2025-08-31`, `2025-09-01`, `2025-11-30`, `2025-12-01`. After each edit, trigger a game-day change or reload; same-day edits alone must not apply.
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
- [ ] Change only the server test file from November to December while clients remain connected. Same-day heartbeats retain the cached season; after a server game-day change or reload, both clients receive the new season together.
- [ ] Verify a normal server world retains the native cycle and receives no calendar heartbeat.
- [ ] Verify reconnecting to a different server/save does not retain stale calendar state.
- [ ] Check logs for world-type registry mismatches and test alongside the intended mod list.

## Acceptance boundary

A usable release requires the startup, persistence, normal-world isolation, genuine native effects and time/weather/sleep tests above. Multiplayer must remain labeled unverified until its separate test is completed. A build alone is not an acceptance test.
