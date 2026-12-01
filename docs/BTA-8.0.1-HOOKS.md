# Verified BTA 8.0.1 integration points

Inspected on Windows with Temurin 17 `jar` and `javap -p -c` before implementing game-specific code. These are actual BTA 8.0.1 symbols, not modern Minecraft/Fabric APIs.

## Reference toolchain

The unmodified reference project uses Gradle 9.3.1, Loom `1.15-SNAPSHOT` (cached implementation: 1.15.5), Loader `0.18.4-bta.11`, HalpLibe `6.1.3+8.0`, and Java 17 compilation with a Java 21 Gradle daemon. Real Seasons pins Loom 1.15.5 and does not depend on HalpLibe, Mod Menu, or any Mo' Creatures content.

BTA metadata: `https://downloads.betterthanadventure.net/bta-client/release/v8.0.1/manifest.json`.

Inspected artifacts, relative to the existing Gradle user home:

- `caches/fabric-loom/8.0.1/minecraft-client.jar`
- `caches/fabric-loom/8.0.1/minecraft-server.jar`
- `caches/fabric-loom/minecraftMaven/net/minecraft/minecraft-merged-deobf/8.0.1/minecraft-merged-deobf-8.0.1.jar`

The reference declares no external mappings dependency. BTA already supplies the descriptive `net.minecraft.*` names used in its deobfuscated Loom compile artifact. All Mixins explicitly use `remap = false`; no modern Yarn/intermediary names are assumed.

## Season system

`net.minecraft.core.world.World` has a private final `SeasonManager seasonManager`. In its protected constructor it calls:

```text
net.minecraft.core.world.season.SeasonManager.fromConfig
(Lnet/minecraft/core/world/World;Lnet/minecraft/core/world/config/season/SeasonConfig;)Lnet/minecraft/core/world/season/SeasonManager;
```

The factory normally creates `SeasonManagerCycle` or `SeasonManagerSingle`. Real Seasons intercepts only this factory for `RealSeasonsWorldType`. It does not rewrite the final world field or affect the factory result for any other type.

`World.getWorldTime()J` reads `LevelData`, which is initialized before `SeasonManager.fromConfig` in the constructor. This lets the calendar manager seed `CalendarState` with the initial game world time and an injectable date provider. An authoritative world reads the host `LocalDate` once on creation/load; a remote client never reads its own date.

Verified abstract methods of `SeasonManager`:

```text
getSeasons()Ljava/util/List;
getPreviousSeason()Lnet/minecraft/core/world/season/Season;
getCurrentSeason()Lnet/minecraft/core/world/season/Season;
getNextSeason()Lnet/minecraft/core/world/season/Season;
getSeasonProgress()F
getDayInSeason()I
```

Native constants in `net.minecraft.core.world.season.Seasons`:

- `OVERWORLD_SPRING`
- `OVERWORLD_SUMMER`
- `OVERWORLD_FALL` (the native name for autumn)
- `OVERWORLD_WINTER` (not the special endless-winter season)

`SeasonManagerSingle` returns its fixed season for previous/current/next, progress `0.5F`, and day `0`. Real Seasons deliberately uses the same fixed-season semantics. Inherited `getSeasonModifier()` returns the full seasonal modifier at progress 0.5.

`SeasonManagerCycle` derives season and progress from world time. Calendar worlds bypass it entirely. World time, sleep and weather duration are not rewritten.

`World.tick()V` is inherited by singleplayer `net.minecraft.client.world.WorldClient`. The override in `net.minecraft.server.world.WorldServer.tick()V` calls `World.tick()V`. Inspection with `javap` of the cached actual BTA 8.0.1 `minecraft-merged-deobf` JAR confirms that `World.tick()V` calls `World.updateSeasonAndLight()V` after `updateSleepingPlayers` and the world-time increment via `LevelData.setWorldTime`.

The common `WorldMixin` injection targets HEAD of `updateSeasonAndLight()V`, replacing the former `tick()V` HEAD poll. It observes the post-sleep, post-increment world time and compares `Math.floorDiv(worldTime, 24000L)` to the last observed day. Same-day calls and stopped world time cause zero date reads. Any changed day, including a forward multi-day skip or backwards reset, causes one read and rebases the observed day; skipped days are not replayed. Host-date changes within a game day wait for a day change or reload.

Native `World.updateSeasonAndLight()V` detects day changes using world time divided by `24000` and normally calls public `World.allChanged(ZZ)V` with its second argument true when the in-game day changes. Calendar manager changes use `allChanged(false, true)` without falsifying the day counter or world time. This notifies native level listeners that seasonal rendering needs rebuilding. Normal-world season managers remain unchanged.

`net.minecraft.core.world.weather.WeatherManager.tick()Z` reads `world.getSeasonManager().getCurrentSeason()` and that native season's `allowedWeathers` and `weatherProbability`. Those native paths remain intact. An existing weather event is not forcibly cancelled at a month boundary.

## World types and standard generation

```text
net.minecraft.core.world.type.WorldTypes.register
(Ljava/lang/String;Lnet/minecraft/core/world/type/WorldType;)Lnet/minecraft/core/world/type/WorldType;
```

The method inserts the type into `net.minecraft.core.data.registry.Registries.WORLD_TYPES`. Register at the tail of `WorldTypes.<clinit>()V`, after all vanilla types, not in the loader entrypoint before block initialization.

The default selection is `WorldTypeGroups.DEFAULT`, whose overworld is `WorldTypes.OVERWORLD_EXTENDED` (not the older 128-high `OVERWORLD_DEFAULT`). Its construction is:

```java
new WorldTypeOverworld(
    WorldTypeOverworld.defaultProperties("worldType.overworld.extended")
        .withTags(WorldTypeTags.OVERWORLD));
```

`RealSeasonsWorldType` uses the same construction except for its language key. It inherits the actual standard `WorldTypeOverworld` methods:

- `createBiomeProvider(World)` creates `BiomeProviderOverworld`.
- `createChunkGenerator(World)` creates `generate.chunk.perlin.overworld.ChunkGeneratorOverworld`.
- `allBiomes()`, spawn checks, initial world time, sky and day/night calculations are inherited unchanged.

No terrain, cave, structure or biome generator is copied or replaced. This establishes generator equivalence by construction; a same-seed in-game comparison has not yet been performed.

`WorldTypeGroups.GROUPS` is a mutable list. A `new WorldTypeGroups.Group(WorldType)` uses normal default types for the other dimensions. Append at the tail of `WorldTypeGroups.<clinit>()V`; do not replace `DEFAULT`.

`net.minecraft.client.gui.worldsettings.WorldTypeButtonComponent.buttonClicked(IIIIIII)V` reads this list for its selector. Its labels append `.name` and `.desc` to `WorldType.getLanguageKey()`. No GUI injection is needed.

`net.minecraft.core.lang.Language` has a protected final `Properties entries` and two constructors, with either `ZipFile` or `File` as the last parameter. The client language Mixin adds only the two mod-specific fallback entries at constructor return; existing translations win. No unrelated language files are replaced.

## Persistence

`net.minecraft.core.world.save.DimensionData` (not `LevelData`) stores a final `WorldType worldType`.

```text
fromCompoundTag(Lcom/mojang/nbt/tags/CompoundTag;Lnet/minecraft/core/world/Dimension;)Lnet/minecraft/core/world/save/DimensionData;
toCompoundTag(Lnet/minecraft/core/world/save/DimensionData;Lcom/mojang/nbt/tags/CompoundTag;)Lcom/mojang/nbt/tags/CompoundTag;
```

The native key is `WorldType`. Serialization uses `Registries.WORLD_TYPES.getKey(type)`; deserialization uses `getItem(savedKey)`. The persisted key is `realseasons:overworld`. No custom NBT flag, save format or additional persistence Mixin is necessary.

If a type is unavailable, BTA falls back to `WorldTypeGroups.DEFAULT.get(dimension)`. Therefore removing the mod lets BTA use its standard extended generator and normal season cycle, but saving without the mod can permanently replace the custom identifier. Back up worlds before removal.

## Multiplayer

Verified symbols:

```text
net.minecraft.core.net.packet.PacketCustomPayload(String, byte[])
net.minecraft.client.net.handler.PacketHandlerClient.handleCustomPayload(Lnet/minecraft/core/net/packet/PacketCustomPayload;)V
net.minecraft.server.net.handler.PacketHandlerServer.sendPacket(Lnet/minecraft/core/net/packet/Packet;)V
net.minecraft.server.entity.player.PlayerServer.playerNetServerHandler
net.minecraft.client.net.handler.PacketHandlerClient.worldClientMP
```

Native login/respawn packets carry numeric registry world type IDs. The client handler reconstructs its `WorldConfiguration` by finding a matching `WorldTypeGroups.Group`. Keeping the mod registered on both sides is required.

`WorldClientMP.isClientSide` is assigned after the superclass constructor. The remote-world marker Mixin is already visible to `instanceof` during that constructor, so its calendar manager never reads the client's date, including during world construction.

Channel `RealSeasons:Sync` carries protocol version 1 and one season byte. The server sends a two-byte heartbeat every 20 ticks in calendar worlds, covering joins and dimension re-entry. This cadence is unchanged: the heartbeat sends cached state and does not read the calendar, even if world time is stopped. Clients reject malformed payloads and apply valid state only to their active calendar manager. No server-side handler accepts a client's season.

Before the first server payload, the remote manager uses a deterministic spring placeholder, never the client's clock. Multiplayer behavior, registry compatibility with other mods and the initial handshake remain unverified in an actual game session.

## Verification boundaries

`./gradlew.bat regressionTest --console=plain --offline` and then `./gradlew.bat build --console=plain` both passed with **893 regression checks**; the full build regenerated the main and sources JARs. Calendar mapping and game-day cadence checks passed; see [TESTING.md](TESTING.md) for coverage.

ASM checks against actual BTA 8.0.1 bytecode verify the current `WorldMixin` HEAD injection selector `updateSeasonAndLight()V` exists, the `World.tick` order `updateSleepingPlayers` → `LevelData.setWorldTime` → `updateSeasonAndLight`, and initialization of `LevelData` before `SeasonManager.fromConfig`. Injection selectors and shadow fields are checked without initializing the game.

Normal-world runtime behavior, remote-client authority and the cached 20-tick heartbeat are unchanged and code-inspected only, not automated runtime passes. Neither ASM nor `javap` inspection applies Mixins or launches the game. Actual Mixin application, save/reload, climate effects, renderer rebuilding, weather, sleep, terrain equivalence and multiplayer remain untested in BTA.
