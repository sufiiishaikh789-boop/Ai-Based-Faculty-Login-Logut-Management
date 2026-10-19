package com.example.facultyattendance.location;

/** Geographic helpers (no Android dependency so they can be unit tested on the JVM). */
public final class GeoMath {
    private static final double EARTH_RADIUS_M = 6_371_008.8; // IUGG mean radius

    private GeoMath() { }

    /** Great-circle distance using the haversine formula. */
    public static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double p1 = Math.toRadians(lat1);
        double p2 = Math.toRadians(lat2);
        double dp = Math.toRadians(lat2 - lat1);
        double dl = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dp / 2) * Math.sin(dp / 2)
                + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
