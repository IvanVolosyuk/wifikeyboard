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

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.channels.ServerSocketChannel;
import java.util.ArrayList;
import java.util.Enumeration;

import com.volosyukivan.PortUpdateListener.Stub;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Build;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class WiFiKeyboard extends Activity {
    int port = 7777;
    LinearLayout layout;
    ServiceConnection serviceConnection;

    public static ArrayList<String> getNetworkAddresses() {
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
      return addrs;
    }

    private View createView() {
      ArrayList<String> addrs = getNetworkAddresses();
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
      text(R.string.desc_setup_wifi, 20);
      text(R.string.desc_goto_settings);
      text(R.string.desc_enable_kbd);
      final int sdkVersion = Integer.parseInt(Build.VERSION.SDK);
      if (sdkVersion >= Build.VERSION_CODES.HONEYCOMB) {
        text(R.string.desc_toch_input_field_honeycomb);
      } else {
        text(R.string.desc_toch_input_field);
      }
      text(R.string.desc_change_input_method);
      text("", 15);
      if (addrs.size() == 0) {
        text("Enable wifi or GPRS/3G", 20);
      } else if (addrs.size() == 1) {
        text(getString(R.string.desc_connect_to_one, addrs.get(0), port), 20);
      } else {
        text(R.string.desc_connect_to_any);
        for (String addr : addrs) {
          text("http://" + addr + ":" + port, 20);
        }
      }
      text(R.string.desc_warn);
      text("", 15);
      text(R.string.desc_alt_usb_cable, 20);
      text(R.string.desc_adb_from_sdk);
      text(getString(R.string.desc_cmdline, port, port), 15);
      text(getString(R.string.desc_connect_local, port), 15);
      return parent;
    }

    @Override
    public void onResume() {
        super.onResume();

//        // FIXME: block default port out of use
//        ServerSocketChannel ch;
//        try {
//          ch = ServerSocketChannel.open();
//          ch.socket().setReuseAddress(true);
//          ch.socket().bind(new java.net.InetSocketAddress(7777));
//          ch = ServerSocketChannel.open();
//          ch.socket().setReuseAddress(true);
//          ch.socket().bind(new java.net.InetSocketAddress(1111));
//        } catch (IOException e1) {
//          // TODO Auto-generated catch block
//          e1.printStackTrace();
//        }

        serviceConnection = new ServiceConnection() {
          //@Override
          public void onServiceConnected(ComponentName name, IBinder service) {
            Debug.d("WiFiInputMethod connected to HttpService.");
            try {
              Stub listener = new PortUpdateListener.Stub() {
                @Override
                public void portUpdated(int newPort) throws RemoteException {
                  if (newPort != port) {
                    port = newPort;
                    setContentView(createView());
                  }
                }
              };
              RemoteKeyboard.Stub.asInterface(service).setPortUpdateListener(listener);
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
        if (this.bindService(new Intent(this, HttpService.class),
            serviceConnection, BIND_AUTO_CREATE) == false) {
          throw new RuntimeException("failed to connect to HttpService");
        }

        setContentView(createView());
    }

    @Override
    protected void onPause() {
      super.onPause();
      this.unbindService(serviceConnection);
    }

    private void text(int resId) {
      text(resId, 15);
    }

    private void text(int resId, int fontSize) {
      String msg = getString(resId);
      text(msg, fontSize);
    }

    private void text(String msg, int fontSize) {
      TextView v = new TextView(this);
      v.setText(msg);
      v.setTextSize(fontSize);
      layout.addView(v);
    }
}
