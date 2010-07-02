package com.volosyukivan;

import android.util.Log;

public class Debug {
  public static void d(String msg) {
    Log.d("wifikeyboard", msg);
  }
  
  public static void e(String msg, Throwable e) {
    Log.e("wifikeyboard", msg, e);
  }
}