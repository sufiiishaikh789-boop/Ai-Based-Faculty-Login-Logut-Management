package com.example.facultyattendance.ui;

import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.facultyattendance.databinding.ActivityHistoryBinding;
import com.example.facultyattendance.domain.AttendanceRecord;
import com.example.facultyattendance.util.TimeFormat;
import com.google.android.material.datepicker.MaterialDatePicker;

import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** Administrator view of all attendance with search, date range, status filter and CSV export. */
public class HistoryActivity extends AdminBaseActivity {
    private static final int LIMIT = 500;

    private ActivityHistoryBinding b;
    private RowAdapter adapter;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private String fromDate;   // yyyy-MM-dd or null
    private String toDate;
    private int loadGeneration;
    private List<AttendanceRecord> lastResults = new ArrayList<>();

    private final ActivityResultLauncher<String> exportLauncher = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), this::writeExport);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityHistoryBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        adapter = new RowAdapter(null);
        b.historyList.setLayoutManager(new LinearLayoutManager(this));
        b.historyList.setAdapter(adapter);

        b.searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void onTextChanged(CharSequence s, int a, int c, int d) { }
            @Override public void afterTextChanged(Editable s) {
                handler.removeCallbacks(reload);
                handler.postDelayed(reload, 300);          // debounce typing
            }
        });
        b.statusChips.setOnCheckedStateChangeListener((group, ids) -> load());
        b.btnFromDate.setOnClickListener(v -> pickDate(true));
        b.btnToDate.setOnClickListener(v -> pickDate(false));
        b.btnClearDates.setOnClickListener(v -> { fromDate = null; toDate = null; updateDateButtons(); load(); });
        b.btnExport.setOnClickListener(v -> {
            String name = "attendance_" + DateTimeFormat.stamp(container.clock.instant()) + ".csv";
            exportLauncher.launch(name);
        });
        updateDateButtons();
    }

    private final Runnable reload = this::load;

    @Override protected void onAdminReady() { load(); }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    private String selectedState() {
        if (b.chipOpen.isChecked()) return "OPEN_SESSION";
        if (b.chipCompleted.isChecked()) return "COMPLETED";
        return null;
    }

    private void load() {
        final int gen = ++loadGeneration;
        final String q = String.valueOf(b.searchInput.getText());
        final String from = fromDate, to = toDate, state = selectedState();
        b.historyProgress.setVisibility(View.VISIBLE);
        container.io.execute(() -> {
            final List<AttendanceRecord> result = container.attendance.search(q, from, to, state, LIMIT);
            final ZoneId zone = container.settings.getAttendancePolicy().zone();
            ui(() -> {
                if (gen != loadGeneration) return;          // a newer query is already running
                b.historyProgress.setVisibility(View.GONE);
                lastResults = result;
                List<RowAdapter.Row> rows = new ArrayList<>();
                for (AttendanceRecord r : result) {
                    String sub = "In: " + TimeFormat.dateTime(r.loginAt(), zone) + "\nOut: "
                            + (r.logoutAt() == null ? "none (open session)" : TimeFormat.dateTime(r.logoutAt(), zone));
                    String badge = r.isOpen() ? "OPEN_SESSION" : (r.isCorrected() ? "CORRECTED" : "COMPLETED");
                    int kind = r.isOpen() ? RowAdapter.WARN : (r.isCorrected() ? RowAdapter.WARN : RowAdapter.OK);
                    if (r.isDemo()) { badge = "DEMO · " + badge; kind = RowAdapter.WARN; }
                    rows.add(new RowAdapter.Row(r.facultyName() + "  (" + r.facultyUid() + ")", sub, badge, kind, r));
                }
                adapter.submit(rows);
                b.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }

    private void pickDate(final boolean isFrom) {
        MaterialDatePicker<Long> picker = MaterialDatePicker.Builder.datePicker()
                .setTitleText(isFrom ? "From date" : "To date").build();
        picker.addOnPositiveButtonClickListener(sel -> {
            String d = Instant.ofEpochMilli(sel).atZone(ZoneOffset.UTC).toLocalDate().toString();
            if (isFrom) fromDate = d; else toDate = d;
            updateDateButtons();
            load();
        });
        picker.show(getSupportFragmentManager(), isFrom ? "from" : "to");
    }

    private void updateDateButtons() {
        b.btnFromDate.setText(fromDate == null ? "From date" : "From " + fromDate);
        b.btnToDate.setText(toDate == null ? "To date" : "To " + toDate);
    }

    private void writeExport(@Nullable final Uri uri) {
        if (uri == null) return;                              // user cancelled the file picker
        final List<AttendanceRecord> snapshot = new ArrayList<>(lastResults);
        container.io.execute(() -> {
            String message;
            try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                if (os == null) throw new IOException("no stream");
                ZoneId zone = container.settings.getAttendancePolicy().zone();
                container.attendance.writeCsv(os, snapshot, zone);
                container.attendance.recordAudit("EXPORT_CSV", "rows=" + snapshot.size(), container.clock.millis());
                message = "Exported " + snapshot.size() + " rows.";
            } catch (IOException | RuntimeException e) {
                message = "Export failed. Nothing was saved.";
            }
            final String m = message;
            ui(() -> toast(m));
        });
    }

    /** File-name timestamp helper kept local so it does not depend on the device locale. */
    private static final class DateTimeFormat {
        static String stamp(Instant i) {
            return DateTimeFormatter.ofPattern("yyyyMMdd_HHmm").withZone(ZoneOffset.UTC).format(i);
        }
    }
}
