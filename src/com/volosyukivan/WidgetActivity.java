package com.volosyukivan;

import android.app.Activity;
import android.content.Context;
import android.view.inputmethod.InputMethodManager;

public class WidgetActivity extends Activity {
  @Override
  protected void onResume() {
    super.onResume();
    final InputMethodManager service =
      (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//    service.getEnabledInputMethodList();
//    service.setInputMethod(token, id);
//    new Handler().postDelayed(new Runnable() {
//      @Override
//      public void run() {
//      }
//    }, 100);
    service.showInputMethodPicker();
    finish();
  }
}
