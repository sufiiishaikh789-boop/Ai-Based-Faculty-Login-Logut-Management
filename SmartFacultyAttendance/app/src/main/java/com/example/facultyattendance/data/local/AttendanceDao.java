package com.example.facultyattendance.data.local;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AttendanceDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    long insert(AttendanceEntity entity);

    @Update
    int update(AttendanceEntity entity);

    @Query("SELECT * FROM attendance WHERE id = :id")
    AttendanceEntity get(long id);

    @Query("SELECT * FROM attendance WHERE facultyUid = :uid AND openMarker = 1 LIMIT 1")
    AttendanceEntity findOpen(String uid);

    @Query("SELECT * FROM attendance WHERE facultyUid = :uid AND logoutAtMillis IS NOT NULL "
            + "ORDER BY logoutAtMillis DESC LIMIT 1")
    AttendanceEntity findLatestClosed(String uid);

    @Query("SELECT * FROM attendance WHERE facultyUid = :uid ORDER BY loginAtMillis DESC LIMIT :limit")
    List<AttendanceEntity> recent(String uid, int limit);

    @Query("SELECT * FROM attendance WHERE facultyUid = :uid AND localDate = :date ORDER BY loginAtMillis ASC")
    List<AttendanceEntity> forDate(String uid, String date);

    @Query("SELECT * FROM attendance WHERE openMarker = 1 ORDER BY loginAtMillis ASC")
    List<AttendanceEntity> openSessions();

    /**
     * Filters are all optional (pass null to skip). `state` is OPEN_SESSION or COMPLETED.
     * The search text must already have LIKE wildcards escaped with a backslash.
     */
    @Query("SELECT * FROM attendance WHERE "
            + "(:q IS NULL OR facultyUid LIKE '%' || :q || '%' ESCAPE '\\' OR facultyName LIKE '%' || :q || '%' ESCAPE '\\') "
            + "AND (:fromDate IS NULL OR localDate >= :fromDate) "
            + "AND (:toDate IS NULL OR localDate <= :toDate) "
            + "AND (:state IS NULL OR (:state = 'OPEN_SESSION' AND openMarker = 1) "
            + "     OR (:state = 'COMPLETED' AND openMarker IS NULL)) "
            + "ORDER BY loginAtMillis DESC LIMIT :limit")
    List<AttendanceEntity> search(String q, String fromDate, String toDate, String state, int limit);

    @Query("SELECT COUNT(*) FROM attendance")
    int count();

    @Query("DELETE FROM attendance WHERE demo = 1")
    int deleteDemo();
}
