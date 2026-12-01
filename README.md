# Real Seasons — Better Than Adventure! 8.0.1

A standalone Java 17 mod adding a **Real Seasons** selection to BTA's world type menu. It uses BTA's native seasonal system and the normal **default extended overworld** generator. No Mo' Creatures code, assets, HalpLibe or standard Fabric API is included or required.

**Status:** compiled successfully; **893 regression checks passed**, including the game-day calendar cadence and current bytecode contracts. Not yet launched or tested in BTA; a successful build is not proof of working in-game behavior.

## Behavior

| Local calendar months | Native BTA season |
| --- | --- |
| December–February | Winter (`OVERWORLD_WINTER`) |
| March–May | Spring (`OVERWORLD_SPRING`) |
| June–August | Summer (`OVERWORLD_SUMMER`) |
| September–November | Autumn (`OVERWORLD_FALL`) |

- The host's `LocalDate` (system default timezone) is read once on world creation/load. Later checks compare `Math.floorDiv(worldTime, 24000L)` with the last observed game day: the same day or stopped world time causes no date reads.
- Any changed game day triggers one date read and rebases the observed day, including sleep, forward multi-day skips and backwards time resets. Skipped days are not replayed. Host-date changes within the same game day wait until a day change or reload.
- The season stays at its fixed native midpoint; game days and sleeping only resample the host date, never advance a separate season cycle. There is no gradual calendar-season blending.
- Day/night, sleep, weather progression, native plant mechanics and generation are not replaced. Seasonal effects are provided by BTA, not a cosmetic label.
- Existing weather is allowed to finish normally; a month boundary does not forcibly start snow or rain. Snow, ice and vegetation follow BTA's normal update rules, not an instant whole-world rewrite.
- Normal worlds retain their native `SeasonManagerCycle` or `SeasonManagerSingle`.
- The calendar mode is persisted by BTA's own dimension `WorldType` string: `realseasons:overworld`. No custom save format is introduced.
- Nether and Drift retain their normal native world types and seasons.

## Installation

1. Use **BTA 8.0.1**, Java 17 or newer, and its compatible BTA/Babric Fabric Loader. This project compiles against **`0.18.4-bta.11`**; use that version or a compatible later BTA loader.
2. Copy **`build/libs/real-seasons-0.0.2+8.0.1.jar`** into that game instance's `mods` directory. Do not install the `-sources.jar`.
3. Start BTA and open the world creation settings. Select **Real Seasons** in the world type list, then create the world normally.
4. For multiplayer, install the same mod on the dedicated server and every client. Prefer matching mod lists and versions on both sides. Do not connect an unmodded client to a Real Seasons world.

Back up saves before testing. The first actual launch and menu selection have not yet been verified.

## Build on Windows

Open **this directory** (`better_then_adventure/real-seasons`), not the sibling `mo-creatures-bta` project or the workspace root, in a terminal:

```powershell
.\gradlew.bat build --console=plain
```

The wrapper uses Gradle 9.3.1. Gradle itself requires Java 21; `gradle/gradle-daemon-jvm.properties` selects Temurin 21 (already available in the reference environment). Mod compilation and the checks use Temurin **17**, with `--release 17`. If building on another machine, install both JDKs or configure Gradle toolchain discovery; the original environment already has both.

Run just the regression checks using cached dependencies:

```powershell
.\gradlew.bat regressionTest --console=plain --offline
```

Outputs:

- `build/libs/real-seasons-0.0.2+8.0.1.jar`
- `build/libs/real-seasons-0.0.2+8.0.1-sources.jar`

The project is independent: it has its own settings, wrapper, dependencies, source tree and build output. It is a sibling of `mo-creatures-bta` inside the workspace, not a Gradle subproject of the reference. Neither project depends on the other or on a root Gradle build. Only the Gradle wrapper and daemon JVM configuration were copied from the reference tooling.

First-time builds may download Gradle/JDKs and dependencies from Gradle, Fabric, Maven Central, Signalum and BTA repositories. No secrets or paid APIs are needed. Do not refresh/delete working Gradle caches to fix an unrelated network timeout.

## Architecture

- `calendar/CalendarSeason`: pure month-to-season mapping.
- `calendar/CalendarState`: initial game world time and an injectable date provider; one initial date read, then one read per observed game-day change, with effective-season change detection.
- `RealSeasonsWorldType`: inherits the standard extended `WorldTypeOverworld` generator and properties.
- `CalendarSeasonManager`: native `SeasonManager` implementation using BTA's real `Seasons` objects; fixed-season semantics match `SeasonManagerSingle`.
- Eight small Mixins: type registration, selection list, season factory, game-day checks at `World.updateSeasonAndLight()V` HEAD, remote-world marker, client payload handler, fallback labels and server heartbeat. No terrain, world-time, save-format, sleep or weather injections.
- `docs/BTA-8.0.1-HOOKS.md`: verified classes, descriptors, registration timing and save behavior.

### Multiplayer authority

The dedicated server chooses the season. Every 20 ticks it sends a two-byte, versioned `RealSeasons:Sync` custom payload to players in the calendar overworld. This heartbeat sends cached state without reading the calendar; it covers login, respawn and returning from other dimensions. Clients never read their own calendar for a remote world and cannot set the server's season.

A new remote manager uses a deterministic spring placeholder until the first server update (normally within 20 server ticks). There may therefore be a brief initial visual mismatch. No state is shared globally between worlds or connections. Multiplayer and registry interoperability with other mods still require an actual server/client test.

## Safe date-change testing

**Do not change the Windows clock.** For development, add this Java VM argument to the **game launcher** (or dedicated server launcher), not just the Gradle daemon:

```text
-Drealseasons.testDateFile=D:/code/java/real-seasons-test-date.txt
```

Create that text file with an ISO local date, for example:

```text
2025-11-30
```

While the world runs, change its contents to `2025-12-01`, then wait for the next Minecraft day, sleep across a day boundary, make a day-changing time skip, or reload the world. The next date read should switch native autumn to winter and notify the native renderer. Same-day file edits are not immediate: within-day time changes and frozen world time do not reread the file. Multi-day skips or backwards day resets cause one read, not one per skipped day; reload reads a fresh date. Use `2024-02-29` and `2024-03-01` for leap-year testing. An invalid/missing file retains the last valid date and logs a warning when read; if no valid test date has ever been read, the current local date is used.

In multiplayer only the **server's** override matters. Setting a different test file on a client must not change its received season. Remove the VM argument after testing; without it, only the real local calendar is used.

## Validation and limitations

Executed successfully on the local Windows/JDK environment, in order:

- `./gradlew.bat regressionTest --console=plain --offline` — **893 checks passed**.
- `./gradlew.bat build --console=plain` — **BUILD SUCCESSFUL**, with **893 checks passed** and both main/sources JARs regenerated.

Coverage retains calendar mapping, leap years, month boundaries, timezone behavior and test-file handling. New checks cover initial mid-day reads, exact day boundaries and duplicate calls, no reads within a day or at frozen time, same-season day caching, sleep/multi-day skips, backwards rebasing and within-day rewinds, live date rollovers, fresh reload state, isolation, exactly 61 reads over 60 days, negative `floorDiv` and day indices beyond `int` range. ASM checks the current injection selector, tick call order and constructor initialization order; see [the validation checklist](docs/TESTING.md).

Normal-world behavior, remote-client authority and the cached 20-tick heartbeat are unchanged and code-inspected only, not runtime-tested by this suite.

The invalid-test-file case deliberately produces a logged parse warning during the regression run; it is expected, not a test failure. A logging-library `Reflection.getCallerClass` warning may also appear under the headless test harness.

**Not verified in-game:** Mixin transformation/startup, menu labels, creation and reload of saves, actual climate/vegetation/snow/ice/rendering, same-seed terrain comparison, sleep/weather/day-night behavior and multiplayer. See [the game test checklist](docs/TESTING.md). Bytecode contract checks do not apply Mixins or launch Minecraft.

### Removing the mod

BTA's verified deserializer falls back to the normal default type when a stored world type is unknown. Thus a calendar world should fall back to the normal extended generator and native season cycle without the mod. **This is not an in-game save-compatibility guarantee.** Saving without Real Seasons can replace its type identifier permanently. Keep a backup; normal worlds do not contain the mod's identifier.

### Dedicated server setup

The mod does not replace BTA's server properties UI or add a server world-type alias. A practical initial test is to create a Real Seasons save in singleplayer and transfer a backup copy using your server's normal world-directory procedure. The native stored type is what selects the calendar manager. This workflow still needs verification on an actual server.
