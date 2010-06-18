package com.volosyukivan;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class WiFiKeyboard extends Activity {
    int port = 7777;
    LinearLayout layout;
    
    private View createView() {
      ArrayList<String> addrs = new ArrayList<String>();
      try {
        Enumeration<NetworkInterface> ifaces =
          NetworkInterface.getNetworkInterfaces();
        while (ifaces.hasMoreElements()) {
          NetworkInterface iface = ifaces.nextElement();
          if ("lo".equals(iface.getName())) continue;
          Enumeration<InetAddress> addresses = iface.getInetAddresses();
          while (addresses.hasMoreElements()) {
            InetAddress addr = addresses.nextElement();
            addrs.add(addr.getHostAddress());
          }
        }
      } catch (SocketException e) {
        Debug.d("failed to get network interfaces");
      }

      ScrollView parent = new ScrollView(this);
      int fill = LinearLayout.LayoutParams.FILL_PARENT;
      int wrap = LinearLayout.LayoutParams.WRAP_CONTENT;
      LinearLayout.LayoutParams fillAll =
        new LinearLayout.LayoutParams(fill, fill);
      parent.setLayoutParams(fillAll);
      layout = new LinearLayout(this);
      layout.setOrientation(LinearLayout.VERTICAL);
      layout.setLayoutParams(new LinearLayout.LayoutParams(fill, wrap));
      parent.addView(layout);
      text("Setting up WiFiKeyboard:", 20);
      text("Go to Settings->Language & Keyboard");
      text("Enable there WiFiKeyboard");
      text("On any text input touch and hold for a second");
      text("Change 'InputMethod' to 'WiFiKeyboard'");
      text("");
      if (addrs.size() == 0) {
        text("Enable wifi or GPRS/3G", 20);
      } else if (addrs.size() == 1) {
        text("Connect your computer's browser to http://" + addrs.get(0) + ":" + port, 20);
      } else {
        text("Connect your computer's browser to any of:");
        for (String addr : addrs) {
          text("http://" + addr + ":" + port, 20);
        }
      }
      text("This will only work if you did previous steps and your phone is visible from your computer.");
      text("");
      text("Alternatively you can use usb cable to connect to your computer", 20);
      text("You need 'adb' command from android SDK");
      text("Type 'adb forward tcp:" + port + " tcp:" + port + "' in command line");
      text("And connect your computer's browser to http://localhost:" + port);
      return parent;
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if (this.bindService(new Intent(this, HttpService.class),
            new ServiceConnection() {
          //@Override
          public void onServiceConnected(ComponentName name, IBinder service) {
            Debug.d("WiFiInputMethod connected to HttpService.");
            try {
              int newPort = RemoteKeyboard.Stub.asInterface(service).getPort();
              if (newPort != port) {
                port = newPort;
                setContentView(createView());
              }
            } catch (RemoteException e) {
              throw new RuntimeException(
                  "WiFiInputMethod failed to connected to HttpService.", e);
            }
          }
          //@Override
          public void onServiceDisconnected(ComponentName name) {
            Debug.d("WiFiInputMethod disconnected from HttpService.");
          }
        }, BIND_AUTO_CREATE) == false) {
          throw new RuntimeException("failed to connect to HttpService");
        }

        setContentView(createView());
    }
    
    private void text(String msg) {
      text(msg, 15);
    }
    
    private void text(String msg, int fontSize) {
      TextView v = new TextView(this);
      v.setText(msg);
      v.setTextSize(fontSize);
      layout.addView(v);
    }
}