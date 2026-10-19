package com.example.facultyattendance.data.local;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

/** Faculty row. The face template is stored ONLY as an AES-GCM ciphertext (Android Keystore key). */
@Entity(tableName = "faculty")
public class FacultyEntity {
    @PrimaryKey
    @NonNull
    public String uid = "";
    @NonNull
    public String name = "";
    @NonNull
    public String department = "";
    public boolean active;
    public long createdAtMillis;
    public long updatedAtMillis;
    /** 0 when no face is enrolled. */
    public long enrolledAtMillis;
    @Nullable
    public byte[] encryptedTemplate;
    @Nullable
    public String templateModelId;
    public boolean templateDemo;
}
