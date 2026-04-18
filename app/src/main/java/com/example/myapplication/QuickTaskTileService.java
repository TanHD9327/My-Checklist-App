package com.example.myapplication;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Intent;
import android.service.quicksettings.TileService;

public class QuickTaskTileService extends TileService {

    @SuppressLint("StartActivityAndCollapseDeprecated")
    @Override
    public void onClick() {
        super.onClick();
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        intent.putExtra("OPEN_ADD_TASK", true);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(pendingIntent);
        } else {
            // Dành cho các phiên bản Android cũ hơn
            startActivityAndCollapse(intent);
        }
    }
}
