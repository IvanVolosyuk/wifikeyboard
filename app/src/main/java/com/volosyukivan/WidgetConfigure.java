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

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.RemoteViews;
import android.widget.CompoundButton.OnCheckedChangeListener;

public class WidgetConfigure extends Activity {
  private static final String PREFS_WIDGETS = "widgets";
  
  private int mAppWidgetId;
  
  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Intent intent = getIntent();
    Bundle extras = intent.getExtras();
    if (extras != null) {
        mAppWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID, 
                AppWidgetManager.INVALID_APPWIDGET_ID);
    }
    
    setContentView(R.layout.configure);
    final EditText editText = (EditText) findViewById(R.id.text);
    final CheckBox enabled = (CheckBox) findViewById(R.id.enabled);
    Button ok = (Button) findViewById(R.id.ok);
    Button cancel = (Button) findViewById(R.id.cancel);
    
    ok.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        SharedPreferences prefs = getSharedPreferences(PREFS_WIDGETS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(textEnableProperty(mAppWidgetId), enabled.isChecked());
        editor.putString(textStringProperty(mAppWidgetId), editText.getText().toString());
        editor.commit();
        WidgetProvider.log(WidgetConfigure.this, "Widget " + mAppWidgetId + " configured");

        
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(WidgetConfigure.this);
        updateWidget(
            WidgetConfigure.this, appWidgetManager, mAppWidgetId);
        Intent resultValue = new Intent();
        resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
        setResult(RESULT_OK, resultValue);
        finish();
      }
    });
    cancel.setOnClickListener(new OnClickListener() {
      @Override
      public void onClick(View v) {
        finish();
      }
    });
    
    enabled.setOnCheckedChangeListener(new OnCheckedChangeListener() {
      @Override
      public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        editText.setEnabled(isChecked);
      }
    });
    
    Intent resultValue = new Intent();
    resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
    setResult(RESULT_CANCELED, resultValue);
  }
  
  static String textEnableProperty(int id) {
    return "textEnable:" + Integer.toString(id);
  }
  
  static String textStringProperty(int id) {
    return "textString:" + Integer.toString(id);
  }
  
  static void deleteWidget(Context context, int id) {
    SharedPreferences.Editor editor =
      context.getSharedPreferences(PREFS_WIDGETS, Context.MODE_PRIVATE).edit();
    editor.remove(textEnableProperty(id));
    editor.remove(textStringProperty(id));
    editor.commit();
  }
  
  static void updateWidget(
      Context context, AppWidgetManager appWidgetManager, int id) {
    SharedPreferences prefs =
      context.getSharedPreferences(PREFS_WIDGETS, Context.MODE_PRIVATE);
    boolean textEnable = prefs.getBoolean(textEnableProperty(id), true);
    String text = prefs.getString(textStringProperty(id), "WiFiKeyboard");
    RemoteViews view = new RemoteViews("com.volosyukivan", R.layout.widget);
    if (!textEnable) view.setViewVisibility(R.id.text, View.GONE);
    else view.setTextViewText(R.id.text, text);
    view.setImageViewResource(R.id.icon, R.drawable.icon);
    Intent intent = new Intent(context, WidgetActivity.class);
    PendingIntent pendingIntent =
    PendingIntent.getActivity(context, 0, intent,
        Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);

    // FIXME: not icon, but all widget
    view.setOnClickPendingIntent(R.id.icon, pendingIntent);
    appWidgetManager.updateAppWidget(id, view);
  }
}
