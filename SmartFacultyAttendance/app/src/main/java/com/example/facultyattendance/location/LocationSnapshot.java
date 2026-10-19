package com.example.facultyattendance.location;

/** One GPS fix. Never persisted or logged. */
public final class LocationSnapshot {
    private final double latitude;
    private final double longitude;
    private final double accuracyMeters;
    private final long ageMillis;
    private final boolean mock;

    public LocationSnapshot(double latitude, double longitude, double accuracyMeters,
                            long ageMillis, boolean mock) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.accuracyMeters = accuracyMeters;
        this.ageMillis = ageMillis;
        this.mock = mock;
    }

    public double latitude() { return latitude; }
    public double longitude() { return longitude; }
    public double accuracyMeters() { return accuracyMeters; }
    public long ageMillis() { return ageMillis; }
    public boolean isMock() { return mock; }
}
