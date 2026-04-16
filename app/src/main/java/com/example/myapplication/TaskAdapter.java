package com.example.myapplication;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class TaskAdapter extends ArrayAdapter<Task> {
    private DatabaseHelper db;
    private List<Task> taskList;
    private int appColor = Color.parseColor("#3498db");
    private boolean is24HourFormat = true;
    private Runnable onStatusChangedListener;

    public TaskAdapter(Context context, List<Task> tasks, DatabaseHelper db, Runnable onStatusChangedListener) {
        super(context, 0, tasks);
        this.taskList = tasks;
        this.db = db;
        this.onStatusChangedListener = onStatusChangedListener;
    }

    public void setAppColor(int color) {
        this.appColor = color;
    }

    public void setIs24HourFormat(boolean is24Hour) {
        this.is24HourFormat = is24Hour;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.item_task, parent, false);
        }

        Task task = getItem(position);

        CheckBox cb = convertView.findViewById(R.id.todoCheckbox);
        TextView txtContent = convertView.findViewById(R.id.todoText);
        TextView txtDate = convertView.findViewById(R.id.todoDate);
        ImageButton btnDelete = convertView.findViewById(R.id.btnDelete);

        btnDelete.setColorFilter(Color.RED);

        txtContent.setText(task.content);
        txtDate.setText(formatDisplayDate(task.date));

        cb.setOnCheckedChangeListener(null);
        cb.setChecked(task.isDone == 1);
        updateStrikeThrough(txtContent, task.isDone == 1);

        cb.setOnCheckedChangeListener((buttonView, isChecked) -> {
            int status = isChecked ? 1 : 0;
            String finishDate = isChecked ? String.valueOf(System.currentTimeMillis()) : null;
            task.isDone = status;
            task.finishDate = finishDate;
            db.updateStatus(task.id, status, finishDate);
            updateStrikeThrough(txtContent, isChecked);
            if (onStatusChangedListener != null) {
                onStatusChangedListener.run();
            }
        });

        btnDelete.setOnClickListener(v -> {
            db.deleteTask(task.id);
            taskList.remove(position);
            notifyDataSetChanged();
        });

        return convertView;
    }

    private void updateStrikeThrough(TextView tv, boolean isDone) {
        if (isDone) {
            tv.setPaintFlags(tv.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
            tv.setAlpha(0.5f);
        } else {
            tv.setPaintFlags(tv.getPaintFlags() & (~Paint.STRIKE_THRU_TEXT_FLAG));
            tv.setAlpha(1.0f);
        }
    }

    private String formatDisplayDate(String dateStr) {
        if (dateStr == null || dateStr.isEmpty()) return "";

        // Các định dạng có thể có trong dữ liệu
        String[] patterns = {
            "d/M/yyyy h:mm a", "d/M/yyyy HH:mm",
            "h:mm a", "HH:mm",
            "d/M/yyyy"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setLenient(false);
                Date date = sdf.parse(dateStr);
                if (date != null) {
                    if (pattern.contains(":")) {
                        // Nếu có giờ, quyết định hiển thị theo 12h/24h
                        String outPattern;
                        if (pattern.contains("/")) {
                            outPattern = is24HourFormat ? "dd/MM/yyyy HH:mm" : "dd/MM/yyyy hh:mm a";
                        } else {
                            outPattern = is24HourFormat ? "HH:mm" : "hh:mm a";
                        }
                        return new SimpleDateFormat(outPattern, Locale.US).format(date);
                    } else {
                        // Chỉ có ngày
                        return new SimpleDateFormat("dd/MM/yyyy", Locale.US).format(date);
                    }
                }
            } catch (ParseException ignored) {}
        }
        return dateStr;
    }
}
