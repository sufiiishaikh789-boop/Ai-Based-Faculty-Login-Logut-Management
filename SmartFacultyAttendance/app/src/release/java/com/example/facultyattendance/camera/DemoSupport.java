package com.example.facultyattendance.camera;

/**
 * RELEASE builds: demo support does not exist. DemoFaceEmbedder is compiled only into the debug
 * variant, so a release APK cannot contain a fake verifier even if someone edits stored settings.
 */
public final class DemoSupport {
    private DemoSupport() { }

    public static boolean isAvailable() { return false; }

    public static FaceEmbedder createEmbedder() { return null; }
}
