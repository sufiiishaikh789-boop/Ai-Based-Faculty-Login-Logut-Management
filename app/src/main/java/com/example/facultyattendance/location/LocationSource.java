package com.example.facultyattendance.location;

/** Blocking source of one GPS fix. Called on a background thread, never on the UI thread. */
public interface LocationSource {
    LocationFetchResult fetch();
}
