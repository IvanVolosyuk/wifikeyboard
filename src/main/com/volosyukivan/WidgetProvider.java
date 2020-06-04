/**
 * WiFi Keyboard - Remote Keyboard for Android.
 * Copyright (C) 2011 Ivan Volosyuk
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
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
