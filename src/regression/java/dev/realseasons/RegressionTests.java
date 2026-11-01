package dev.realseasons;

import dev.realseasons.calendar.CalendarSeason;
import dev.realseasons.calendar.CalendarState;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

public final class RegressionTests {
    private static int checks;
    private static final LongPredicate NIGHTS = time -> Math.floorMod(time, 24000L) >= 12000L;

    public static void main(String[] args) throws Exception {
        calendarMapping();
        nightChangesAndIsolation();
        seasonalNightBoundaries();
        dateFile();
        verifiedGameHooks();
        System.out.println("PASS: " + checks + " regression checks (calendar, nightfall, isolation, date file, BTA bytecode contracts).");
        System.out.println("These checks do not launch Minecraft or apply Mixins; in-game validation remains required.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static void calendarMapping() {
        CalendarSeason[] months = {CalendarSeason.WINTER, CalendarSeason.WINTER,
                CalendarSeason.SPRING, CalendarSeason.SPRING, CalendarSeason.SPRING,
                CalendarSeason.SUMMER, CalendarSeason.SUMMER, CalendarSeason.SUMMER,
                CalendarSeason.AUTUMN, CalendarSeason.AUTUMN, CalendarSeason.AUTUMN, CalendarSeason.WINTER};
        for (int year : new int[]{2024, 2025}) {
            LocalDate end = LocalDate.of(year + 1, 1, 1);
            for (LocalDate day = LocalDate.of(year, 1, 1); day.isBefore(end); day = day.plusDays(1)) {
                check(CalendarSeason.fromDate(day) == months[day.getMonthValue() - 1], "Wrong season: " + day);
            }
            for (int month = 1; month <= 12; month++) {
                LocalDate last = YearMonth.of(year, month).atEndOfMonth();
                LocalDate next = last.plusDays(1);
                check(CalendarSeason.fromDate(last) == months[month - 1], "End of month: " + last);
                check(CalendarSeason.fromDate(next) == months[next.getMonthValue() - 1], "Start of month: " + next);
            }
        }
        check(CalendarSeason.fromDate(LocalDate.of(2024, 2, 29)) == CalendarSeason.WINTER, "Leap day");
        Instant boundary = Instant.parse("2025-03-01T00:30:00Z");
        check(new CalendarState(Clock.fixed(boundary, ZoneOffset.UTC), 0, NIGHTS).season() == CalendarSeason.SPRING, "UTC date");
        check(new CalendarState(Clock.fixed(boundary, ZoneId.of("America/Los_Angeles")), 0, NIGHTS).season() == CalendarSeason.WINTER,
                "Must use clock's local timezone, not UTC");
    }

    private static void nightChangesAndIsolation() {
        AtomicReference<LocalDate> date = new AtomicReference<>(LocalDate.of(2025, 11, 30));
        AtomicInteger reads = new AtomicInteger();
        Supplier<LocalDate> source = () -> { reads.incrementAndGet(); return date.get(); };
        CalendarState state = new CalendarState(source, 6000, NIGHTS);
        check(state.season() == CalendarSeason.AUTUMN && reads.get() == 1, "Initial mid-day date read");
        date.set(LocalDate.of(2025, 12, 1));
        for (long time = 6000; time < 12000; time++) {
            if (state.tick(time)) throw new AssertionError("Checked date before sunset");
        }
        check(reads.get() == 1 && state.season() == CalendarSeason.AUTUMN, "Real-date change waits for nightfall");
        check(state.tick(12000) && state.season() == CalendarSeason.WINTER && reads.get() == 2, "Exact sunset boundary");
        check(!state.tick(12000) && reads.get() == 2, "Repeated sunset does not reread date");
        for (long time = 12001; time < 36000; time++) {
            if (state.tick(time)) throw new AssertionError("Unexpected change before next night");
        }
        check(reads.get() == 2, "No extra calendar reads at night, dawn or in daytime");
        date.set(LocalDate.of(2026, 2, 28));
        check(!state.tick(36000) && reads.get() == 3, "Read once even if effective season is unchanged");
        check(!state.tick(36001) && reads.get() == 3, "Unchanged season still records the checked night");
        date.set(LocalDate.of(2026, 3, 1));
        check(state.tick(60000) && state.season() == CalendarSeason.SPRING && reads.get() == 4, "Live Feb/Mar rollover");
        date.set(LocalDate.of(2024, 2, 29));
        check(state.tick(84000) && state.season() == CalendarSeason.WINTER && reads.get() == 5, "Backwards real-date change");
        date.set(LocalDate.of(2025, 5, 31));
        check(!state.tick(120001) && reads.get() == 5, "Sleep or skip landing in morning does not read date");
        check(state.tick(132000) && state.season() == CalendarSeason.SPRING && reads.get() == 6, "After sleeping, wait for next nightfall");
        date.set(LocalDate.of(2025, 6, 1));
        check(state.tick(2400000 + 12000) && state.season() == CalendarSeason.SUMMER && reads.get() == 7,
                "Multi-day skip into night reads once, without replaying skipped nights");
        date.set(LocalDate.of(2025, 9, 1));
        check(state.tick(60000) && state.season() == CalendarSeason.AUTUMN && reads.get() == 8,
                "Backwards skip into another night checks date");
        date.set(LocalDate.of(2025, 12, 1));
        check(!state.tick(61000) && !state.tick(60500) && reads.get() == 8, "Rewind within the same night does not reread");
        check(!state.tick(54000) && !state.tick(60000) && reads.get() == 8, "Returning to an already checked night does not reread");
        check(state.tick(84000) && state.season() == CalendarSeason.WINTER && reads.get() == 9,
                "Next night after backwards skip is not blocked by old night number");
        for (int i = 0; i < 24000; i++) {
            if (state.tick(84000)) throw new AssertionError("Frozen game time changed the season");
        }
        check(reads.get() == 9, "Frozen game time never rereads date");
        CalendarState other = new CalendarState(Clock.fixed(Instant.parse("2025-07-01T12:00:00Z"), ZoneOffset.UTC), 84000, NIGHTS);
        check(other.season() == CalendarSeason.SUMMER && state.season() == CalendarSeason.WINTER, "World state isolation");
        date.set(LocalDate.of(2025, 9, 1));
        CalendarState reloaded = new CalendarState(source, 84000, NIGHTS);
        check(reloaded.season() == CalendarSeason.AUTUMN && reads.get() == 10, "Reload reads fresh date at night");
        check(!reloaded.tick(84001) && reads.get() == 10, "Loading at night does not duplicate initial read");
        check(state.season() == CalendarSeason.WINTER, "Reload does not alter another world's state");

        AtomicInteger fixedReads = new AtomicInteger();
        CalendarState fixed = new CalendarState(() -> { fixedReads.incrementAndGet(); return LocalDate.of(2024, 2, 29); }, 6000, NIGHTS);
        for (long time = 6001; time <= 6000 + 24000 * 60; time++) {
            if (fixed.tick(time)) throw new AssertionError("Game ticks advanced a fixed calendar season");
        }
        check(fixed.season() == CalendarSeason.WINTER && fixedReads.get() == 61, "60 game days: one read per night, fixed real season");
        for (int month = 1; month <= 12; month++) {
            AtomicReference<LocalDate> d = new AtomicReference<>(YearMonth.of(2024, month).atEndOfMonth());
            CalendarState s = new CalendarState(d::get, 11999, NIGHTS);
            CalendarSeason old = s.season();
            d.set(d.get().plusDays(1));
            check(!s.tick(11999), "Month boundary waits for nightfall");
            check(s.tick(12000) == (old != CalendarSeason.fromDate(d.get())), "Boundary effective change " + month);
            check(s.season() == CalendarSeason.fromDate(d.get()), "Live boundary " + month);
        }

        AtomicInteger negativeReads = new AtomicInteger();
        CalendarState negative = new CalendarState(() -> { negativeReads.incrementAndGet(); return date.get(); }, -12001, NIGHTS);
        date.set(LocalDate.of(2025, 12, 1));
        check(negative.tick(-12000) && negativeReads.get() == 2, "Negative-time sunset");
        check(!negative.tick(-1) && !negative.tick(0) && negativeReads.get() == 2, "Negative night and following dawn do not reread");
        check(!negative.tick(-36000) && negativeReads.get() == 3, "Rewind into earlier negative night reads once");
        check(!negative.tick(12000) && negativeReads.get() == 4, "Return from negative time rebases tracking");
        long distantDay = 24000L * (Integer.MAX_VALUE + 1L);
        CalendarState distant = new CalendarState(source, distantDay + 6000, NIGHTS);
        date.set(LocalDate.of(2025, 6, 1));
        check(distant.tick(distantDay + 12000) && distant.season() == CalendarSeason.SUMMER, "Night index does not overflow int");
    }

    private static void seasonalNightBoundaries() {
        AtomicReference<LocalDate> date = new AtomicReference<>(LocalDate.of(2025, 11, 30));
        AtomicInteger reads = new AtomicInteger();
        AtomicInteger sunset = new AtomicInteger(10000);
        LongPredicate nights = time -> {
            long tick = Math.floorMod(time, 24000L);
            int sunrise = 12000 - sunset.get();
            return sunrise >= 0 ? tick >= sunset.get() || tick < sunrise
                    : tick >= sunset.get() && tick < 24000 + sunrise;
        };
        Supplier<LocalDate> source = () -> { reads.incrementAndGet(); return date.get(); };
        CalendarState state = new CalendarState(source, 6000, nights);
        date.set(LocalDate.of(2025, 12, 1));
        check(!state.tick(9999) && reads.get() == 1, "Short-day sunset not reached");
        check(state.tick(10000) && reads.get() == 2, "Season-dependent earlier sunset");
        date.set(LocalDate.of(2025, 3, 1));
        check(!state.tick(23999) && !state.tick(24000) && !state.tick(25999) && reads.get() == 2,
                "One long winter night spans tick zero without another read");
        check(!state.tick(26000) && reads.get() == 2, "Winter sunrise does not apply date");
        check(state.tick(34000) && reads.get() == 3, "Next winter night samples date");
        sunset.set(14000);
        check(!state.tick(37999) && !state.tick(38000) && reads.get() == 3,
                "Season change moving sunset cannot reread the same evening");
        check(!state.tick(46000) && !state.tick(48000) && reads.get() == 3, "Summer sunrise and tick zero do not read");
        check(!state.tick(62000) && reads.get() == 4, "Next long-day sunset reads once");

        AtomicInteger loadedReads = new AtomicInteger();
        CalendarState loaded = new CalendarState(() -> { loadedReads.incrementAndGet(); return date.get(); }, 25000, nightsForWinter());
        check(!loaded.tick(25001) && loadedReads.get() == 1, "Loading during post-zero winter night reads once");
        check(!loaded.tick(34000) && loadedReads.get() == 2, "First new night after nighttime load reads once");
        CalendarState atSunset = new CalendarState(source, 13999, nights);
        date.set(LocalDate.of(2025, 6, 1));
        check(atSunset.tick(14000), "First tick crossing sunset after load is not suppressed");
    }

    private static LongPredicate nightsForWinter() {
        return time -> {
            long tick = Math.floorMod(time, 24000L);
            return tick >= 10000 || tick < 2000;
        };
    }

    private static void dateFile() throws Exception {
        Path file = Files.createTempFile("real-seasons-date", ".txt");
        String previous = System.getProperty("realseasons.testDateFile");
        try {
            Files.writeString(file, "2025-11-30\n");
            System.setProperty("realseasons.testDateFile", file.toString());
            Supplier<LocalDate> source = CalendarDateSource.create();
            CalendarState state = new CalendarState(source, 6000, NIGHTS);
            check(state.season() == CalendarSeason.AUTUMN, "Read test date file");
            Files.writeString(file, "2025-12-01\n");
            check(!state.tick(11999) && state.season() == CalendarSeason.AUTUMN, "File edit waits for nightfall");
            check(state.tick(12000) && state.season() == CalendarSeason.WINTER, "Live file update at sunset");
            Files.writeString(file, "not-a-date");
            check(source.get().equals(LocalDate.of(2025, 12, 1)), "Invalid file keeps last valid date");
            Files.delete(file);
            check(source.get().equals(LocalDate.of(2025, 12, 1)), "Missing file keeps last valid date");
        } finally {
            if (previous == null) System.clearProperty("realseasons.testDateFile");
            else System.setProperty("realseasons.testDateFile", previous);
            Files.deleteIfExists(file);
        }
    }

    private static ClassNode node(String name) throws Exception {
        try (InputStream stream = RegressionTests.class.getResourceAsStream("/" + name + ".class")) {
            if (stream == null) throw new AssertionError("Missing class: " + name);
            ClassNode n = new ClassNode();
            new ClassReader(stream).accept(n, 0);
            return n;
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> a, List<AnnotationNode> b) {
        List<AnnotationNode> result = new ArrayList<>();
        if (a != null) result.addAll(a);
        if (b != null) result.addAll(b);
        return result;
    }

    private static Object value(AnnotationNode a, String key) {
        for (int i = 0; a.values != null && i < a.values.size(); i += 2) {
            if (key.equals(a.values.get(i))) return a.values.get(i + 1);
        }
        return null;
    }

    private static void verifiedGameHooks() throws Exception {
        String[] mixins = {"WorldTypesMixin", "WorldTypeGroupsMixin", "SeasonManagerMixin", "WorldMixin",
                "client/RemoteWorldMixin", "client/PacketHandlerClientMixin", "client/LanguageMixin", "server/WorldServerMixin"};
        for (String mixin : mixins) {
            ClassNode m = node("dev/realseasons/mixin/" + mixin);
            check(m.version == Opcodes.V17, "Java 17 bytecode: " + mixin);
            AnnotationNode target = annotations(m.visibleAnnotations, m.invisibleAnnotations).stream()
                    .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
            Type type = (Type) ((List<?>) value(target, "value")).get(0);
            ClassNode game = node(type.getInternalName());
            for (MethodNode method : m.methods) {
                for (AnnotationNode a : annotations(method.visibleAnnotations, method.invisibleAnnotations)) {
                    if (!a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Inject;")) continue;
                    for (Object selector : (List<?>) value(a, "method")) {
                        String s = selector.toString();
                        check(game.methods.stream().anyMatch(g -> s.equals(g.name) || s.equals(g.name + g.desc)),
                                "Verified injection target: " + game.name + "." + s);
                    }
                }
            }
            for (FieldNode field : m.fields) {
                boolean shadow = annotations(field.visibleAnnotations, field.invisibleAnnotations).stream()
                        .anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;"));
                if (shadow) check(game.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc)),
                        "Verified shadow field: " + game.name + "." + field.name);
            }
        }
        ClassNode realType = node("dev/realseasons/RealSeasonsWorldType");
        check(realType.superName.equals("net/minecraft/core/world/type/overworld/WorldTypeOverworld"), "Inherit standard generator");
        check(realType.methods.stream().noneMatch(m -> m.name.equals("createChunkGenerator") || m.name.equals("createBiomeProvider")
                || m.name.equals("onWorldCreation") || m.name.equals("getCelestialAngle")), "No terrain or time overrides");
        ClassNode manager = node("dev/realseasons/CalendarSeasonManager");
        check(manager.superName.equals("net/minecraft/core/world/season/SeasonManager"), "Use native season manager");
        for (MethodNode method : manager.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call) {
                    check(!Set.of("setWorldTime", "setWorldTimeUpdateTicks", "setTotalWorldTime").contains(call.name), "Do not change game time");
                }
            }
        }
        ClassNode worldType = node("net/minecraft/core/world/type/WorldType");
        check(worldType.methods.stream().anyMatch(m -> m.name.equals("getTimeOfDay")
                && m.desc.equals("(Lnet/minecraft/core/world/World;JF)F")), "Verified native solar-time getter");
        MethodNode solarTime = worldType.methods.stream().filter(m -> m.name.equals("getTimeOfDay")).findFirst().orElseThrow();
        boolean seasonalDayLength = false;
        for (AbstractInsnNode insn : solarTime.instructions) {
            if (insn instanceof MethodInsnNode call && call.name.equals("getDayLengthTicks")) seasonalDayLength = true;
        }
        check(seasonalDayLength, "Native solar time accounts for seasonal day length");
        boolean nativeNight = false;
        for (MethodNode method : manager.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof MethodInsnNode call) {
                    if (call.owner.equals(worldType.name) && call.name.equals("getTimeOfDay")) nativeNight = true;
                    check(!call.name.equals("isDaytime") && !call.name.equals("getSkyDarken"), "Night detection must not depend on weather");
                }
            }
        }
        check(nativeNight, "Calendar uses native solar time, not a fixed sunset tick");
        ClassNode dimensionData = node("net/minecraft/core/world/save/DimensionData");
        for (String name : new String[]{"fromCompoundTag", "toCompoundTag"}) {
            MethodNode method = dimensionData.methods.stream().filter(m -> m.name.equals(name)).findFirst().orElseThrow();
            boolean key = false;
            for (AbstractInsnNode insn : method.instructions) {
                if (insn instanceof LdcInsnNode constant && "WorldType".equals(constant.cst)) key = true;
            }
            check(key, "Native WorldType persistence key: " + name);
        }
        ClassNode world = node("net/minecraft/core/world/World");
        check(world.methods.stream().anyMatch(m -> m.name.equals("getWorldTime") && m.desc.equals("()J")),
                "Verified world-time getter");
        MethodNode worldTick = world.methods.stream().filter(m -> m.name.equals("tick") && m.desc.equals("()V")).findFirst().orElseThrow();
        int sleepIndex = -1, timeIndex = -1, seasonIndex = -1;
        for (int i = 0; i < worldTick.instructions.size(); i++) {
            if (worldTick.instructions.get(i) instanceof MethodInsnNode call) {
                if (call.name.equals("updateSleepingPlayers")) sleepIndex = i;
                if (call.owner.equals("net/minecraft/core/world/save/LevelData") && call.name.equals("setWorldTime")) timeIndex = i;
                if (call.name.equals("updateSeasonAndLight")) seasonIndex = i;
            }
        }
        check(sleepIndex >= 0 && timeIndex > sleepIndex && seasonIndex > timeIndex,
                "Calendar hook runs after sleep and game-time advancement");
        MethodNode constructor = world.methods.stream().filter(m -> m.name.equals("<init>")
                && m.desc.endsWith("Lnet/minecraft/core/world/save/DimensionData;)V")).findFirst().orElseThrow();
        int levelDataIndex = -1, factoryIndex = -1;
        for (int i = 0; i < constructor.instructions.size(); i++) {
            AbstractInsnNode insn = constructor.instructions.get(i);
            if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTFIELD && field.name.equals("levelData")) levelDataIndex = i;
            if (insn instanceof MethodInsnNode call && call.owner.equals("net/minecraft/core/world/season/SeasonManager")
                    && call.name.equals("fromConfig")) factoryIndex = i;
        }
        check(levelDataIndex >= 0 && factoryIndex > levelDataIndex, "Initial game time is available when creating the calendar manager");
        ClassNode server = node("net/minecraft/server/world/WorldServer");
        MethodNode tick = server.methods.stream().filter(m -> m.name.equals("tick") && m.desc.equals("()V")).findFirst().orElseThrow();
        boolean superTick = false;
        for (AbstractInsnNode insn : tick.instructions) {
            if (insn instanceof MethodInsnNode call && call.owner.equals("net/minecraft/core/world/World") && call.name.equals("tick")) superTick = true;
        }
        check(superTick, "Server executes the calendar tick hook");
        check(node("net/minecraft/client/world/WorldClient").methods.stream().noneMatch(m -> m.name.equals("tick")), "Singleplayer inherits World.tick");
        check(node("dev/realseasons/mixin/client/RemoteWorldMixin").interfaces.contains("dev/realseasons/RemoteCalendarWorld"), "Remote world marker");
    }
}
