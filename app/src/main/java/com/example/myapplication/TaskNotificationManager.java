package com.example.myapplication;

import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public class TaskNotificationManager {
    private static final SimpleDateFormat SDF_12 = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.US);
    private static final SimpleDateFormat SDF_24 = new SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.US);
    private static final SimpleDateFormat SDF_DATE_ONLY = new SimpleDateFormat("dd/MM/yyyy", Locale.US);
    private static final SimpleDateFormat SDF_TIME_ONLY_12 = new SimpleDateFormat("hh:mm a", Locale.US);
    private static final SimpleDateFormat SDF_TIME_ONLY_24 = new SimpleDateFormat("HH:mm", Locale.US);

    public static void updateNotification(Context context) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        DatabaseHelper db = new DatabaseHelper(context);
        ArrayList<Task> allTasks = db.getAllTasksAsObjects();
        ArrayList<Task> unfinishedTasks = new ArrayList<>();
        for (Task t : allTasks) {
            if (t.isDone == 0) unfinishedTasks.add(t);
        }

        manager.cancelAll();
        if (unfinishedTasks.isEmpty()) return;

        boolean isNotifEnabled = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("isNotifEnabled", true);
        if (!isNotifEnabled) return;

        scheduleReminders(context, unfinishedTasks);
    }

    private static void scheduleReminders(Context context, ArrayList<Task> tasks) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        int reminderMinutes = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getInt("reminderMinutes", 10);
        
        for (Task task : tasks) {
            Date taskDate = parseDate(task.date);
            if (taskDate == null) continue;

            // 1. Lập lịch "Đúng giờ" (Luôn có nút Done)
            long dueTime = taskDate.getTime();
            if (dueTime > System.currentTimeMillis()) {
                Intent dueIntent = new Intent(context, ReminderReceiver.class);
                dueIntent.putExtra("task_id", task.id);
                dueIntent.putExtra("task_content", task.content);
                dueIntent.putExtra("is_on_time", true); // Đánh dấu đúng giờ
                
                PendingIntent pDue = PendingIntent.getBroadcast(
                        context, task.id, dueIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                setAlarm(alarmManager, dueTime, pDue);
            }

            // 2. Lập lịch "Nhắc trước" (Không có nút Done)
            if (reminderMinutes > 0) {
                long alarmTime = dueTime - (reminderMinutes * 60 * 1000);
                if (alarmTime > System.currentTimeMillis()) {
                    Intent remIntent = new Intent(context, ReminderReceiver.class);
                    remIntent.putExtra("task_id", task.id);
                    remIntent.putExtra("task_content", task.content);
                    remIntent.putExtra("is_on_time", false); // Đánh dấu là nhắc trước
                    
                    // Request code khác đi (ví dụ taskId + 20000) để không bị đè
                    PendingIntent pRem = PendingIntent.getBroadcast(
                            context, task.id + 20000, remIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    setAlarm(alarmManager, alarmTime, pRem);
                }
            }
        }
    }

    private static void setAlarm(AlarmManager am, long time, PendingIntent pi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi);
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, time, pi);
        }
    }

    private static Date parseDate(String dateStr) {
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
}
