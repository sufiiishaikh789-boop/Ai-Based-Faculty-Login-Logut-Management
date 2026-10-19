package com.example.facultyattendance.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.facultyattendance.R;
import com.example.facultyattendance.databinding.ActivityListBinding;
import com.example.facultyattendance.domain.FacultyRecord;

import java.util.ArrayList;
import java.util.List;

public class FacultyListActivity extends AdminBaseActivity {
    private ActivityListBinding b;
    private RowAdapter adapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        b = ActivityListBinding.inflate(getLayoutInflater());
        setRoot(b.getRoot());
        b.listTitle.setText(R.string.faculty_list_title);
        b.btnPrimary.setText(R.string.btn_add_faculty);
        b.btnPrimary.setVisibility(View.VISIBLE);
        b.btnPrimary.setOnClickListener(v -> startActivity(new Intent(this, FacultyEditActivity.class)));
        adapter = new RowAdapter(row -> {
            Intent i = new Intent(this, FacultyEditActivity.class);
            i.putExtra(FacultyEditActivity.EXTRA_UID, ((FacultyRecord) row.tag).uid());
            startActivity(i);
        });
        b.listView.setLayoutManager(new LinearLayoutManager(this));
        b.listView.setAdapter(adapter);
    }

    @Override
    protected void onAdminReady() {
        container.io.execute(() -> {
            final List<FacultyRecord> list = container.faculty.list();
            ui(() -> {
                List<RowAdapter.Row> rows = new ArrayList<>();
                for (FacultyRecord f : list) {
                    String badge;
                    int kind;
                    if (!f.isActive()) { badge = "INACTIVE"; kind = RowAdapter.NEUTRAL; }
                    else if (f.hasTemplate()) { badge = f.isTemplateDemo() ? "DEMO FACE" : "FACE ENROLLED"; kind = f.isTemplateDemo() ? RowAdapter.WARN : RowAdapter.OK; }
                    else { badge = "NO FACE"; kind = RowAdapter.WARN; }
                    rows.add(new RowAdapter.Row(f.name(), f.uid() + (f.department().isEmpty() ? "" : "  ·  " + f.department()), badge, kind, f));
                }
                adapter.submit(rows);
                b.emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
            });
        });
    }
}
