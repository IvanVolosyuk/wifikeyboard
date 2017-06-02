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
