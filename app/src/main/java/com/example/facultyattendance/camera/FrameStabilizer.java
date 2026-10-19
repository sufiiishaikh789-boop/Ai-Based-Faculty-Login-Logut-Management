package com.example.facultyattendance.camera;

/** Requires the same identity on N consecutive analysed frames before it is trusted. */
public final class FrameStabilizer {
    private final int required;
    private String current;
    private int count;

    public FrameStabilizer(int required) { this.required = required; }

    public void reset() { current = null; count = 0; }

    /** @return true once the same non-null uid has been offered `required` times in a row. */
    public boolean offer(String uid) {
        if (uid == null) { reset(); return false; }
        if (uid.equals(current)) count++; else { current = uid; count = 1; }
        return count >= required;
    }
}
