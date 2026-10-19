package com.example.facultyattendance.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Display formatting only. Storage always uses UTC instants. */
public final class TimeFormat {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm:ss", Locale.getDefault());
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss", Locale.getDefault());

    private TimeFormat() { }

    public static String dateTime(Instant i, ZoneId zone) {
        return i == null ? "—" : DATE_TIME.format(i.atZone(zone));
    }

    public static String time(Instant i, ZoneId zone) {
        return i == null ? "—" : TIME.format(i.atZone(zone));
    }
}
