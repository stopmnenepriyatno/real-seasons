package dev.realseasons;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.function.Supplier;

final class CalendarDateSource {
    private CalendarDateSource() {}

    static Supplier<LocalDate> create() {
        String testFile = System.getProperty("realseasons.testDateFile");
        if (testFile == null || testFile.isBlank()) return LocalDate::now;
        Path path = Path.of(testFile);
        RealSeasons.LOGGER.warn("Development date override enabled: {}", path.toAbsolutePath());
        return new Supplier<>() {
            private LocalDate lastValidDate;
            private boolean warned;

            @Override
            public LocalDate get() {
                try {
                    lastValidDate = LocalDate.parse(Files.readString(path).trim());
                    warned = false;
                } catch (IOException | DateTimeParseException ex) {
                    if (!warned) {
                        RealSeasons.LOGGER.warn("Cannot read test date from {}; keeping the last valid date", path, ex);
                        warned = true;
                    }
                }
                return lastValidDate != null ? lastValidDate : LocalDate.now();
            }
        };
    }
}
