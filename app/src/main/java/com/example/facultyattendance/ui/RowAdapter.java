package com.example.facultyattendance.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.facultyattendance.R;
import com.example.facultyattendance.databinding.ItemRowBinding;

import java.util.ArrayList;
import java.util.List;

/** One reusable card-row adapter for faculty, attendance, open-session and audit lists. */
public final class RowAdapter extends RecyclerView.Adapter<RowAdapter.Holder> {
    public static final int NEUTRAL = 0, OK = 1, WARN = 2, ERROR = 3;

    public static final class Row {
        public final String title;
        public final String subtitle;
        public final String badge;
        public final int badgeKind;
        public final Object tag;

        public Row(String title, String subtitle, String badge, int badgeKind, Object tag) {
            this.title = title;
            this.subtitle = subtitle;
            this.badge = badge;
            this.badgeKind = badgeKind;
            this.tag = tag;
        }
    }

    public interface OnRowClick { void onClick(Row row); }

    private final List<Row> rows = new ArrayList<>();
    private final OnRowClick listener;

    public RowAdapter(OnRowClick listener) { this.listener = listener; }

    public void submit(List<Row> newRows) {
        rows.clear();
        rows.addAll(newRows);
        notifyDataSetChanged();
    }

    @NonNull @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        final Row row = rows.get(position);
        h.b.rowTitle.setText(row.title);
        h.b.rowSubtitle.setText(row.subtitle);
        h.b.rowSubtitle.setVisibility(row.subtitle == null || row.subtitle.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        if (row.badge == null || row.badge.isEmpty()) {
            h.b.rowBadge.setVisibility(android.view.View.GONE);
        } else {
            h.b.rowBadge.setVisibility(android.view.View.VISIBLE);
            h.b.rowBadge.setText(row.badge);
            int bg;
            switch (row.badgeKind) {
                case OK: bg = R.drawable.bg_badge_ok; break;
                case WARN: bg = R.drawable.bg_badge_warn; break;
                case ERROR: bg = R.drawable.bg_badge_error; break;
                default: bg = R.drawable.bg_badge_neutral; break;
            }
            h.b.rowBadge.setBackgroundResource(bg);
        }
        h.b.getRoot().setClickable(listener != null);
        h.b.getRoot().setOnClickListener(listener == null ? null : v -> listener.onClick(row));
    }

    @Override public int getItemCount() { return rows.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemRowBinding b;
        Holder(ItemRowBinding b) { super(b.getRoot()); this.b = b; }
    }
}
