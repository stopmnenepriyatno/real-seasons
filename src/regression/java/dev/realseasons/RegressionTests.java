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
        pollingAndIsolation();
        dateFile();
        verifiedGameHooks();
        System.out.println("PASS: " + checks + " regression checks (calendar, polling, isolation, date file, BTA bytecode contracts).");
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
        check(new CalendarState(Clock.fixed(boundary, ZoneOffset.UTC)).season() == CalendarSeason.SPRING, "UTC date");
        check(new CalendarState(Clock.fixed(boundary, ZoneId.of("America/Los_Angeles"))).season() == CalendarSeason.WINTER,
                "Must use clock's local timezone, not UTC");
    }

    private static void pollingAndIsolation() {
        AtomicReference<LocalDate> date = new AtomicReference<>(LocalDate.of(2025, 11, 30));
        AtomicInteger reads = new AtomicInteger();
        CalendarState state = new CalendarState(() -> { reads.incrementAndGet(); return date.get(); });
        check(state.season() == CalendarSeason.AUTUMN && reads.get() == 1, "Initial date read");
        date.set(LocalDate.of(2025, 12, 1));
        for (int i = 0; i < 19; i++) check(!state.tick(), "Do not check before 20 ticks");
        check(reads.get() == 1 && state.season() == CalendarSeason.AUTUMN, "Polling is bounded");
        check(state.tick() && state.season() == CalendarSeason.WINTER && reads.get() == 2, "Live Nov/Dec rollover");
        date.set(LocalDate.of(2026, 2, 28));
        for (int i = 0; i < 20; i++) check(!state.tick(), "No effective season change within winter");
        date.set(LocalDate.of(2026, 3, 1));
        for (int i = 0; i < 19; i++) state.tick();
        check(state.tick() && state.season() == CalendarSeason.SPRING, "Live Feb/Mar rollover");
        date.set(LocalDate.of(2024, 2, 29));
        for (int i = 0; i < 19; i++) state.tick();
        check(state.tick() && state.season() == CalendarSeason.WINTER, "Backwards date changes");
        CalendarState other = new CalendarState(Clock.fixed(Instant.parse("2025-07-01T12:00:00Z"), ZoneOffset.UTC));
        check(other.season() == CalendarSeason.SUMMER && state.season() == CalendarSeason.WINTER, "World state isolation");
        for (int i = 0; i < 24000 * 60; i++) {
            if (state.tick()) throw new AssertionError("Game ticks advanced a fixed calendar season");
        }
        check(state.season() == CalendarSeason.WINTER, "60 game days cannot advance the calendar");
        for (int month = 1; month <= 12; month++) {
            AtomicReference<LocalDate> d = new AtomicReference<>(YearMonth.of(2024, month).atEndOfMonth());
            CalendarState s = new CalendarState(d::get);
            CalendarSeason old = s.season();
            d.set(d.get().plusDays(1));
            for (int i = 0; i < 19; i++) check(!s.tick(), "Boundary polling interval");
            check(s.tick() == (old != CalendarSeason.fromDate(d.get())), "Boundary effective change " + month);
            check(s.season() == CalendarSeason.fromDate(d.get()), "Live boundary " + month);
        }
    }

    private static void dateFile() throws Exception {
        Path file = Files.createTempFile("real-seasons-date", ".txt");
        String previous = System.getProperty("realseasons.testDateFile");
        try {
            Files.writeString(file, "2025-11-30\n");
            System.setProperty("realseasons.testDateFile", file.toString());
            Supplier<LocalDate> source = CalendarDateSource.create();
            CalendarState state = new CalendarState(source);
            check(state.season() == CalendarSeason.AUTUMN, "Read test date file");
            Files.writeString(file, "2025-12-01\n");
            for (int i = 0; i < 19; i++) state.tick();
            check(state.tick() && state.season() == CalendarSeason.WINTER, "Live file update");
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
