package kz.birchat.api.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class TimeUtils {

    private TimeUtils() {
    }

    public static LocalDateTime utcNow() {
        return LocalDateTime.now(ZoneOffset.UTC);
    }

    public static OffsetDateTime toUtcOffset(LocalDateTime value) {
        if (value == null) {
            return null;
        }

        return value.atOffset(ZoneOffset.UTC);
    }

    public static OffsetDateTime utcOffsetNow() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}