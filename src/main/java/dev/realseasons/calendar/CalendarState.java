package dev.realseasons.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;
import java.util.function.Supplier;

/** Per-world state; never reads game time or changes the clock. */
public final class CalendarState {
    private final Supplier<LocalDate> dates;
    private CalendarSeason season;
    private int ticks;

    public CalendarState(Clock clock) {
        this(() -> LocalDate.now(clock));
    }

    public CalendarState(Supplier<LocalDate> dates) {
        this.dates = Objects.requireNonNull(dates, "dates");
        this.season = CalendarSeason.fromDate(dates.get());
    }

    public CalendarSeason season() {
        return season;
    }

    public boolean tick() {
        if (++ticks < 20) return false;
        ticks = 0;
        CalendarSeason next = CalendarSeason.fromDate(dates.get());
        if (next == season) return false;
        season = next;
        return true;
    }
}
