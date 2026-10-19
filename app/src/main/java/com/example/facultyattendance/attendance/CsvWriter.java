package com.example.facultyattendance.attendance;

/**
 * Minimal RFC 4180 CSV helper with a spreadsheet formula-injection guard.
 * Cells beginning with = + - @ (or tab/CR) get a leading apostrophe so Excel/Sheets do not execute them.
 */
public final class CsvWriter {
    public static final String EOL = "\r\n";

    private CsvWriter() { }

    public static String escape(String value) {
        if (value == null) return "";
        String v = value;
        if (!v.isEmpty()) {
            char c = v.charAt(0);
            if (c == '=' || c == '+' || c == '-' || c == '@' || c == '\t' || c == '\r') v = "'" + v;
        }
        boolean needsQuotes = v.indexOf(',') >= 0 || v.indexOf('"') >= 0
                || v.indexOf('\n') >= 0 || v.indexOf('\r') >= 0;
        if (!needsQuotes) return v;
        return "\"" + v.replace("\"", "\"\"") + "\"";
    }

    public static String row(String... cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells[i]));
        }
        return sb.append(EOL).toString();
    }
}
