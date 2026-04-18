package com.example.myapplication;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NotificationReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if ("ACTION_DONE".equals(intent.getAction())) {
            int taskId = intent.getIntExtra("task_id", -1);
            if (taskId != -1) {
                DatabaseHelper db = new DatabaseHelper(context);
                // Cập nhật trạng thái hoàn thành
                db.updateStatus(taskId, 1, String.valueOf(System.currentTimeMillis()));
                
                // Cập nhật lại logic thông báo (để xóa các báo thức cũ của task này)
                TaskNotificationManager.updateNotification(context);
                
                // Gửi tín hiệu yêu cầu App cập nhật danh sách hiển thị
                Intent refreshIntent = new Intent("com.example.myapplication.REFRESH_DATA");
                refreshIntent.setPackage(context.getPackageName()); // Chỉ gửi trong nội bộ app
                refreshIntent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND); // Ưu tiên xử lý nhanh
                context.sendBroadcast(refreshIntent);
            }
        }
    }
}
