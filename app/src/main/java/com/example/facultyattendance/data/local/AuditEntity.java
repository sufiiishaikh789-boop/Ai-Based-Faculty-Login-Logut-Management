package com.example.facultyattendance.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "audit", indices = {@Index("atMillis")})
public class AuditEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    @NonNull
    public String action = "";
    @Nullable
    public Long recordId;
    @Nullable
    public String targetUid;
    public long atMillis;
    @NonNull
    public String actor = "";
    @Nullable
    public String reason;
    @Nullable
    public String details;
}
