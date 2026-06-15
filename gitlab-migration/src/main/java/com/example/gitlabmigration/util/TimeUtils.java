package com.example.gitlabmigration.util;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * UTC timestamp utilities matching the Python toolkit's ISO-8601 format.
 */
public final class TimeUtils {

    private static final DateTimeFormatter ISO_UTC =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

    private TimeUtils() {}

    /** Current UTC timestamp in ISO-8601, e.g. "2025-03-15T14:30:00Z". */
    public static String utcNow() {
        return ISO_UTC.format(Instant.now());
    }

    /** UTC timestamp without colons, suitable for filenames. */
    public static String utcNowFileSafe() {
        return utcNow().replace(":", "");
    }
}
