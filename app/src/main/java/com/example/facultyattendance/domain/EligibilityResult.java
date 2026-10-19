package com.example.facultyattendance.domain;

import java.time.Instant;

/** Result of the campus check (trusted Wi-Fi, or GPS fallback). Carries no raw coordinates. */
public final class EligibilityResult {
    public enum Outcome {
        ELIGIBLE_WIFI, ELIGIBLE_GPS, DEMO_BYPASS,
        NOT_CONFIGURED, WIFI_NOT_TRUSTED, OUT_OF_RANGE, LOW_ACCURACY, STALE_LOCATION,
        PERMISSION_DENIED, APPROXIMATE_ONLY, LOCATION_DISABLED, LOCATION_TIMEOUT,
        MOCK_LOCATION, LOCATION_ERROR
    }

    private final Outcome outcome;
    private final String message;
    private final String evidence;
    private final Instant checkedAt;

    private EligibilityResult(Outcome outcome, String message, String evidence, Instant checkedAt) {
        this.outcome = outcome;
        this.message = message;
        this.evidence = evidence;
        this.checkedAt = checkedAt;
    }

    public static EligibilityResult eligibleWifi(String evidence, Instant at) {
        return new EligibilityResult(Outcome.ELIGIBLE_WIFI, "Connected to the campus network.", evidence, at);
    }

    public static EligibilityResult eligibleGps(String evidence, Instant at) {
        return new EligibilityResult(Outcome.ELIGIBLE_GPS, "Location is within the campus radius.", evidence, at);
    }

    public static EligibilityResult demoBypass(String reason, Instant at) {
        return new EligibilityResult(Outcome.DEMO_BYPASS, reason, "DEMO_BYPASS", at);
    }

    public static EligibilityResult rejected(Outcome outcome, String message, Instant at) {
        if (outcome == Outcome.ELIGIBLE_WIFI || outcome == Outcome.ELIGIBLE_GPS || outcome == Outcome.DEMO_BYPASS) {
            throw new IllegalArgumentException("not a rejection outcome");
        }
        return new EligibilityResult(outcome, message, null, at);
    }

    public Outcome outcome() { return outcome; }
    public String message() { return message; }
    public String evidence() { return evidence; }
    public Instant checkedAt() { return checkedAt; }
    public boolean isDemo() { return outcome == Outcome.DEMO_BYPASS; }
    public boolean isEligible() {
        return outcome == Outcome.ELIGIBLE_WIFI || outcome == Outcome.ELIGIBLE_GPS || outcome == Outcome.DEMO_BYPASS;
    }
}
