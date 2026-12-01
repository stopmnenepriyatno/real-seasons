package dev.realseasons;

import dev.realseasons.calendar.CalendarSeason;
import dev.realseasons.calendar.CalendarState;
import net.minecraft.core.world.World;
import net.minecraft.core.world.season.Season;
import net.minecraft.core.world.season.SeasonManager;
import net.minecraft.core.world.season.Seasons;

import java.util.List;

public final class CalendarSeasonManager extends SeasonManager {
    private final CalendarState calendar;
    private CalendarSeason current;

    public CalendarSeasonManager(World world) {
        super(world);
        // isClientSide is assigned AFTER the World superclass constructor in BTA.
        // A marker is therefore necessary to avoid ever consulting a remote client's clock.
        boolean remote = world instanceof RemoteCalendarWorld;
        calendar = remote ? null : new CalendarState(CalendarDateSource.create(), world.getWorldTime());
        current = remote ? CalendarSeason.SPRING : calendar.season();
    }

    public void tickCalendar() {
        if (calendar != null && calendar.tick(world.getWorldTime())) setSeason(calendar.season());
    }

    public CalendarSeason calendarSeason() {
        return current;
    }

    public void acceptServerSeason(CalendarSeason season) {
        if (calendar == null) setSeason(season);
    }

    private void setSeason(CalendarSeason season) {
        if (current == season) return;
        current = season;
        world.allChanged(false, true);
    }

    @Override
    public List<Season> getSeasons() {
        return List.of(getCurrentSeason());
    }

    @Override
    public Season getCurrentSeason() {
        return switch (current) {
            case SPRING -> Seasons.OVERWORLD_SPRING;
            case SUMMER -> Seasons.OVERWORLD_SUMMER;
            case AUTUMN -> Seasons.OVERWORLD_FALL;
            case WINTER -> Seasons.OVERWORLD_WINTER;
        };
    }

    // Match BTA's fixed-season manager: no blending toward adjacent seasons.
    @Override public Season getPreviousSeason() { return getCurrentSeason(); }
    @Override public Season getNextSeason() { return getCurrentSeason(); }
    @Override public float getSeasonProgress() { return 0.5F; }
    @Override public int getDayInSeason() { return 0; }
}
