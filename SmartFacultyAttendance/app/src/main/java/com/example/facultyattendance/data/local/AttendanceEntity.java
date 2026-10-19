package com.example.facultyattendance.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * One attendance session.
 *
 * openMarker is 1 while the session is open and NULL once it is closed. The UNIQUE index on
 * (facultyUid, openMarker) lets SQLite itself guarantee "at most one open session per faculty
 * member" (NULLs are distinct in unique indexes, so any number of closed sessions are allowed).
 */
@Entity(
        tableName = "attendance",
        foreignKeys = @ForeignKey(entity = FacultyEntity.class, parentColumns = "uid",
                childColumns = "facultyUid", onDelete = ForeignKey.RESTRICT),
        indices = {
                @Index(value = {"facultyUid", "openMarker"}, unique = true),
                @Index(value = {"facultyUid", "loginAtMillis"}),
                @Index(value = {"localDate"})
        })
public class AttendanceEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String facultyUid = "";
    @NonNull
    public String facultyName = "";
    /** yyyy-MM-dd in the configured zone, for efficient date filtering. */
    @NonNull
    public String localDate = "";
    public long loginAtMillis;
    @Nullable
    public Long logoutAtMillis;
    @NonNull
    public String status = "LOGGED_IN";
    @Nullable
    public Integer openMarker;
    @Nullable
    public String loginEvidence;
    @Nullable
    public String logoutEvidence;
    public boolean demo;
    public boolean corrected;
}
