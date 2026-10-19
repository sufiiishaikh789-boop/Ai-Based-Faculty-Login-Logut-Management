package com.example.facultyattendance.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Decides the operating mode. DEMO is only possible in a build that contains the demo support
 * class (debug builds). A release build can never resolve to DEMO.
 */
public final class ModeResolver {
    public enum Mode { CONFIGURED, DEMO, NOT_READY }

    public static final class Decision {
        public final Mode mode;
        public final List<String> blockers;
        Decision(Mode mode, List<String> blockers) { this.mode = mode; this.blockers = blockers; }
    }

    private ModeResolver() { }

    public static Decision resolve(boolean demoSupportCompiledIn, boolean demoEnabled,
                                   boolean realModelReady, boolean campusConfigUsable) {
        if (demoSupportCompiledIn && demoEnabled) return new Decision(Mode.DEMO, new ArrayList<String>());
        List<String> blockers = new ArrayList<String>();
        if (!realModelReady) blockers.add("Face-recognition model is not installed (see docs/MODEL_SETUP.md).");
        if (!campusConfigUsable) blockers.add("Campus configuration is missing or invalid (Admin > Configuration).");
        return new Decision(blockers.isEmpty() ? Mode.CONFIGURED : Mode.NOT_READY, blockers);
    }
}
