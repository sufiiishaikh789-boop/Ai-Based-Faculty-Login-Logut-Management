package com.example.facultyattendance.config;

import java.time.Duration;
import java.time.ZoneId;

/**
 * Time rules for the attendance state machine. Everything is configurable; nothing is
 * hard-coded inside AttendanceService.
 *
 * duplicateWindow: a second scan inside this window of the LOGIN time is ignored; a scan after
 *                  it records LOGOUT (the unusual rule required by this project).
 * reLoginCooldown: a scan this soon after a LOGOUT is ignored so one gesture cannot immediately
 *                  start a new session. Set to zero for the literal "next scan opens a session".
 * maxEligibilityAge: how old the campus check may be when attendance is written.
 */
public final class AttendancePolicy {
    public static final Duration DEFAULT_DUPLICATE_WINDOW = Duration.ofMinutes(15);
    public static final Duration DEFAULT_RELOGIN_COOLDOWN = Duration.ofSeconds(60);
    public static final Duration DEFAULT_ELIGIBILITY_AGE = Duration.ofSeconds(120);

    private final Duration duplicateWindow;
    private final Duration reLoginCooldown;
    private final Duration maxEligibilityAge;
    private final ZoneId zone;

    public AttendancePolicy(Duration duplicateWindow, Duration reLoginCooldown,
                            Duration maxEligibilityAge, ZoneId zone) {
        if (duplicateWindow == null || duplicateWindow.isZero() || duplicateWindow.isNegative())
            throw new IllegalArgumentException("duplicateWindow must be positive");
        if (reLoginCooldown == null || reLoginCooldown.isNegative())
            throw new IllegalArgumentException("reLoginCooldown must not be negative");
        if (maxEligibilityAge == null || maxEligibilityAge.isZero() || maxEligibilityAge.isNegative())
            throw new IllegalArgumentException("maxEligibilityAge must be positive");
        if (zone == null) throw new IllegalArgumentException("zone required");
        this.duplicateWindow = duplicateWindow;
        this.reLoginCooldown = reLoginCooldown;
        this.maxEligibilityAge = maxEligibilityAge;
        this.zone = zone;
    }

    public static AttendancePolicy defaults(ZoneId zone) {
        return new AttendancePolicy(DEFAULT_DUPLICATE_WINDOW, DEFAULT_RELOGIN_COOLDOWN,
                DEFAULT_ELIGIBILITY_AGE, zone);
    }

    public Duration duplicateWindow() { return duplicateWindow; }
    public Duration reLoginCooldown() { return reLoginCooldown; }
    public Duration maxEligibilityAge() { return maxEligibilityAge; }
    public ZoneId zone() { return zone; }
}
