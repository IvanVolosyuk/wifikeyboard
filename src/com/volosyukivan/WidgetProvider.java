package com.volosyukivan;

import java.util.Date;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

public class WidgetProvider extends AppWidgetProvider {
  @Override
  public void onDisabled(Context context) {
    super.onDisabled(context);
  }

  @Override
  public void onEnabled(Context context) {
    super.onEnabled(context);
  }
  
  public static void log(Context context, String msg) {
//    SharedPreferences prefs = context.getSharedPreferences("log", Context.MODE_PRIVATE);
//    SharedPreferences.Editor e = prefs.edit();
//    String log = prefs.getString("log", "");
//    Date date = new Date();
//    
//    log = log + "\n" + date.getHours() +":"+ date.getMinutes()+" " + msg;
//    e.putString("log", log);
//    e.commit();
//    Log.d("wifikeyboard", log);
  }
  
  @Override
  public void onUpdate(Context context, AppWidgetManager appWidgetManager,
      int[] appWidgetIds) {
    for (int id : appWidgetIds) {
      WidgetConfigure.updateWidget(context, appWidgetManager, id);
      log(context, "Widget " + id + " updated");
    }
  }
  
  @Override
  public void onDeleted(Context context, int[] appWidgetIds) {
    super.onDeleted(context, appWidgetIds);
    for (int id : appWidgetIds) {
      log(context, "Widget " + id + " deleted");
      WidgetConfigure.deleteWidget(context, id);
    }
  }
}
