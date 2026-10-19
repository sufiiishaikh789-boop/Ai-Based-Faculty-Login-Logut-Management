package com.example.facultyattendance.ui;

import com.example.facultyattendance.camera.FaceScanState;

/** User-facing wording for scan states. Kept in one place so tone and accuracy stay consistent. */
final class UiText {
    static final int INFO = 0, OK = 1, WARN = 2, ERROR = 3;

    private UiText() { }

    static String title(FaceScanState s, boolean enrolling) {
        switch (s) {
            case INITIALIZING: return "Starting camera…";
            case PERMISSION_REQUIRED: return "Permission needed";
            case LOCATING_CAMPUS: return "Checking campus eligibility…";
            case CAMPUS_REJECTED: return "Not eligible right now";
            case NOT_READY: return "Setup required";
            case SCANNING: return enrolling ? "Look at the camera" : "Look at the camera";
            case BLINK_REQUIRED: return "Blink once";
            case VERIFYING: return enrolling ? "Saving…" : "Verifying…";
            case NO_FACE: return "No face detected";
            case MULTIPLE_FACES: return "More than one face";
            case LOW_QUALITY: return "Adjust position or lighting";
            case MISMATCH: return "Face not recognised";
            case AMBIGUOUS: return "Could not be certain";
            case NO_ENROLLED_FACES: return "No enrolled faces";
            case ENROLLING: return "Capturing…";
            case SUCCESS: return "Done";
            case DUPLICATE: return "Already recorded";
            default: return "Something went wrong";
        }
    }

    static String detail(FaceScanState s) {
        switch (s) {
            case INITIALIZING: return "Preparing the face scanner.";
            case PERMISSION_REQUIRED: return "Camera access lets this device verify your face. Images are processed on the device and are not saved.";
            case LOCATING_CAMPUS: return "Looking for the campus Wi-Fi, or a precise GPS fix if Wi-Fi is not available.";
            case SCANNING: return "Keep your face inside the oval, facing forward, in even light.";
            case BLINK_REQUIRED: return "A quick blink confirms a live person. This is a basic check, not a guarantee against spoofing.";
            case VERIFYING: return "Hold still for a moment.";
            case NO_FACE: return "Move closer and face the camera.";
            case MULTIPLE_FACES: return "Only one person should be in view.";
            case LOW_QUALITY: return "Face the camera straight on in even lighting and fill about a third of the screen width.";
            case MISMATCH: return "This face does not match an enrolled faculty member.";
            case AMBIGUOUS: return "The result was too close between profiles. Try again in better light.";
            case NO_ENROLLED_FACES: return "Ask an administrator to enrol faculty faces first.";
            case ENROLLING: return "Hold still and keep looking at the camera.";
            default: return "";
        }
    }

    static int kind(FaceScanState s) {
        switch (s) {
            case SUCCESS: return OK;
            case CAMPUS_REJECTED: case NOT_READY: case MISMATCH: case AMBIGUOUS: case NO_ENROLLED_FACES:
            case MULTIPLE_FACES: case LOW_QUALITY: case NO_FACE: case DUPLICATE: case PERMISSION_REQUIRED:
                return WARN;
            case ERROR: return ERROR;
            default: return INFO;
        }
    }
}
