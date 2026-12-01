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
import java.util.function.Supplier;

public final class RegressionTests {
    private static int checks;

    public static void main(String[] args) throws Exception {
        calendarMapping();
        dayChangesAndIsolation();
        dateFile();
        verifiedGameHooks();
        System.out.println("PASS: " + checks + " regression checks (calendar, game-day changes, isolation, date file, BTA bytecode contracts).");
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
        check(new CalendarState(Clock.fixed(boundary, ZoneOffset.UTC), 0).season() == CalendarSeason.SPRING, "UTC date");
        check(new CalendarState(Clock.fixed(boundary, ZoneId.of("America/Los_Angeles")), 0).season() == CalendarSeason.WINTER,
                "Must use clock's local timezone, not UTC");
    }

    private static void dayChangesAndIsolation() {
        AtomicReference<LocalDate> date = new AtomicReference<>(LocalDate.of(2025, 11, 30));
        AtomicInteger reads = new AtomicInteger();
        Supplier<LocalDate> source = () -> { reads.incrementAndGet(); return date.get(); };
        CalendarState state = new CalendarState(source, 12000);
        check(state.season() == CalendarSeason.AUTUMN && reads.get() == 1, "Initial mid-day date read");
        date.set(LocalDate.of(2025, 12, 1));
        for (long time = 12000; time < 24000; time++) {
            if (state.tick(time)) throw new AssertionError("Checked date within the same game day");
        }
        check(reads.get() == 1 && state.season() == CalendarSeason.AUTUMN, "Real midnight waits for next game day");
        check(state.tick(24000) && state.season() == CalendarSeason.WINTER && reads.get() == 2, "Exact game-day boundary");
        check(!state.tick(24000) && reads.get() == 2, "Repeated boundary does not reread date");
        for (long time = 24001; time < 48000; time++) {
            if (state.tick(time)) throw new AssertionError("Unexpected change inside a game day");
        }
        check(reads.get() == 2, "No calendar reads during an entire game day");
        date.set(LocalDate.of(2026, 2, 28));
        check(!state.tick(48000) && reads.get() == 3, "Read once even if effective season is unchanged");
        check(!state.tick(48001) && reads.get() == 3, "Unchanged season still records the checked day");
        date.set(LocalDate.of(2026, 3, 1));
        check(state.tick(72000) && state.season() == CalendarSeason.SPRING && reads.get() == 4, "Live Feb/Mar rollover");
        date.set(LocalDate.of(2024, 2, 29));
        check(state.tick(96000) && state.season() == CalendarSeason.WINTER && reads.get() == 5, "Backwards real-date change");
        date.set(LocalDate.of(2025, 5, 31));
        check(state.tick(126000) && state.season() == CalendarSeason.SPRING && reads.get() == 6, "Sleep skips past exact boundary");
        date.set(LocalDate.of(2025, 6, 1));
        check(state.tick(2400000 + 6000) && state.season() == CalendarSeason.SUMMER && reads.get() == 7,
                "Multi-day skip reads current date once, without replaying skipped days");
        date.set(LocalDate.of(2025, 9, 1));
        check(state.tick(48000 + 6000) && state.season() == CalendarSeason.AUTUMN && reads.get() == 8,
                "Backwards time skip checks date and rebases day tracking");
        date.set(LocalDate.of(2025, 12, 1));
        check(!state.tick(48000 + 1000) && reads.get() == 8, "Backwards skip within the same day does not reread");
        check(state.tick(72000) && state.season() == CalendarSeason.WINTER && reads.get() == 9,
                "Next day after backwards skip is not blocked by old day number");
        for (int i = 0; i < 24000; i++) {
            if (state.tick(72000)) throw new AssertionError("Frozen game time changed the season");
        }
        check(reads.get() == 9, "Frozen game time never rereads date");
        CalendarState other = new CalendarState(Clock.fixed(Instant.parse("2025-07-01T12:00:00Z"), ZoneOffset.UTC), 72000);
        check(other.season() == CalendarSeason.SUMMER && state.season() == CalendarSeason.WINTER, "World state isolation");
        date.set(LocalDate.of(2025, 9, 1));
        CalendarState reloaded = new CalendarState(source, 72000);
        check(reloaded.season() == CalendarSeason.AUTUMN && reads.get() == 10, "Reload reads fresh date even on the same game day");
        check(!reloaded.tick(72001) && reads.get() == 10, "First tick after loading does not duplicate initial read");
        check(state.season() == CalendarSeason.WINTER, "Reload does not alter another world's state");

        AtomicInteger fixedReads = new AtomicInteger();
        CalendarState fixed = new CalendarState(() -> { fixedReads.incrementAndGet(); return LocalDate.of(2024, 2, 29); }, 0);
        for (long time = 1; time <= 24000 * 60; time++) {
            if (fixed.tick(time)) throw new AssertionError("Game ticks advanced a fixed calendar season");
        }
        check(fixed.season() == CalendarSeason.WINTER && fixedReads.get() == 61, "60 game days: one read per day, fixed real season");
        for (int month = 1; month <= 12; month++) {
            AtomicReference<LocalDate> d = new AtomicReference<>(YearMonth.of(2024, month).atEndOfMonth());
            CalendarState s = new CalendarState(d::get, 23998);
            CalendarSeason old = s.season();
            d.set(d.get().plusDays(1));
            check(!s.tick(23999), "Month boundary waits for game day");
            check(s.tick(24000) == (old != CalendarSeason.fromDate(d.get())), "Boundary effective change " + month);
            check(s.season() == CalendarSeason.fromDate(d.get()), "Live boundary " + month);
        }

        AtomicInteger negativeReads = new AtomicInteger();
        CalendarState negative = new CalendarState(() -> { negativeReads.incrementAndGet(); return date.get(); }, 0);
        date.set(LocalDate.of(2025, 12, 1));
        check(negative.tick(-1) && negativeReads.get() == 2, "Negative time belongs to previous day");
        check(!negative.tick(-24000) && negativeReads.get() == 2, "Negative day uses floor division");
        check(!negative.tick(-24001) && negativeReads.get() == 3, "Negative day boundary reads once");
        check(!negative.tick(0) && negativeReads.get() == 4, "Return from negative time rebases tracking");
        long distantDay = 24000L * Integer.MAX_VALUE;
        CalendarState distant = new CalendarState(source, distantDay);
        date.set(LocalDate.of(2025, 6, 1));
        check(distant.tick(distantDay + 24000) && distant.season() == CalendarSeason.SUMMER, "Day number does not overflow int");
    }

    private static void dateFile() throws Exception {
        Path file = Files.createTempFile("real-seasons-date", ".txt");
        String previous = System.getProperty("realseasons.testDateFile");
        try {
            Files.writeString(file, "2025-11-30\n");
            System.setProperty("realseasons.testDateFile", file.toString());
            Supplier<LocalDate> source = CalendarDateSource.create();
            CalendarState state = new CalendarState(source, 12000);
            check(state.season() == CalendarSeason.AUTUMN, "Read test date file");
            Files.writeString(file, "2025-12-01\n");
            check(!state.tick(23999) && state.season() == CalendarSeason.AUTUMN, "File edit waits for game-day boundary");
            check(state.tick(24000) && state.season() == CalendarSeason.WINTER, "Live file update on next game day");
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
