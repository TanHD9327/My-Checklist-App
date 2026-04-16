package com.example.myapplication;

import android.app.DatePickerDialog;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private ArrayList<Task> taskList;
    private ArrayList<Task> finishedList;
    private TaskAdapter adapter;
    private TaskAdapter finishedAdapter;
    private DatabaseHelper db;
    private boolean isDarkMode = false;
    private int openDialogType = 0; // 0: none, 1: settings, 2: theme, 3: language, 4: time format, 5: color picker
    private boolean is24HourFormat = true;
    private int appColorHue = 210; // Default blue
    private float appSaturation = 0.8f;
    private float appValue = 0.8f;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        is24HourFormat = prefs.getBoolean("is24Hour", true);
        isDarkMode = prefs.getBoolean("isDarkMode", false);
        appColorHue = prefs.getInt("appColorHue", 210);
        appSaturation = prefs.getFloat("appSaturation", 0.8f);
        appValue = prefs.getFloat("appValue", 0.8f);

        // Áp dụng trạng thái Dark Mode đã lưu
        AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);
        ListView listView = findViewById(R.id.listView);
        ListView listViewFinished = findViewById(R.id.listViewFinished);
        Button btnAdd = findViewById(R.id.btnAdd);
        ImageButton btnSettings = findViewById(R.id.btnSettings); 

        taskList = new ArrayList<>();
        finishedList = new ArrayList<>();
        
        adapter = new TaskAdapter(this, taskList, db, this::refreshData);
        finishedAdapter = new TaskAdapter(this, finishedList, db, this::refreshData);
        
        listView.setAdapter(adapter);
        listViewFinished.setAdapter(finishedAdapter);

        // Sắp xếp dữ liệu ngay sau khi khởi tạo adapter
        refreshData();
        applyAppColor();

        // Khôi phục trạng thái menu nếu có
        if (savedInstanceState != null) {
            openDialogType = savedInstanceState.getInt("openDialogType", 0);
            if (openDialogType == 1) showSettingsMenu(null);
            else if (openDialogType == 2) showDarkModeDialog();
            else if (openDialogType == 3) showLanguageDialog();
            else if (openDialogType == 4) showTimeFormatDialog();
            else if (openDialogType == 5) showColorPickerDialog();
        }

        // --- 1. THÊM TASK ---
        btnAdd.setOnClickListener(v -> showAddTaskDialog());

        // --- 2. MENU CÀI ĐẶT ---
        if (btnSettings != null) {
            btnSettings.setOnClickListener(this::showSettingsMenu);
        }
    }

    private void applyAppColor() {
        int color = Color.HSVToColor(new float[]{appColorHue, appSaturation, appValue});
        
        // Tính toán độ sáng của màu để quyết định màu chữ (Trắng hay Đen)
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        
        // Nếu ở Dark Mode và màu chọn là Xanh dương đậm, ta có thể cần ép màu Title sáng hơn
        int titleColor = color;
        if (isDarkMode && luminance < 0.3) {
            // Nếu màu quá tối trong chế độ tối, làm sáng màu Title lên một chút để dễ nhìn
            titleColor = Color.HSVToColor(new float[]{appColorHue, appSaturation * 0.7f, Math.max(appValue, 0.9f)});
        }

        int contrastColor = (luminance > 0.5) ? Color.BLACK : Color.WHITE;

        Button btnAdd = findViewById(R.id.btnAdd);
        if (btnAdd != null) {
            btnAdd.setBackgroundColor(color);
            btnAdd.setTextColor(contrastColor); // Đổi màu chữ trên nút cho dễ nhìn
        }
        
        TextView txtTitle = findViewById(R.id.txtAppTitle);
        if (txtTitle != null) {
            txtTitle.setTextColor(titleColor);
        }

        ImageButton btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) {
            btnSettings.setColorFilter(titleColor);
        }

        TextView txtFinishedHeader = findViewById(R.id.txtFinishedHeader);
        if (txtFinishedHeader != null) {
            txtFinishedHeader.setTextColor(titleColor);
        }

        // Đổi màu thanh trạng thái và icon trên đó
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(color);
            
            // Nếu màu nền sáng, các icon (pin, giờ) phải màu tối và ngược lại
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                View decor = getWindow().getDecorView();
                if (luminance > 0.5) {
                    decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
                } else {
                    decor.setSystemUiVisibility(0);
                }
            }
        }

        if (adapter != null) {
            adapter.setAppColor(color);
            adapter.setIs24HourFormat(is24HourFormat);
            adapter.notifyDataSetChanged();
        }
        if (finishedAdapter != null) {
            finishedAdapter.setAppColor(color);
            finishedAdapter.setIs24HourFormat(is24HourFormat);
            finishedAdapter.notifyDataSetChanged();
        }
    }

    private void showAddTaskDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_add_task, null);
        EditText inputContent = dialogView.findViewById(R.id.editTaskContent);
        Button btnPickDate = dialogView.findViewById(R.id.btnPickDate);
        EditText editHour = dialogView.findViewById(R.id.editHour);
        EditText editMinute = dialogView.findViewById(R.id.editMinute);
        RadioGroup rgAmPm = dialogView.findViewById(R.id.rgAmPm);
        RadioButton rbAm = dialogView.findViewById(R.id.rbAm);
        RadioButton rbPm = dialogView.findViewById(R.id.rbPm);

        final Calendar calendar = Calendar.getInstance();
        final String[] selectedDate = {""};

        btnPickDate.setOnClickListener(v -> {
            new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
                selectedDate[0] = dayOfMonth + "/" + (month + 1) + "/" + year;
                btnPickDate.setText(selectedDate[0]);
            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show();
        });

        if (!is24HourFormat) {
            rgAmPm.setVisibility(View.VISIBLE);
            rbAm.setChecked(true);
        }

        builder.setView(dialogView)
                .setTitle(R.string.new_task_title)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null);

        AlertDialog dialog = builder.create();
        dialog.show();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String content = inputContent.getText().toString().trim();
            String hourStr = editHour.getText().toString().trim();
            String minuteStr = editMinute.getText().toString().trim();

            if (content.isEmpty()) {
                inputContent.setError("Content cannot be empty");
                return;
            }

            String finalTime = "";
            if (!hourStr.isEmpty()) {
                int h = Integer.parseInt(hourStr);
                int m = minuteStr.isEmpty() ? 0 : Integer.parseInt(minuteStr);

                boolean isValid = false;
                if (is24HourFormat) {
                    if (h >= 0 && h <= 23 && m >= 0 && m <= 59) {
                        isValid = true;
                        finalTime = String.format(Locale.US, "%02d:%02d", h, m);
                    }
                } else {
                    if (h >= 1 && h <= 12 && m >= 0 && m <= 59) {
                        isValid = true;
                        String amPm = rbAm.isChecked() ? "AM" : "PM";
                        finalTime = String.format(Locale.US, "%02d:%02d %s", h, m, amPm);
                    }
                }

                if (!isValid) {
                    Toast.makeText(this, R.string.invalid_time, Toast.LENGTH_SHORT).show();
                    return;
                }
            }

            String fullDate = selectedDate[0];
            if (!finalTime.isEmpty()) {
                fullDate = (fullDate.isEmpty() ? "" : fullDate + " ") + finalTime;
            }

            db.addTask(content, fullDate);
            refreshData();
            dialog.dismiss();
        });
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("openDialogType", openDialogType);
    }

    private void showSettingsMenu(View v) {
        openDialogType = 1;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings);

        String[] options = {
            getString(R.string.dark_mode), 
            getString(R.string.language),
            getString(R.string.time_format),
            getString(R.string.app_color)
        };
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                showDarkModeDialog();
            } else if (which == 1) {
                showLanguageDialog();
            } else if (which == 2) {
                showTimeFormatDialog();
            } else if (which == 3) {
                showColorPickerDialog();
            }
        });
        builder.setNegativeButton(R.string.done, (dialog, which) -> openDialogType = 0);
        builder.setOnCancelListener(dialog -> openDialogType = 0);
        builder.show();
    }

    private void showColorPickerDialog() {
        openDialogType = 5;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.app_color);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // 1. Vùng chọn Saturation/Value (Hình chữ nhật)
        final SaturationValueView svView = new SaturationValueView(this);
        LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 400);
        svView.setLayoutParams(svParams);
        svView.setHue(appColorHue);
        svView.setSaturationValue(appSaturation, appValue);
        layout.addView(svView);

        // 2. Thanh trượt chọn Hue
        TextView txtHue = new TextView(this);
        txtHue.setText(getString(R.string.hue));
        txtHue.setPadding(0, 20, 0, 0);
        layout.addView(txtHue);

        SeekBar hueSeekBar = new SeekBar(this);
        hueSeekBar.setMax(360);
        hueSeekBar.setProgress(appColorHue);
        layout.addView(hueSeekBar);

        // 3. Ô xem trước kết quả
        final View preview = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 80);
        lp.setMargins(0, 20, 0, 0);
        preview.setLayoutParams(lp);
        preview.setBackgroundColor(Color.HSVToColor(new float[]{appColorHue, appSaturation, appValue}));
        layout.addView(preview);

        // Sự kiện khi thay đổi Saturation/Value
        svView.setOnColorChangedListener((s, v) -> {
            appSaturation = s;
            appValue = v;
            preview.setBackgroundColor(Color.HSVToColor(new float[]{appColorHue, appSaturation, appValue}));
            prefs.edit().putFloat("appSaturation", appSaturation).putFloat("appValue", appValue).apply();
            applyAppColor();
        });

        // Sự kiện khi thay đổi Hue
        hueSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                appColorHue = progress;
                svView.setHue(appColorHue);
                preview.setBackgroundColor(Color.HSVToColor(new float[]{appColorHue, appSaturation, appValue}));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("appColorHue", appColorHue).apply();
                applyAppColor();
            }
        });

        builder.setView(layout);
        builder.setNegativeButton(R.string.back, (dialog, which) -> showSettingsMenu(null));
        builder.setPositiveButton(R.string.done, (dialog, which) -> openDialogType = 0);
        builder.setOnCancelListener(dialog -> openDialogType = 0);
        builder.show();
    }

    private void showTimeFormatDialog() {
        openDialogType = 4;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.time_format);

        RadioGroup group = new RadioGroup(this);
        group.setPadding(50, 20, 50, 20);

        RadioButton rb12h = new RadioButton(this);
        rb12h.setText(R.string.time_12h);
        rb12h.setId(View.generateViewId());
        group.addView(rb12h);

        RadioButton rb24h = new RadioButton(this);
        rb24h.setText(R.string.time_24h);
        rb24h.setId(View.generateViewId());
        group.addView(rb24h);

        if (is24HourFormat) rb24h.setChecked(true);
        else rb12h.setChecked(true);

        builder.setView(group);

        group.setOnCheckedChangeListener((rg, checkedId) -> {
            is24HourFormat = (checkedId == rb24h.getId());
            prefs.edit().putBoolean("is24Hour", is24HourFormat).apply();
            applyAppColor(); // Cập nhật lại giao diện ngay khi đổi định dạng
        });

        builder.setNegativeButton(R.string.back, (dialog, which) -> showSettingsMenu(null));
        builder.setPositiveButton(R.string.done, (dialog, which) -> openDialogType = 0);
        builder.setOnCancelListener(dialog -> openDialogType = 0);
        builder.show();
    }

    private void showDarkModeDialog() {
        openDialogType = 2;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.theme_title);

        RadioGroup group = new RadioGroup(this);
        group.setPadding(50, 20, 50, 20);

        RadioButton rbLight = new RadioButton(this);
        rbLight.setText(R.string.light_theme);
        rbLight.setId(View.generateViewId());
        group.addView(rbLight);

        RadioButton rbDark = new RadioButton(this);
        rbDark.setText(R.string.dark_theme);
        rbDark.setId(View.generateViewId());
        group.addView(rbDark);

        if (isDarkMode) rbDark.setChecked(true);
        else rbLight.setChecked(true);

        builder.setView(group);

        group.setOnCheckedChangeListener((rg, checkedId) -> {
            boolean newMode = (checkedId == rbDark.getId());
            if (newMode != isDarkMode) {
                isDarkMode = newMode;
                prefs.edit().putBoolean("isDarkMode", isDarkMode).apply();
                AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                        AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        builder.setNegativeButton(R.string.back, (dialog, which) -> showSettingsMenu(null));
        builder.setPositiveButton(R.string.done, (dialog, which) -> openDialogType = 0);
        builder.setOnCancelListener(dialog -> openDialogType = 0);
        builder.show();
    }

    private void showLanguageDialog() {
        openDialogType = 3;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.lang_title);

        // Tạo layout cho RadioGroup
        RadioGroup group = new RadioGroup(this);
        group.setPadding(50, 20, 50, 20);

        RadioButton rbVi = new RadioButton(this);
        rbVi.setText("Tiếng Việt");
        rbVi.setId(View.generateViewId());
        group.addView(rbVi);

        RadioButton rbEn = new RadioButton(this);
        rbEn.setText("English");
        rbEn.setId(View.generateViewId());
        group.addView(rbEn);

        // Kiểm tra ngôn ngữ hiện tại để tích sẵn
        String currentLang = Locale.getDefault().getLanguage();
        if (currentLang.equals("vi")) rbVi.setChecked(true);
        else rbEn.setChecked(true);

        builder.setView(group);

        // Thay đổi ngôn ngữ ngay lập tức khi chọn
        group.setOnCheckedChangeListener((rg, checkedId) -> {
            String newLang = (checkedId == rbVi.getId()) ? "vi" : "en";
            if (!newLang.equals(Locale.getDefault().getLanguage())) {
                setLocale(newLang);
                // setLocale có lệnh recreate(), onCreate sẽ mở lại dialog
            }
        });

        builder.setNegativeButton(R.string.back, (dialog, which) -> showSettingsMenu(null));
        builder.setPositiveButton(R.string.done, (dialog, which) -> openDialogType = 0);
        builder.setOnCancelListener(dialog -> openDialogType = 0);
        builder.show();
    }

    private void setLocale(String langCode) {
        Locale locale = new Locale(langCode);
        Locale.setDefault(locale);
        Resources res = getResources();
        Configuration config = res.getConfiguration();
        config.setLocale(locale);
        res.updateConfiguration(config, res.getDisplayMetrics());
        recreate(); // Khởi động lại Activity để áp dụng ngôn ngữ
    }

    private void refreshData() {
        // Xóa các task đã hoàn thành quá 7 ngày
        long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
        db.deleteOldFinishedTasks(sevenDaysAgo);

        ArrayList<Task> allTasks = db.getAllTasksAsObjects();
        taskList.clear();
        finishedList.clear();

        for (Task t : allTasks) {
            if (t.isDone == 1) {
                finishedList.add(t);
            } else {
                taskList.add(t);
            }
        }
        
        Comparator<Task> taskComparator = new Comparator<Task>() {
            SimpleDateFormat sdf12 = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US);
            SimpleDateFormat sdf24 = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
            SimpleDateFormat sdfDateOnly = new SimpleDateFormat("dd/MM/yyyy", Locale.US);

            @Override
            public int compare(Task t1, Task t2) {
                Date d1 = parseDate(t1.date);
                Date d2 = parseDate(t2.date);

                if (d1 == null && d2 == null) return 0;
                if (d1 == null) return 1;
                if (d2 == null) return -1;
                return d1.compareTo(d2);
            }

            private Date parseDate(String dateStr) {
                if (dateStr == null || dateStr.isEmpty()) return null;
                try {
                    if (dateStr.contains("AM") || dateStr.contains("PM")) {
                        return sdf12.parse(dateStr);
                    } else if (dateStr.contains(":")) {
                        return sdf24.parse(dateStr);
                    } else {
                        return sdfDateOnly.parse(dateStr);
                    }
                } catch (ParseException e) {
                    return null;
                }
            }
        };

        Collections.sort(taskList, taskComparator);
        Collections.sort(finishedList, taskComparator);

        TextView txtFinishedHeader = findViewById(R.id.txtFinishedHeader);
        ListView listViewFinished = findViewById(R.id.listViewFinished);
        if (finishedList.isEmpty()) {
            txtFinishedHeader.setVisibility(View.GONE);
            listViewFinished.setVisibility(View.GONE);
        } else {
            txtFinishedHeader.setVisibility(View.VISIBLE);
            listViewFinished.setVisibility(View.VISIBLE);
        }

        adapter.notifyDataSetChanged();
        finishedAdapter.notifyDataSetChanged();
    }
}