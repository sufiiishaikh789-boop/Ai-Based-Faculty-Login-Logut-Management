package com.example.facultyattendance.camera;

/** DEBUG builds only: exposes the demo embedder. The release source set has a stub that returns nothing. */
public final class DemoSupport {
    private DemoSupport() { }

    public static boolean isAvailable() { return true; }

    public static FaceEmbedder createEmbedder() { return new DemoFaceEmbedder(); }
}
