package dev.realseasons.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.LongPredicate;
import java.util.function.Supplier;

/** Per-world calendar state; reads the local date on initialization and once per observed night. */
public final class CalendarState {
    private static final long TICKS_PER_DAY = 24000L;

    private final Supplier<LocalDate> dates;
    private final LongPredicate nights;
    private final long initialWorldTime;
    private CalendarSeason season;
    private boolean initializedNight;
    private Long lastNight;

    public CalendarState(Clock clock, long worldTime, LongPredicate nights) {
        this(() -> LocalDate.now(clock), worldTime, nights);
    }

    public CalendarState(Supplier<LocalDate> dates, long worldTime, LongPredicate nights) {
        this.dates = Objects.requireNonNull(dates, "dates");
        this.nights = Objects.requireNonNull(nights, "nights");
        this.initialWorldTime = worldTime;
        this.season = CalendarSeason.fromDate(dates.get());
    }

    public CalendarSeason season() {
        return season;
    }

    public boolean tick(long worldTime) {
        if (!initializedNight) {
            // Native solar time needs the world's season manager, unavailable during its construction.
            initializedNight = true;
            if (nights.test(initialWorldTime)) lastNight = nightIndex(initialWorldTime);
        }
        if (!nights.test(worldTime)) return false;
        long night = nightIndex(worldTime);
        if (lastNight != null && lastNight == night) return false;
        // A skip into another night reads once; a skipped night ending in daytime waits for sunset.
        lastNight = night;
        CalendarSeason next = CalendarSeason.fromDate(dates.get());
        if (next == season) return false;
        season = next;
        return true;
    }

    private static long nightIndex(long worldTime) {
        // Anchor cycles at solar noon (tick 6000), so one night can span the tick-zero boundary.
        return Math.floorDiv(worldTime, TICKS_PER_DAY)
                - (Math.floorMod(worldTime, TICKS_PER_DAY) < 6000L ? 1 : 0);
    }
}
