package com.example.myapplication;

import android.app.DatePickerDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;
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
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private List<Task> taskList;
    private List<Task> finishedList;
    private TodoAdapter adapter;
    private TodoAdapter finishedAdapter;
    private DatabaseHelper db;
    private boolean isDarkMode = false;
    private int openDialogType = 0; // 0: none, 1: settings, 2: theme, 3: language, 4: time format, 5: color picker
    private boolean is24HourFormat = true;
    private int appColorHue = 210;
    private float appSaturation = 0.8f;
    private float appValue = 0.8f;
    private SharedPreferences prefs;

    private ActivityResultLauncher<String> exportLauncher;
    private ActivityResultLauncher<String> importLauncher;

    private int reminderMinutes = 10; // Mặc định nhắc trước 10 phút

    private static final SimpleDateFormat SDF_12 = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US);
    private static final SimpleDateFormat SDF_24 = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
    private static final SimpleDateFormat SDF_DATE_ONLY = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
    private static final SimpleDateFormat SDF_TIME_ONLY_12 = new SimpleDateFormat("hh:mm a", Locale.US);
    private static final SimpleDateFormat SDF_TIME_ONLY_24 = new SimpleDateFormat("HH:mm", Locale.US);

    private final BroadcastReceiver refreshReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if ("com.example.myapplication.REFRESH_DATA".equals(intent.getAction())) {
                refreshData();
            }
        }
    };

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        checkQuickTaskIntent(intent);
    }

    private void checkQuickTaskIntent(Intent intent) {
        if (intent != null && intent.getBooleanExtra("OPEN_ADD_TASK", false)) {
            intent.removeExtra("OPEN_ADD_TASK");
            showAddTaskDialog();
        }
    }

    private ActivityResultLauncher<String[]> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        
        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                    // Xử lý kết quả xin quyền nếu cần
                });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(new String[]{
                    android.Manifest.permission.POST_NOTIFICATIONS
            });
        }
        is24HourFormat = prefs.getBoolean("is24Hour", true);
        isDarkMode = prefs.getBoolean("isDarkMode", false);
        appColorHue = prefs.getInt("appColorHue", 210);
        appSaturation = prefs.getFloat("appSaturation", 0.8f);
        appValue = prefs.getFloat("appValue", 0.8f);
        reminderMinutes = prefs.getInt("reminderMinutes", 10);

        AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);

        super.onCreate(savedInstanceState);
        
        if (savedInstanceState != null) {
            overridePendingTransition(0, 0);
        }
        
        setContentView(R.layout.activity_main);

        db = new DatabaseHelper(this);
        ListView listView = findViewById(R.id.listView);
        ListView listViewFinished = findViewById(R.id.listViewFinished);
        Button btnAdd = findViewById(R.id.btnAdd);
        ImageButton btnSettings = findViewById(R.id.btnSettings); 

        taskList = new ArrayList<>();
        finishedList = new ArrayList<>();
        
        adapter = new TodoAdapter(this, taskList, db, this::refreshData);
        finishedAdapter = new TodoAdapter(this, finishedList, db, this::refreshData);
        
        adapter.setOnTaskDeletedListener(this::handleTaskDeletion);
        finishedAdapter.setOnTaskDeletedListener(this::handleTaskDeletion);

        TodoAdapter.OnSelectionChangedListener selectionListener = this::updateBulkActionButton;
        adapter.setOnSelectionChangedListener(selectionListener);
        finishedAdapter.setOnSelectionChangedListener(selectionListener);

        listView.setAdapter(adapter);
        listViewFinished.setAdapter(finishedAdapter);

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (!adapter.getSelectedIds().isEmpty() || !finishedAdapter.getSelectedIds().isEmpty()) {
                    exitSelectionMode();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            }
        });

        applyAppColor();
        checkQuickTaskIntent(getIntent());

        if (savedInstanceState != null) {
            openDialogType = savedInstanceState.getInt("openDialogType", 0);
            if (openDialogType > 0) {
                // Sử dụng postDelayed để đảm bảo UI ổn định hoàn toàn mới hiện Dialog và nạp data
                findViewById(android.R.id.content).postDelayed(() -> {
                    if (openDialogType == 1) showSettingsMenu(null);
                    else if (openDialogType == 2) showDarkModeDialog();
                    else if (openDialogType == 3) showLanguageDialog();
                    else if (openDialogType == 4) showTimeFormatDialog();
                    else if (openDialogType == 5) showColorPickerDialog();
                    else if (openDialogType == 6) showAddTaskDialog();
                    
                    // Sau khi Dialog hiện xong mới bắt đầu load data ngầm
                    refreshData();
                }, 100);
            } else {
                refreshData();
            }
        } else {
            refreshData();
        }

        exportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument("application/json"), uri -> {
            if (uri != null) {
                try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                    if (os != null) {
                        JSONArray array = db.exportTasksToJSON();
                        os.write(array.toString(4).getBytes());
                    }
                    Toast.makeText(this, R.string.export_success, Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Export failed", Toast.LENGTH_SHORT).show();
                }
            }
        });

        importLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                try (InputStream is = getContentResolver().openInputStream(uri)) {
                    if (is != null) {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                        StringBuilder sb = new StringBuilder();
                        String line;
                        while ((line = reader.readLine()) != null) sb.append(line);
                        JSONArray array = new JSONArray(sb.toString());
                        db.importTasksFromJSON(array);
                        refreshData();
                        Toast.makeText(this, R.string.import_success, Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    Toast.makeText(this, R.string.import_error, Toast.LENGTH_SHORT).show();
                }
            }
        });

        btnAdd.setOnClickListener(v -> showAddTaskDialog());

        findViewById(R.id.btnBulkUndone).setOnClickListener(v -> {
            List<Integer> selectedIds = new ArrayList<>(adapter.getSelectedIds());
            selectedIds.addAll(finishedAdapter.getSelectedIds());
            db.updateTasksStatus(selectedIds, 0, null);
            exitSelectionMode();
        });

        findViewById(R.id.btnBulkDone).setOnClickListener(v -> {
            List<Integer> selectedIds = new ArrayList<>(adapter.getSelectedIds());
            selectedIds.addAll(finishedAdapter.getSelectedIds());
            db.updateTasksStatus(selectedIds, 1, String.valueOf(System.currentTimeMillis()));
            exitSelectionMode();
        });

        findViewById(R.id.btnBulkDelete).setOnClickListener(v -> {
            List<Integer> selectedIds = new ArrayList<>(adapter.getSelectedIds());
            selectedIds.addAll(finishedAdapter.getSelectedIds());
            db.deleteTasks(selectedIds);
            exitSelectionMode();
        });

        findViewById(R.id.btnBulkCancel).setOnClickListener(v -> exitSelectionMode());

        if (btnSettings != null) {
            btnSettings.setOnClickListener(this::showSettingsMenu);
        }

        IntentFilter filter = new IntentFilter("com.example.myapplication.REFRESH_DATA");
        ContextCompat.registerReceiver(this, refreshReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(refreshReceiver);
        super.onDestroy();
    }

    private void exitSelectionMode() {
        adapter.setSelectionMode(false);
        finishedAdapter.setSelectionMode(false);
        updateBulkActionButton();
        refreshData();
    }

    private void updateBulkActionButton() {
        int count = adapter.getSelectedIds().size() + finishedAdapter.getSelectedIds().size();
        View layoutHeader = findViewById(R.id.layoutHeader);
        View layoutBulk = findViewById(R.id.layoutBulkActions);
        TextView txtCount = findViewById(R.id.txtSelectionCount);
        View btnDone = findViewById(R.id.btnBulkDone);
        View btnUndone = findViewById(R.id.btnBulkUndone);

        if (count > 0) {
            layoutHeader.setVisibility(View.GONE);
            layoutBulk.setVisibility(View.VISIBLE);
            txtCount.setText(String.format(Locale.getDefault(), getString(R.string.items_selected), count));
            
            boolean hasFinishedSelected = !finishedAdapter.getSelectedIds().isEmpty();
            boolean hasUnfinishedSelected = !adapter.getSelectedIds().isEmpty();
            
            btnDone.setVisibility(hasUnfinishedSelected ? View.VISIBLE : View.GONE);
            btnUndone.setVisibility(hasFinishedSelected ? View.VISIBLE : View.GONE);
        } else {
            layoutHeader.setVisibility(View.VISIBLE);
            layoutBulk.setVisibility(View.GONE);
        }
    }

    private void applyAppColor() {
        int color = Color.HSVToColor(new float[]{appColorHue, appSaturation, appValue});
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        
        int titleColor = color;
        if (isDarkMode && luminance < 0.3) {
            titleColor = Color.HSVToColor(new float[]{appColorHue, appSaturation * 0.7f, Math.max(appValue, 0.9f)});
        }

        int contrastColor = (luminance > 0.5) ? Color.BLACK : Color.WHITE;

        Button btnAdd = findViewById(R.id.btnAdd);
        if (btnAdd != null) {
            btnAdd.setBackgroundColor(color);
            btnAdd.setTextColor(contrastColor);
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

        // Apply to Bulk Actions layout
        View layoutBulk = findViewById(R.id.layoutBulkActions);
        if (layoutBulk != null) {
            android.graphics.drawable.GradientDrawable bulkBg = new android.graphics.drawable.GradientDrawable();
            bulkBg.setCornerRadius(10f);
            if (isDarkMode) {
                bulkBg.setColor(Color.argb(0x22, Color.red(color), Color.green(color), Color.blue(color)));
                bulkBg.setStroke(2, color);
            } else {
                bulkBg.setColor(Color.argb(0x11, Color.red(color), Color.green(color), Color.blue(color)));
                bulkBg.setStroke(2, Color.argb(0x44, Color.red(color), Color.green(color), Color.blue(color)));
            }
            layoutBulk.setBackground(bulkBg);
        }

        TextView txtSelectionCount = findViewById(R.id.txtSelectionCount);
        if (txtSelectionCount != null) {
            txtSelectionCount.setTextColor(titleColor);
        }

        ImageButton btnBulkCancel = findViewById(R.id.btnBulkCancel);
        if (btnBulkCancel != null) {
            btnBulkCancel.setColorFilter(titleColor);
        }

        getWindow().setStatusBarColor(color);
        WindowInsetsControllerCompat controller = new WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(luminance > 0.5);

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
        showTaskEditorDialog(null);
    }

    public void showTaskEditorDialog(Task taskToEdit) {
        boolean isEdit = (taskToEdit != null);
        openDialogType = 6;
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

        if (isEdit) {
            inputContent.setText(taskToEdit.content);
            if (taskToEdit.date != null && !taskToEdit.date.isEmpty()) {
                // Sơ bộ phân tách ngày và giờ từ chuỗi date
                String d = taskToEdit.date;
                if (d.contains("/")) {
                    String[] parts = d.split(" ");
                    selectedDate[0] = parts[0];
                    btnPickDate.setText(selectedDate[0]);
                    if (parts.length > 1) {
                        String timePart = parts[1];
                        if (timePart.contains(":")) {
                            String[] hm = timePart.split(":");
                            editHour.setText(hm[0]);
                            if (hm.length > 1) {
                                String min = hm[1];
                                if (d.contains("AM") || d.contains("PM")) {
                                    editMinute.setText(min.substring(0, 2));
                                    if (d.contains("PM")) rbPm.setChecked(true);
                                    else rbAm.setChecked(true);
                                } else {
                                    editMinute.setText(min);
                                }
                            }
                        }
                    }
                } else if (d.contains(":")) {
                    // Trường hợp chỉ có giờ không có ngày
                    String[] parts = d.split(" ");
                    String[] hm = parts[0].split(":");
                    editHour.setText(hm[0]);
                    if (hm.length > 1) {
                        String min = hm[1];
                        if (d.contains("AM") || d.contains("PM")) {
                            editMinute.setText(min);
                            if (d.contains("PM")) rbPm.setChecked(true);
                            else rbAm.setChecked(true);
                        } else {
                            editMinute.setText(min);
                        }
                    }
                }
            }
        }

        btnPickDate.setOnClickListener(v -> new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            selectedDate[0] = dayOfMonth + "/" + (month + 1) + "/" + year;
            btnPickDate.setText(selectedDate[0]);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show());

        if (!is24HourFormat) {
            rgAmPm.setVisibility(View.VISIBLE);
            if (!isEdit) rbAm.setChecked(true);
        }

        builder.setView(dialogView)
                .setTitle(isEdit ? R.string.edit_task_title : R.string.new_task_title)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, (dialog, which) -> openDialogType = 0);

        builder.setOnCancelListener(dialog -> openDialogType = 0);

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
            // Nếu không chọn ngày mà có nhập giờ, mặc định lấy ngày hôm nay
            if (fullDate.isEmpty() && !finalTime.isEmpty()) {
                Calendar c = Calendar.getInstance();
                fullDate = String.format(Locale.US, "%d/%d/%d", 
                    c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR));
            }

            if (!finalTime.isEmpty()) {
                fullDate = (fullDate.isEmpty() ? "" : fullDate + " ") + finalTime;
            }

            if (isEdit) {
                db.updateTask(taskToEdit.id, content, fullDate);
            } else {
                db.addTask(content, fullDate);
            }
            refreshData();
            openDialogType = 0;
            dialog.dismiss();
        });
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("openDialogType", openDialogType);
    }

    private void handleTaskDeletion(@NonNull Task task, int position) {
        if (task.isDone == 1) {
            finishedList.remove(task);
            finishedAdapter.notifyDataSetChanged();
        } else {
            taskList.remove(task);
            adapter.notifyDataSetChanged();
        }

        Snackbar snackbar = Snackbar.make(findViewById(R.id.listView), R.string.task_deleted, Snackbar.LENGTH_LONG);
        snackbar.setAction(R.string.undo, v -> refreshData());
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar transientBottomBar, int event) {
                if (event != DISMISS_EVENT_ACTION) {
                    db.deleteTask(task.id);
                }
            }
        });
        snackbar.show();
    }

    private void showSettingsMenu(View v) {
        openDialogType = 1;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.settings);
        String[] options = {
            getString(R.string.dark_mode), 
            getString(R.string.language),
            getString(R.string.time_format),
            getString(R.string.app_color),
            getString(R.string.reminder_settings),
            getString(R.string.export_data),
            getString(R.string.import_data)
        };
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) showDarkModeDialog();
            else if (which == 1) showLanguageDialog();
            else if (which == 2) showTimeFormatDialog();
            else if (which == 3) showColorPickerDialog();
            else if (which == 4) showReminderSettingDialog();
            else if (which == 5) exportLauncher.launch("checklist_backup.json");
            else if (which == 6) importLauncher.launch("application/json");
        });
        builder.setNegativeButton(R.string.done, (dialog, which) -> openDialogType = 0);
        builder.setOnCancelListener(dialog -> openDialogType = 0);
        builder.show();
    }

    private void showReminderSettingDialog() {
        String[] options = {
            getString(R.string.reminder_none),
            getString(R.string.reminder_mins, 10),
            getString(R.string.reminder_mins, 30),
            getString(R.string.reminder_days, 1),
            getString(R.string.reminder_days, 2),
            getString(R.string.reminder_custom)
        };
        int[] values = {0, 10, 30, 1440, 2880, -1}; // 1440 mins = 1 day, 2880 mins = 2 days
        
        int currentIndex = 1; // Default 10p
        boolean isCustom = true;
        for(int i=0; i<values.length-1; i++) {
            if(values[i] == reminderMinutes) { 
                currentIndex = i; 
                isCustom = false;
                break; 
            }
        }
        if (isCustom && reminderMinutes > 0) currentIndex = 5;
        else if (reminderMinutes == 0) currentIndex = 0;

        new AlertDialog.Builder(this)
            .setTitle(R.string.reminder_title)
            .setSingleChoiceItems(options, currentIndex, (d, which) -> {
                if (values[which] != -1) {
                    reminderMinutes = values[which];
                    prefs.edit().putInt("reminderMinutes", reminderMinutes).apply();
                    refreshData();
                    d.dismiss();
                } else {
                    d.dismiss();
                    showCustomReminderDialog();
                }
            })
            .setNegativeButton(R.string.back, (d, w) -> showSettingsMenu(null))
            .show();
    }

    private void showCustomReminderDialog() {
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(50, 20, 50, 0);

        EditText input = new EditText(this);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setHint(R.string.reminder_hint);
        
        Spinner unitSpinner = new Spinner(this);
        String[] units = {
            getString(R.string.unit_minutes),
            getString(R.string.unit_hours),
            getString(R.string.unit_days)
        };
        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this, android.R.layout.simple_spinner_dropdown_item, units);
        unitSpinner.setAdapter(adapter);

        // Nạp dữ liệu đã chỉnh sửa trước đó
        if (reminderMinutes > 0) {
            if (reminderMinutes % 1440 == 0) { // Nếu là tròn ngày
                input.setText(String.valueOf(reminderMinutes / 1440));
                unitSpinner.setSelection(2); // Ngày
            } else if (reminderMinutes % 60 == 0) { // Nếu là tròn giờ
                input.setText(String.valueOf(reminderMinutes / 60));
                unitSpinner.setSelection(1); // Giờ
            } else {
                input.setText(String.valueOf(reminderMinutes));
                unitSpinner.setSelection(0); // Phút
            }
        }

        container.addView(input);
        container.addView(unitSpinner);

        new AlertDialog.Builder(this)
            .setTitle(R.string.reminder_custom_title)
            .setMessage(R.string.reminder_custom_msg)
            .setView(container)
            .setPositiveButton(R.string.done, (d, w) -> {
                String val = input.getText().toString();
                if (!val.isEmpty()) {
                    int value = Integer.parseInt(val);
                    int unitPos = unitSpinner.getSelectedItemPosition();
                    
                    if (unitPos == 1) value *= 60;        // Hours to Mins
                    else if (unitPos == 2) value *= 1440; // Days to Mins
                    
                    reminderMinutes = value;
                    prefs.edit().putInt("reminderMinutes", reminderMinutes).apply();
                    refreshData();
                }
            })
            .setNegativeButton(R.string.back, (d, w) -> showReminderSettingDialog())
            .show();
    }

    private void showColorPickerDialog() {
        openDialogType = 5;
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.app_color);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        final SaturationValueView svView = new SaturationValueView(this);
        LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 400);
        svView.setLayoutParams(svParams);
        svView.setHue(appColorHue);
        svView.setSaturationValue(appSaturation, appValue);
        layout.addView(svView);

        SeekBar hueSeekBar = new SeekBar(this);
        hueSeekBar.setMax(360);
        hueSeekBar.setProgress(appColorHue);
        
        int[] colors = new int[361];
        for (int i = 0; i <= 360; i++) {
            colors[i] = Color.HSVToColor(new float[]{i, 1f, 1f});
        }
        android.graphics.drawable.GradientDrawable rainbowDepth = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, colors);
        rainbowDepth.setCornerRadius(10f);
        
        hueSeekBar.setPadding(30, 40, 30, 40);
        hueSeekBar.setBackground(rainbowDepth);
        hueSeekBar.setProgressDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
        hueSeekBar.setSplitTrack(false);

        layout.addView(hueSeekBar);

        final View preview = new View(this);
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 80);
        previewParams.topMargin = 20;
        preview.setLayoutParams(previewParams);
        preview.setBackgroundColor(Color.HSVToColor(new float[]{appColorHue, appSaturation, appValue}));
        layout.addView(preview);

        svView.setOnColorChangedListener((s, v) -> {
            appSaturation = s;
            appValue = v;
            preview.setBackgroundColor(Color.HSVToColor(new float[]{appColorHue, appSaturation, appValue}));
            prefs.edit().putFloat("appSaturation", appSaturation).putFloat("appValue", appValue).apply();
            applyAppColor();
        });

        hueSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                appColorHue = progress;
                svView.setHue(appColorHue);
                preview.setBackgroundColor(Color.HSVToColor(new float[]{appColorHue, appSaturation, appValue}));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                prefs.edit().putInt("appColorHue", appColorHue).apply();
                applyAppColor();
            }
        });

        builder.setView(layout);
        builder.setPositiveButton(R.string.done, (dialog, which) -> openDialogType = 0);
        builder.setNegativeButton(R.string.back, (dialog, which) -> showSettingsMenu(null));
        builder.show();
    }

    private void showTimeFormatDialog() {
        openDialogType = 4;
        String[] formats = {"24-hour", "12-hour (AM/PM)"};
        new AlertDialog.Builder(this)
                .setTitle(R.string.time_format)
                .setSingleChoiceItems(formats, is24HourFormat ? 0 : 1, (dialog, which) -> {
                    is24HourFormat = (which == 0);
                    prefs.edit().putBoolean("is24Hour", is24HourFormat).apply();
                    applyAppColor();
                })
                .setPositiveButton(R.string.done, (dialog, which) -> openDialogType = 0)
                .setNegativeButton(R.string.back, (dialog, which) -> showSettingsMenu(null))
                .show();
    }

    private void showDarkModeDialog() {
        openDialogType = 2;
        String[] themes = {getString(R.string.light_theme), getString(R.string.dark_theme)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.dark_mode)
                .setSingleChoiceItems(themes, isDarkMode ? 1 : 0, (d, which) -> {
                    isDarkMode = (which == 1);
                    prefs.edit().putBoolean("isDarkMode", isDarkMode).apply();
                    openDialogType = 2;
                    // Không gọi d.dismiss() ở đây để tránh chớp màn hình trước khi recreate
                    AppCompatDelegate.setDefaultNightMode(isDarkMode ?
                            AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
                })
                .setPositiveButton(R.string.done, (d, which) -> openDialogType = 0)
                .setNegativeButton(R.string.back, (d, which) -> showSettingsMenu(null))
                .show();
    }

    private void showLanguageDialog() {
        openDialogType = 3;
        String[] langs = {getString(R.string.lang_en), getString(R.string.lang_vi)};
        String currentLang = AppCompatDelegate.getApplicationLocales().toLanguageTags();
        int checkedItem = currentLang.contains("vi") ? 1 : 0;

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.language)
                .setSingleChoiceItems(langs, checkedItem, (d, which) -> {
                    openDialogType = 3;
                    setLocale(which == 0 ? "en" : "vi");
                })
                .setPositiveButton(R.string.done, (d, which) -> openDialogType = 0)
                .setNegativeButton(R.string.back, (d, which) -> showSettingsMenu(null))
                .create();
        
        // Tắt hiệu ứng hoạt họa của cửa sổ Dialog để tránh chớp khi nạp lại
        if (dialog.getWindow() != null) {
            dialog.getWindow().setWindowAnimations(0);
        }
        dialog.show();
    }

    private void setLocale(String langCode) {
        // Tắt hiệu ứng chuyển cảnh của Activity để mượt hơn khi đổi ngôn ngữ
        overridePendingTransition(0, 0);
        LocaleListCompat appLocale = LocaleListCompat.forLanguageTags(langCode);
        AppCompatDelegate.setApplicationLocales(appLocale);
    }

    private void refreshData() {
        // Chạy việc xóa task cũ trong một thread riêng để tránh lag UI
        new Thread(() -> {
            long sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000);
            db.deleteOldFinishedTasks(sevenDaysAgo);
        }).start();

        List<Task> allTasks = db.getAllTasksAsObjects();
        taskList.clear();
        finishedList.clear();
        for (Task t : allTasks) {
            if (t.isDone == 1) finishedList.add(t);
            else taskList.add(t);
        }
        
        Comparator<Task> taskComparator = new Comparator<Task>() {
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
                    if (dateStr.contains("/")) {
                        if (dateStr.contains("AM") || dateStr.contains("PM")) return SDF_12.parse(dateStr);
                        else if (dateStr.contains(":")) return SDF_24.parse(dateStr);
                        else return SDF_DATE_ONLY.parse(dateStr);
                    } else {
                        Date time;
                        if (dateStr.contains("AM") || dateStr.contains("PM")) time = SDF_TIME_ONLY_12.parse(dateStr);
                        else time = SDF_TIME_ONLY_24.parse(dateStr);
                        
                        if (time != null) {
                            Calendar now = Calendar.getInstance();
                            Calendar t = Calendar.getInstance();
                            t.setTime(time);
                            now.set(Calendar.HOUR_OF_DAY, t.get(Calendar.HOUR_OF_DAY));
                            now.set(Calendar.MINUTE, t.get(Calendar.MINUTE));
                            return now.getTime();
                        }
                    }
                } catch (ParseException e) { return null; }
                return null;
            }
        };

        taskList.sort(taskComparator);
        finishedList.sort(taskComparator);

        findViewById(R.id.txtFinishedHeader).setVisibility(finishedList.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.listViewFinished).setVisibility(finishedList.isEmpty() ? View.GONE : View.VISIBLE);

        adapter.notifyDataSetChanged();
        finishedAdapter.notifyDataSetChanged();
        updateBulkActionButton();
        
        // Update notifications whenever data changes
        TaskNotificationManager.updateNotification(this);
    }
}
