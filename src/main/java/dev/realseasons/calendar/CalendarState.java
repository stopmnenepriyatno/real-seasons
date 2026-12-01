package dev.realseasons.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Supplier;

/** Per-world calendar state; reads the local date only on initialization and game-day changes. */
public final class CalendarState {
    private static final long TICKS_PER_DAY = 24000L;

    private final Supplier<LocalDate> dates;
    private CalendarSeason season;
    private long lastDay;

    public CalendarState(Clock clock, long worldTime) {
        this(() -> LocalDate.now(clock), worldTime);
    }

    public CalendarState(Supplier<LocalDate> dates, long worldTime) {
        this.dates = Objects.requireNonNull(dates, "dates");
        this.lastDay = Math.floorDiv(worldTime, TICKS_PER_DAY);
        this.season = CalendarSeason.fromDate(dates.get());
    }

    public CalendarSeason season() {
        return season;
    }

    public boolean tick(long worldTime) {
        long day = Math.floorDiv(worldTime, TICKS_PER_DAY);
        if (day == lastDay) return false;
        // Rebase even after backwards or multi-day skips; never replay skipped days.
        lastDay = day;
        CalendarSeason next = CalendarSeason.fromDate(dates.get());
        if (next == season) return false;
        season = next;
        return true;
    }
}
