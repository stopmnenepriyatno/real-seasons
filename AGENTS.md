# AGENTS.md — Real Seasons for Better Than Adventure! 8.0.1

## Mission

Build and maintain **Real Seasons**, a standalone Java mod for **Better Than Adventure! (BTA) 8.0.1**. The primary goal is to provide a selectable **Real Seasons** world mode whose native BTA season follows the host computer's calendar rather than the normal in-game seasonal cycle.

The user works in **Zed on Windows** and does not need Java tutorials. Make changes directly when tools permit, explain outcomes in plain language, and request only the information or permissions actually needed.

## Known environment

- Workspace container (not a Gradle project): `D:\code\java\better_then_adventure`
- Independent reference project: `D:\code\java\better_then_adventure\mo-creatures-bta`
- Independent Real Seasons project: `D:\code\java\better_then_adventure\real-seasons`
- Reference code: <https://github.com/colinthepwner/mo-creatures-bta>
- JDK 17 installed at `C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot`
- The reference project successfully completed `./gradlew.bat build` on Windows.
- Observed reference tooling: Gradle 9.3.1, Fabric Loom 1.15.5, and a Gradle daemon running on JDK 21. These are **observations**, not requirements for the new mod; verify configurations in files.
- Some Maven downloads from `maven.thesignalumproject.net` previously timed out. Avoid unnecessarily invalidating working caches or refreshing all dependencies.

The workspace contains two independent sibling projects: `mo-creatures-bta` and `real-seasons`. Inspect their files first, keep the reference project intact, and run Gradle commands from the appropriate project directory rather than the workspace root. Do not merge their source trees or add a shared root Gradle build.

## Non-negotiable requirements

1. Target **BTA 8.0.1**, not a modern Minecraft release or a different BTA version.
2. Use the **native BTA season system**, including actual gameplay and visual effects, rather than a cosmetic season label.
3. Provide a selectable world mode named **Real Seasons** while retaining standard BTA generation and leaving normal worlds unchanged.
4. Persist the world-specific Real Seasons setting across saves and reloads.
5. Keep day/night progression, sleep, weather, and unrelated mechanics intact. Never freeze global world time as a shortcut.
6. Use the **local calendar date** (`java.time.LocalDate` and the system default timezone) for singleplayer. Do not call Windows APIs or change the system clock.
7. Season mapping: December–February = winter; March–May = spring; June–August = summer; September–November = autumn.
8. Check for date changes periodically while a Real Seasons world is running; do not require a restart at month boundaries.
9. Keep the server authoritative wherever practical; do not allow a client's clock to override a multiplayer server's season.
10. Do not modify or redistribute unrelated Mo' Creatures source or assets.

## Investigation before implementation

Before writing BTA-specific code:

- Inspect `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `fabric.mod.json`, mappings, existing Mixins, and relevant source files.
- Locate the **actual** BTA 8.0.1 classes and methods for season computation, world type selection, world creation UI, world persistence, and client/server synchronization.
- Record verified class names, method signatures/descriptors, mapping namespaces, and relevant file locations.
- Distinguish confirmed facts from hypotheses. **Never invent APIs, mapped names, Mixin targets, or method signatures.**
- If sources or decompiled game classes are unavailable, explain exactly what is missing and how to obtain or inspect it before proceeding.

A selectable world type may be coupled to terrain generation. If implementing it would alter generation or be unsafe, explain the evidence and propose a separate per-world option as a fallback. Do not silently change the requested design.

## Architecture and implementation

- Create a standalone Gradle/Babric project, using the known-working reference build only as a configuration example.
- Keep calendar calculation isolated and testable, preferably with an injectable `Clock` or date provider.
- Keep the world-mode flag and persistence separate from the calendar calculation.
- Apply the native season only for worlds that explicitly enable Real Seasons.
- Avoid excessive work on every tick; use a lightweight periodic check and only update state when the effective season changes.
- Minimize Mixins. Verify every target and injection point against the exact game version and mappings.
- Avoid invasive global changes, unnecessary dependencies, and unrelated refactoring.
- Use Java language features compatible with the project's configured toolchain and target bytecode; do not assume the Gradle daemon's JDK determines the compiled Java target.
- Preserve normal behavior if the mod is disabled or a world does not use Real Seasons, subject to verified save compatibility.

## Build and test discipline

From the correct project directory on Windows, build with:

```powershell
.\gradlew.bat build --console=plain
```

- Check Gradle output and fix errors rather than claiming success.
- Avoid deleting `.gradle`, changing dependency versions, or using `--refresh-dependencies` without a concrete reason.
- If Maven requests time out, report the exact artifact and repository; do not blindly copy JARs into Gradle's internal cache.
- Test season mapping, including February 29, all month boundaries, and a date change while the game is running.
- Test that normal worlds are unaffected and that the Real Seasons flag survives save/reload.
- Test actual native seasonal effects, not only the displayed season name.
- Test preservation of day/night and weather behavior.
- If feasible, test multiplayer authority separately. Clearly label anything untested.
- A successful Gradle build is **not** proof of successful in-game behavior.

## Agent workflow

1. Inspect the workspace and report the minimal verified findings.
2. Identify native season and world-mode hooks before writing game-specific Mixins.
3. Present a concise implementation plan and any genuinely blocking uncertainty.
4. Implement in small, reviewable changes, preserving the reference project.
5. Run builds and targeted tests; iterate on concrete failures.
6. Summarize files changed, commands run, results, limitations, and the next test to perform in the game.

Prefer acting over asking for routine decisions when access is available. Ask before destructive operations, substantial dependency upgrades, changing the design, or touching files outside the agreed project. Never claim a command was run, a file was inspected, or a feature was tested unless that actually happened.

## Communication

- Communicate with the user in **Russian** unless asked otherwise; keep code, filenames, documentation, and commit messages in **English**.
- Keep explanations concise and actionable. The user is not expected to know Java.
- When blocked, provide the exact command or file needed and explain why.
- Clearly separate **implemented**, **compiled**, **tested in-game**, and **not yet verified** statuses.
