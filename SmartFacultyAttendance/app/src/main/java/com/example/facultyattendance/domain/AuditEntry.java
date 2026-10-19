package com.example.facultyattendance.domain;

import java.time.Instant;

/** One line of the administrator audit trail. */
public final class AuditEntry {
    private final long id;
    private final String action;
    private final Long recordId;      // nullable
    private final String targetUid;   // nullable
    private final Instant at;
    private final String actor;
    private final String reason;
    private final String details;

    public AuditEntry(long id, String action, Long recordId, String targetUid, Instant at,
                      String actor, String reason, String details) {
        this.id = id;
        this.action = action;
        this.recordId = recordId;
        this.targetUid = targetUid;
        this.at = at;
        this.actor = actor;
        this.reason = reason;
        this.details = details;
    }

    public long id() { return id; }
    public String action() { return action; }
    public Long recordId() { return recordId; }
    public String targetUid() { return targetUid; }
    public Instant at() { return at; }
    public String actor() { return actor; }
    public String reason() { return reason; }
    public String details() { return details; }
}
