package com.volosyukivan;

import java.util.HashSet;

import com.volosyukivan.RemoteKeyListener.Stub;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class WiFiInputMethod extends InputMethodService {
  @Override
  public void onStartInput(EditorInfo attribute, boolean restarting) {
    super.onStartInput(attribute, restarting);
// FIXME: race condition: the server may not be running yet
//    try {
//      remoteKeyboard.notifyClient();
//    } catch (RemoteException e) {
//      Debug.e("failed communicating to HttpService", e);
//    }
  }

  ServiceConnection serviceConnection;
  private RemoteKeyboard remoteKeyboard;
  protected Stub keyboardListener;

  @Override
  public void onDestroy() {
//    Debug.d("WiFiInputMethod onDestroy()");
    try {
      if (remoteKeyboard != null)
        remoteKeyboard.unregisterKeyListener(keyboardListener);
    } catch (RemoteException e) {
//      Debug.d("Failed to unregister listener");
    }
    remoteKeyboard = null;
    if (serviceConnection != null)
      unbindService(serviceConnection);
    serviceConnection = null;
    super.onDestroy();
  }

  PowerManager.WakeLock wakeLock;
  HashSet<Integer> pressedKeys = new HashSet<Integer>();
  
  @Override
  public void onCreate() {
    super.onCreate();
    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
    wakeLock = pm.newWakeLock(
        PowerManager.SCREEN_BRIGHT_WAKE_LOCK | PowerManager.ON_AFTER_RELEASE, "wifikeyboard");
//    Debug.d("WiFiInputMethod started");
    serviceConnection = new ServiceConnection() {
      //@Override
      public void onServiceConnected(ComponentName name, IBinder service) {
//        Debug.d("WiFiInputMethod connected to HttpService.");
        try {
          remoteKeyboard = RemoteKeyboard.Stub.asInterface(service);
          keyboardListener = new RemoteKeyListener.Stub() {
            @Override
            public void keyEvent(int code, boolean pressed) throws RemoteException {
              // Debug.d("got key in WiFiInputMethod");
              receivedKey(code, pressed);
            }
            @Override
            public void charEvent(int code) throws RemoteException {
              // Debug.d("got key in WiFiInputMethod");
              receivedChar(code);
            }
          };
          RemoteKeyboard.Stub.asInterface(service).registerKeyListener(keyboardListener);
        } catch (RemoteException e) {
          throw new RuntimeException(
              "WiFiInputMethod failed to connected to HttpService.", e);
        }
      }
      //@Override
      public void onServiceDisconnected(ComponentName name) {
//        Debug.d("WiFiInputMethod disconnected from HttpService.");
      }
    }; 
    if (this.bindService(new Intent(this, HttpService.class),
        serviceConnection, BIND_AUTO_CREATE) == false) {
      throw new RuntimeException("failed to connect to HttpService");
    }
  }
  
  void receivedChar(int code) {
    wakeLock.acquire();
    wakeLock.release();
    InputConnection conn = getCurrentInputConnection();
    if (conn == null) {
//      Debug.d("connection closed");
      return;
    }
    String text = null; 
    if (code >= 0 && code <= 65535) {
      text = new String(new char[] { (char) code } );
    } else {
      int HI_SURROGATE_START = 0xD800;
      int LO_SURROGATE_START = 0xDC00;
      int hi = ((code >> 10) & 0x3FF) - 0x040 | HI_SURROGATE_START;
      int lo = LO_SURROGATE_START | (code & 0x3FF);
      text = new String(new char[] { (char) hi, (char) lo } );
    }
    conn.commitText(text, 1);
  }
  
  void receivedKey(int code, boolean pressed) {
    if (code == KeyboardHttpServer.FOCUS) {
      for (int key : pressedKeys) {
        sendKey(key, false, false);
      }
      pressedKeys.clear();
      resetModifiers();
      return;
    }
    if (pressedKeys.contains(code) == pressed) {
      if (pressed == false) return;
      // ignore autorepeat on following keys
      switch (code) {
      case KeyEvent.KEYCODE_ALT_LEFT:
      case KeyEvent.KEYCODE_SHIFT_LEFT:
      case KeyEvent.KEYCODE_HOME:
      case KeyEvent.KEYCODE_MENU: return;
      }
    }
    if (pressed) {
      pressedKeys.add(code);
      sendKey(code, pressed, false);
    } else {
      pressedKeys.remove(code);
      sendKey(code, pressed, pressedKeys.isEmpty());
    }
  }
  
  void resetModifiers() {
    InputConnection conn = getCurrentInputConnection();
    if (conn == null) {
      return;
    }
    conn.clearMetaKeyStates(
        KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON | KeyEvent.META_SYM_ON);
  }
  
  private long lastWake;
  
  void sendKey(int code, boolean down, boolean resetModifiers) {
    long time = System.currentTimeMillis();
    if (time - lastWake > 5000) {
      wakeLock.acquire();
      wakeLock.release();
      lastWake = time;
    }
    InputConnection conn = getCurrentInputConnection();
    if (conn == null) {
//      Debug.d("connection closed");
      return;
    }
    conn.sendKeyEvent(new KeyEvent(
        down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code));
    if (resetModifiers) {
      conn.clearMetaKeyStates(
          KeyEvent.META_ALT_ON | KeyEvent.META_SHIFT_ON | KeyEvent.META_SYM_ON);
    }
  }
}
