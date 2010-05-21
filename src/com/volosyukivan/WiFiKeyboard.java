package com.volosyukivan;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;

import android.app.Activity;
import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class WiFiKeyboard extends Activity {
    /** Called when the activity is first created. */
    @Override
    public void onResume() {
        super.onResume();
        Intent i = new Intent(this, HttpService.class);
        this.startService(i);
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
          text("Connect your computer's browser to http://" + addrs.get(0) + ":7777", 20);
        } else {
          text("Connect your computer's browser to any of:");
          for (String addr : addrs) {
            text("http://" + addr + ":7777", 20);
          }
        }
        text("This will only work if you did previous steps and your phone is visible from your computer.");
        text("");
        text("Alternatively you can use usb cable to connect to your computer", 20);
        text("You need 'adb' command from android SDK");
        text("Type 'adb forward tcp:7777 tcp:7777' in command line");
        text("And connect your computer's browser to http://localhost:7777");
        setContentView(parent);
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
    
    LinearLayout layout;
    
    
}