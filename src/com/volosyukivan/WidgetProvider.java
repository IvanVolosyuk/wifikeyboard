package com.volosyukivan;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import android.widget.RemoteViews;

public class WidgetProvider extends AppWidgetProvider {
  @Override
  public void onDisabled(Context context) {
    super.onDisabled(context);
  }

  @Override
  public void onEnabled(Context context) {
    super.onEnabled(context);
  }
  
  @Override
  public void onUpdate(Context context, AppWidgetManager appWidgetManager,
      int[] appWidgetIds) {
    for (int id : appWidgetIds) {
    }
  }
}
