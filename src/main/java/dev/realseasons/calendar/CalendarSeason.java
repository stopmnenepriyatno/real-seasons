package dev.realseasons.calendar;

import java.time.LocalDate;
import java.util.Objects;

public enum CalendarSeason {
    SPRING, SUMMER, AUTUMN, WINTER;

    public static CalendarSeason fromDate(LocalDate date) {
        return switch (Objects.requireNonNull(date, "date").getMonthValue()) {
            case 3, 4, 5 -> SPRING;
            case 6, 7, 8 -> SUMMER;
            case 9, 10, 11 -> AUTUMN;
            default -> WINTER;
        };
    }
}
