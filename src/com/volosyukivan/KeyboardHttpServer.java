package com.volosyukivan;

import static com.volosyukivan.KeycodeConvertor.convertKey;

import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;

import android.os.Handler;
import android.os.RemoteException;

public final class KeyboardHttpServer extends HttpServer {
  private HttpService service;
  private Handler handler;
  static final int FOCUS = 1024;
  private int seqNum = 0;
  private boolean connected;
  private boolean delivered;
  ArrayList<KeyboardHttpConnection> waitingConnections =
    new ArrayList<KeyboardHttpConnection>();
  
  public HttpConnection newConnection(SocketChannel ch) {
    return new KeyboardHttpConnection(this, ch);
  }

  KeyboardHttpServer(HttpService service, ServerSocketChannel ch) {
    super(ch);
    this.service = service;
    this.handler = new Handler();
  }
  
  public synchronized void notifyClient() {
    postEvent("my event");
  }
  
  public void onEvent(Object o) {
    String event = (String)o;
    for (KeyboardHttpConnection con : waitingConnections) {
//      Debug.d(event);
      byte[] content = event.getBytes();
      ByteBuffer out = con.sendData("text/plain", content, content.length);
      setResponse(con, out);
    }
    waitingConnections.clear();
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
  
  boolean sendKey(final int code0, final boolean pressed) {
    delivered = false;
    final int code = convertKey(code0);
    handler.post(new Runnable() {
      @Override
      public void run() {
        boolean connected0 = false;
        try {
          // Debug.d("key going to listener");
          if (service.listener != null) {
            service.listener.keyEvent(code, pressed);
            connected0 = true;
          }
        } catch (RemoteException e) {
          Debug.e("Exception on input method side, ignore", e);
        }
        notifyDelivered(connected0);
      }
    });
    return waitNotifyDelivered();
  }
    
  boolean sendChar(final int code) {
    delivered = false;
    handler.post(new Runnable() {
      @Override
      public void run() {
        boolean connected0 = false;
        try {
          // Debug.d("key going to listener");
          if (service.listener != null) {
            service.listener.charEvent(code);
            connected0 = true;
          }
        } catch (RemoteException e) {
          Debug.e("Exception on input method side, ignore", e);
        }
        notifyDelivered(connected0);
      }
    });
    return waitNotifyDelivered();
  }
  
  protected synchronized void notifyDelivered(boolean connected0) {
    connected = connected0;
    delivered = true;
    notifyAll();
  }
  private synchronized boolean waitNotifyDelivered() {
    while (!delivered) {
      try {
        wait();
      } catch (InterruptedException e) {
        connected = false;
      }
    }
    return connected;
  }

  public HttpService getService() {
    return service;
  }
  
  public void onInterrupt() {
    
  }
}
