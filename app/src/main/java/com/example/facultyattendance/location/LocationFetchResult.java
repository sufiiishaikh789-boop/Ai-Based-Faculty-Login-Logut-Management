package com.example.facultyattendance.location;

public final class LocationFetchResult {
    public enum Status { OK, PERMISSION_DENIED, APPROXIMATE_ONLY, LOCATION_DISABLED, TIMEOUT, ERROR }

    private final Status status;
    private final LocationSnapshot snapshot;

    private LocationFetchResult(Status status, LocationSnapshot snapshot) {
        this.status = status;
        this.snapshot = snapshot;
    }

    public static LocationFetchResult ok(LocationSnapshot s) {
        if (s == null) throw new IllegalArgumentException("snapshot required");
        return new LocationFetchResult(Status.OK, s);
    }

    public static LocationFetchResult failure(Status status) {
        if (status == Status.OK) throw new IllegalArgumentException("use ok()");
        return new LocationFetchResult(status, null);
    }

    public Status status() { return status; }
    public LocationSnapshot snapshot() { return snapshot; }
}
