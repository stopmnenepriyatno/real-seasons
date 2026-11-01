# Real Seasons — Better Than Adventure! 8.0.1

A standalone Java 17 mod adding a **Real Seasons** selection to BTA's world type menu. It uses BTA's native seasonal system and the normal **default extended overworld** generator. No Mo' Creatures code, assets, HalpLibe or standard Fabric API is included or required.

**Status:** compiled successfully; 1124 executable regression checks passed. Not yet launched or tested in BTA. Do not treat a successful build as proof of working in-game behavior.

## Behavior

| Local calendar months | Native BTA season |
| --- | --- |
| December–February | Winter (`OVERWORLD_WINTER`) |
| March–May | Spring (`OVERWORLD_SPRING`) |
| June–August | Summer (`OVERWORLD_SUMMER`) |
| September–November | Autumn (`OVERWORLD_FALL`) |

- The host's local `LocalDate` is read on world construction, then every 20 world ticks while running. Paused worlds check again after ticking resumes.
- The season stays at its fixed native midpoint; game days and sleeping cannot advance it. There is no gradual calendar-season blending.
- Day/night, sleep, weather progression, native plant mechanics and generation are not replaced. Seasonal effects are provided by BTA, not a cosmetic label.
- Existing weather is allowed to finish normally; a month boundary does not forcibly start snow or rain. Snow, ice and vegetation follow BTA's normal update rules, not an instant whole-world rewrite.
- Normal worlds retain their native `SeasonManagerCycle` or `SeasonManagerSingle`.
- The calendar mode is persisted by BTA's own dimension `WorldType` string: `realseasons:overworld`. No custom save format is introduced.
- Nether and Drift retain their normal native world types and seasons.

## Installation

1. Use **BTA 8.0.1**, Java 17 or newer, and its compatible BTA/Babric Fabric Loader. This project compiles against **`0.18.4-bta.11`**; use that version or a compatible later BTA loader.
2. Copy **`build/libs/real-seasons-1.0.0+8.0.1.jar`** into that game instance's `mods` directory. Do not install the `-sources.jar`.
3. Start BTA and open the world creation settings. Select **Real Seasons** in the world type list, then create the world normally.
4. For multiplayer, install the same mod on the dedicated server and every client. Prefer matching mod lists and versions on both sides. Do not connect an unmodded client to a Real Seasons world.

Back up saves before testing. The first actual launch and menu selection have not yet been verified.

## Build on Windows

Open **this directory** (`better_then_adventure/real-seasons`), not the sibling `mo-creatures-bta` project or the workspace root, in a terminal:

```powershell
.\gradlew.bat build --console=plain
```

The wrapper uses Gradle 9.3.1. Gradle itself requires Java 21; `gradle/gradle-daemon-jvm.properties` selects Temurin 21 (already available in the reference environment). Mod compilation and the checks use Temurin **17**, with `--release 17`. If building on another machine, install both JDKs or configure Gradle toolchain discovery; the original environment already has both.

Where the existing dependencies are cached, the verified offline command is:

```powershell
.\gradlew.bat build --console=plain --offline
```

Run just the regression checks:

```powershell
.\gradlew.bat regressionTest --console=plain --offline
```

Outputs:

- `build/libs/real-seasons-1.0.0+8.0.1.jar`
- `build/libs/real-seasons-1.0.0+8.0.1-sources.jar`

The project is independent: it has its own settings, wrapper, dependencies, source tree and build output. It is a sibling of `mo-creatures-bta` inside the workspace, not a Gradle subproject of the reference. Neither project depends on the other or on a root Gradle build. Only the Gradle wrapper and daemon JVM configuration were copied from the reference tooling.

First-time builds may download Gradle/JDKs and dependencies from Gradle, Fabric, Maven Central, Signalum and BTA repositories. No secrets or paid APIs are needed. Do not refresh/delete working Gradle caches to fix an unrelated network timeout.

## Architecture

- `calendar/CalendarSeason`: pure month-to-season mapping.
- `calendar/CalendarState`: injectable clock/date source, per-world 20-tick polling and effective-season change detection.
- `RealSeasonsWorldType`: inherits the standard extended `WorldTypeOverworld` generator and properties.
- `CalendarSeasonManager`: native `SeasonManager` implementation using BTA's real `Seasons` objects; fixed-season semantics match `SeasonManagerSingle`.
- Eight small Mixins: type registration, selection list, season factory, world polling, remote-world marker, client payload handler, fallback labels and server heartbeat. No terrain, world-time, save-format, sleep or weather injections.
- `docs/BTA-8.0.1-HOOKS.md`: verified classes, descriptors, registration timing and save behavior.

### Multiplayer authority

The dedicated server chooses the season. Every 20 ticks it sends a two-byte, versioned `RealSeasons:Sync` custom payload to players in the calendar overworld. This heartbeat covers login, respawn and returning from other dimensions. Clients never read their own calendar for a remote world and cannot set the server's season.

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

While the world runs, change its contents to `2025-12-01`. The next 20-tick check should switch native autumn to winter and notify the native renderer. Use `2024-02-29` and `2024-03-01` for leap-year testing. The file is reread at the same bounded polling rate. An invalid/missing file retains the last valid date and logs a warning; if no valid test date has ever been read, the current local date is used.

In multiplayer only the **server's** override matters. Setting a different test file on a client must not change its received season. Remove the VM argument after testing; without it, only the real local calendar is used.

## Validation and limitations

Executed successfully on the local Windows/JDK environment:

- `compileJava`, including shared, client and server Mixins.
- `regressionTest`: **1124** checks, including every day of a leap and non-leap year, all month boundaries, live rollovers, backwards date changes, timezone behavior, per-world isolation, no change after 60 simulated game days, bounded reads, test-file updates and exact game bytecode contracts.
- `build --console=plain --offline`, creating both JARs.

The invalid-test-file case deliberately produces a logged parse warning during the regression run; it is expected, not a test failure. A logging-library `Reflection.getCallerClass` warning may also appear under the headless test harness.

**Not verified in-game:** Mixin transformation/startup, menu labels, creation and reload of saves, actual climate/vegetation/snow/ice/rendering, same-seed terrain comparison, sleep/weather/day-night behavior and multiplayer. See [the game test checklist](docs/TESTING.md). Bytecode contract checks do not apply Mixins or launch Minecraft.

### Removing the mod

BTA's verified deserializer falls back to the normal default type when a stored world type is unknown. Thus a calendar world should fall back to the normal extended generator and native season cycle without the mod. **This is not an in-game save-compatibility guarantee.** Saving without Real Seasons can replace its type identifier permanently. Keep a backup; normal worlds do not contain the mod's identifier.

### Dedicated server setup

The mod does not replace BTA's server properties UI or add a server world-type alias. A practical initial test is to create a Real Seasons save in singleplayer and transfer a backup copy using your server's normal world-directory procedure. The native stored type is what selects the calendar manager. This workflow still needs verification on an actual server.
