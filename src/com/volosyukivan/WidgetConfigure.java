package com.volosyukivan;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
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
        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(WidgetConfigure.this);
        updateWidget(
            WidgetConfigure.this, appWidgetManager, mAppWidgetId,
            enabled.isChecked(), editText.getText().toString());
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
  
  static void updateWidget(
      Context context, AppWidgetManager appWidgetManager, int id,
      boolean textEnable, String text) {
    RemoteViews view = new RemoteViews(context.getClass().getPackage().getName(), R.layout.widget);
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
