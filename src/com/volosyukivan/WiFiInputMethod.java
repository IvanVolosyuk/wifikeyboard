package com.volosyukivan;

import java.util.HashSet;

import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.inputmethodservice.InputMethodService;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.KeyEvent;
import android.view.inputmethod.InputConnection;

public class WiFiInputMethod extends InputMethodService {

  @Override
  public void onCreate() {
    super.onCreate();
    Debug.d("WiFiInputMethod started");
    if (this.bindService(new Intent(this, HttpService.class),
        serviceConnection, BIND_AUTO_CREATE) == false) {
      throw new RuntimeException("failed to connect to HttpService");
    }
  }
  
  private final ServiceConnection serviceConnection = new ServiceConnection() {
    //@Override
    public void onServiceConnected(ComponentName name, IBinder service) {
      Debug.d("WiFiInputMethod connected to HttpService.");
      try {
        RemoteKeyboard.Stub.asInterface(service).registerKeyListener(
            new RemoteKeyListener.Stub() {
              @Override
              public void keyEvent(int code, boolean pressed) throws RemoteException {
                // Debug.d("got key in WiFiInputMethod");
                receivedKey(code, pressed);
              }
            });
      } catch (RemoteException e) {
        throw new RuntimeException(
            "WiFiInputMethod failed to connected to HttpService.", e);
      }
    }
    //@Override
    public void onServiceDisconnected(ComponentName name) {
      Debug.d("WiFiInputMethod disconnected from HttpService.");
    }
  };
  
  HashSet<Integer> pressedKeys = new HashSet<Integer>();
  
  void receivedKey(int code, boolean pressed) {
    if (code == HttpServer.FOCUS) {
      for (int key : pressedKeys) {
        sendKey(key, false);
      }
      pressedKeys.clear();
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
    } else {
      pressedKeys.remove(code);
    }
    sendKey(code, pressed);
  }
  
  void sendKey(int code, boolean down) {
    InputConnection conn = getCurrentInputConnection();
    if (conn == null) {
      Debug.d("connection closed");
      return;
    }
    conn.sendKeyEvent(new KeyEvent(
        down ? KeyEvent.ACTION_DOWN : KeyEvent.ACTION_UP, code));
  }
}
