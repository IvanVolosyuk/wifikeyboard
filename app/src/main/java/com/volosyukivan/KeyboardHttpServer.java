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

import static com.volosyukivan.KeycodeConvertor.convertKey;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

import android.os.Handler;
import android.os.RemoteException;
import android.util.Log;

public final class KeyboardHttpServer extends HttpServer {
  private HttpService service;
  static final int FOCUS = 1024;
  private int seqNum = 0;
  ArrayList<KeyboardHttpConnection> waitingConnections =
    new ArrayList<KeyboardHttpConnection>();
  
  public HttpConnection newConnection(SocketChannel ch) {
    return new KeyboardHttpConnection(this, ch);
  }

  KeyboardHttpServer(HttpService service, ServerSocketChannel ch) {
    super(ch);
    this.service = service;
  }
  
  public String getPage() {
    return service.htmlpage.replace("12345", Integer.toString(seqNum + 1));
  }
  
  public String processKeyRequest(String req) {
    boolean success = true;
    boolean event = false;
    String[] ev = req.split(",", -1);
    int seq = Integer.parseInt(ev[0]);
    int numKeysRequired = seq - seqNum;
    if (numKeysRequired <= 0) {
      return "multi";
    }
    int numKeysAvailable = ev.length - 2;
    int numKeys = Math.min(numKeysAvailable, numKeysRequired);

    for (int i = numKeys; i >= 1; i--) {
//      Debug.d("Event: " + ev[i]);
      char mode = ev[i].charAt(0);
      int code = Integer.parseInt(ev[i].substring(1));
      if (mode == 'C') {
        // FIXME: can be a problem with extended unicode characters
        success = success && sendChar(code);
      } else {
        boolean pressed = mode == 'D';
        success = success && sendKey(code, pressed);
      }
      event = true;
    }
    seqNum = seq;

    if (!event) {
      return "multi";
    } else if (success) {
      return "ok";
    } else {
      return "problem";
    }
  }
  
  // used by network thread
  abstract class KeyboardAction extends Action {
    @Override
    public Object run() {
      try {
        RemoteKeyListener listener = service.listener;
        if (listener != null) {
          return runAction(listener);
        }
      } catch (RemoteException e) {
        Debug.e("Exception on input method side, ignore", e);
      }
      return null;
    }
    abstract Object runAction(RemoteKeyListener listener) throws RemoteException;
  };
  
  // executed by network thread
  boolean sendKey(final int code0, final boolean pressed) {
    final int code = convertKey(code0);
//    Log.d("wifikeyboard", "in: " + code0 + " out:" + code);
    
    Object success = runAction(new KeyboardAction() {
      @Override
      Object runAction(RemoteKeyListener listener) throws RemoteException {
        listener.keyEvent(code, pressed);
        return service; // not null for success
      }
    });
    return success != null;
  }
    
  // executed by network thread
  boolean sendChar(final int code) {
    Object success = runAction(new KeyboardAction() {
      @Override
      public Object runAction(RemoteKeyListener listener) throws RemoteException {
        listener.charEvent(code);
        return service; // not null
      }
    });
    return success != null;
  }
  
  public HttpService getService() {
    return service;
  }
  
  // executed by network thread
  public void addWaitingConnection(
      final KeyboardHttpConnection keyboardHttpConnection) {
    runAction(new Action() {
      @Override
      public Object run() {
        waitingConnections.add(keyboardHttpConnection);
        Log.d("wifikeyboard", "add waiting connection");
        return null;
      }
      
    });
  }
  
  public void onExit() {
    runAction(new Action() {
      @Override
      public Object run() {
        service.networkServerFinished();
        return null;
      }
    });
  }
  
  // executed by main thread
  public void notifyClient(final String text) {
    postUpdate(new Update() {
      @Override
      public void run() {
        for (KeyboardHttpConnection con : waitingConnections) {
          //            Debug.d(event);
          byte[] content = text.getBytes();
          ByteBuffer out = con.sendData("text/plain", content, content.length);
          setResponse(con, out);
        }
        waitingConnections.clear();
      }
    });
  }

  // Executed by network thread
  public boolean replaceText(final String string) {
    Object result = runAction(new KeyboardAction() {
      @Override
      Object runAction(RemoteKeyListener listener) throws RemoteException {
        
        return listener.setText(string) ? service : null;
      }
    });
    return result != null;
  }
  
  public Object getText() {
    return runAction(new KeyboardAction() {
      @Override
      Object runAction(RemoteKeyListener listener) throws RemoteException {
        return listener.getText();
      }
    });
  }
}
