package com.example.facultyattendance;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.example.facultyattendance.attendance.AttendanceCsvExporter;
import com.example.facultyattendance.attendance.CsvWriter;
import com.example.facultyattendance.domain.AttendanceRecord;

import org.junit.Test;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class CsvWriterTest {
    @Test public void plainValuesAreUntouched() { assertEquals("hello", CsvWriter.escape("hello")); }

    @Test public void commasQuotesAndLineBreaksAreQuotedAndEscaped() {
        assertEquals("\"a,b\"", CsvWriter.escape("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", CsvWriter.escape("say \"hi\""));
        assertEquals("\"line1\nline2\"", CsvWriter.escape("line1\nline2"));
        assertEquals("\"a\r\nb\"", CsvWriter.escape("a\r\nb"));
    }

    @Test public void nullBecomesEmptyAndFormulasAreNeutralised() {
        assertEquals("", CsvWriter.escape(null));
        assertEquals("'=SUM(A1)", CsvWriter.escape("=SUM(A1)"));
        assertEquals("'@cmd", CsvWriter.escape("@cmd"));
        assertEquals("\"'+1,2\"", CsvWriter.escape("+1,2"));
    }

    @Test public void exportShowsOpenSessionAndRealVersusDemo() {
        List<AttendanceRecord> list = new ArrayList<AttendanceRecord>();
        list.add(AttendanceRecord.newLogin("F-1", "Doe, \"Jane\"", "2026-03-02",
                Instant.parse("2026-03-02T04:00:00Z"), "WIFI:ssid_match", false).withId(7));
        String csv = AttendanceCsvExporter.toCsv(list, ZoneId.of("UTC"));
        String[] lines = csv.split("\r\n");
        assertEquals(2, lines.length);
        assertTrue(lines[0].startsWith("record_id,faculty_uid,"));
        assertTrue(lines[1], lines[1].contains("\"Doe, \"\"Jane\"\"\""));
        assertTrue(lines[1], lines[1].contains("OPEN_SESSION"));
        assertTrue(lines[1], lines[1].contains(",REAL,"));
        assertTrue(lines[1], lines[1].contains("2026-03-02T04:00Z"));
    }
}
