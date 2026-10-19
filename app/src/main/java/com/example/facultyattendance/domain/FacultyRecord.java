package com.example.facultyattendance.domain;

import java.time.Instant;

/** Faculty profile as seen by business logic. Never carries the face template itself. */
public final class FacultyRecord {
    private final String uid;
    private final String name;
    private final String department;
    private final boolean active;
    private final String templateModelId;   // null when no face is enrolled
    private final boolean templateDemo;     // true when the template came from the demo embedder
    private final Instant enrolledAt;       // null when no face is enrolled

    public FacultyRecord(String uid, String name, String department, boolean active,
                         String templateModelId, boolean templateDemo, Instant enrolledAt) {
        this.uid = uid;
        this.name = name;
        this.department = department;
        this.active = active;
        this.templateModelId = templateModelId;
        this.templateDemo = templateDemo;
        this.enrolledAt = enrolledAt;
    }

    public String uid() { return uid; }
    public String name() { return name; }
    public String department() { return department; }
    public boolean isActive() { return active; }
    public String templateModelId() { return templateModelId; }
    public boolean isTemplateDemo() { return templateDemo; }
    public Instant enrolledAt() { return enrolledAt; }
    public boolean hasTemplate() { return templateModelId != null; }

    /** UID rules: 3-32 chars, letters, digits, dot, dash, underscore. */
    public static boolean isValidUid(String uid) {
        return uid != null && uid.matches("[A-Za-z0-9._-]{3,32}");
    }
}
