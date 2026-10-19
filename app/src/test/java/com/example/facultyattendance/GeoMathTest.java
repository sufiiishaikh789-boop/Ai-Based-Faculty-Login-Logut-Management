package com.example.facultyattendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.facultyattendance.location.GeoMath;

import org.junit.Test;

public class GeoMathTest {
    @Test public void sameCoordinateIsZero() {
        assertEquals(0.0, GeoMath.distanceMeters(12.0, 77.0, 12.0, 77.0), 1e-9);
    }

    @Test public void oneHundredThousandthOfADegreeOfLatitudeIsAboutOnePointOneMetres() {
        assertEquals(1.1119, GeoMath.distanceMeters(0.0, 0.0, 0.00001, 0.0), 0.001);
    }

    @Test public void longitudeDistanceShrinksWithLatitude() {
        double eq = GeoMath.distanceMeters(0.0, 0.0, 0.0, 0.0001);
        double at60 = GeoMath.distanceMeters(60.0, 0.0, 60.0, 0.0001);
        assertEquals(11.119, eq, 0.01);
        assertEquals(eq / 2.0, at60, 0.05);
    }

    @Test public void knownLongDistanceIsCorrect() {
        // Paris to London is roughly 343-344 km.
        double d = GeoMath.distanceMeters(48.8566, 2.3522, 51.5074, -0.1278);
        assertTrue(String.valueOf(d), d > 340_000 && d < 347_000);
    }

    @Test public void distanceIsSymmetric() {
        assertEquals(GeoMath.distanceMeters(10, 20, 11, 21), GeoMath.distanceMeters(11, 21, 10, 20), 1e-6);
    }
}
