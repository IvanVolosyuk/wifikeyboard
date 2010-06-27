package com.volosyukivan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import android.os.Handler;
import android.os.RemoteException;
import android.view.KeyEvent;

public class HttpServer extends Thread {
  private ServerSocketChannel ch;
  private HttpService service;
  private Handler handler;
  static final int FOCUS = 1024;
  private boolean isDone;
  private int seqNum = 0;
  private boolean connected;
  private boolean delivered;

  HttpServer(HttpService service, ServerSocketChannel ch) {
    this.service = service;
    this.ch = ch;
    this.handler = new Handler();
  }

  @Override
  public void run() {
    Debug.d("HttpServer started listening");
    try {
      Selector selector = Selector.open();

      ch.configureBlocking(false);
      SelectionKey serverkey = ch.register(selector, SelectionKey.OP_ACCEPT);

      while (!isDone()) {
        Debug.d("waiting for event");
        selector.select();
        Debug.d("got an event");
        Set<SelectionKey> keys = selector.selectedKeys();

        for (Iterator<SelectionKey> i = keys.iterator(); i.hasNext();) {
          SelectionKey key = i.next();
          i.remove();

          if (key == serverkey) {
            if (key.isAcceptable()) {
              SocketChannel client = ch.accept();
              Debug.d("NEW CONNECTION from " + client.socket().getRemoteSocketAddress());
              client.configureBlocking(false);
              SelectionKey clientkey = client.register(selector, SelectionKey.OP_READ);
              clientkey.attach(new HttpConnection(this, client));
            }
          } else {
            SocketChannel client = (SocketChannel) key.channel();
            if (!key.isReadable())
              continue;
            HttpConnection conn = (HttpConnection) key.attachment();
            Debug.d("processing read event");
            if (!conn.process()) {
              Debug.d("CONNECTION CLOSED from " + client.socket().getRemoteSocketAddress());
              key.cancel();
              client.close();
            }
            Debug.d("finished read event");
          }
        }
      }
        
      try {
        Debug.d("Server socket close");
        selector.close();
        ch.close();
      } catch (IOException e) {
        Debug.e("closing listening socket", e);
      }
    } catch (IOException e) {
    }
  }
  
  private final synchronized boolean isDone() {
    return isDone;
  }
  
  public synchronized void finish() {
    isDone = true;
  }
  
  public void sendData(
      SocketChannel ch,
      String content_type,
      byte[] content,
      int content_length) throws IOException {
    byte[] headers = String.format("HTTP/1.1 200 OK\n" +
        "Content-Type: %s\n"+
        "Content-Length: %d\n" +
        "\n", content_type, content_length).getBytes();

    ByteBuffer out = ByteBuffer.allocate(headers.length + content_length);
    out.put(headers);
    out.put(content, 0, content_length);
    out.flip();
    ch.write(out);
  }
  
  public void sendImage(SocketChannel ch, int resid) throws IOException {
    InputStream is2 = service.getResources().openRawResource(resid);
    byte[] image = new byte[10240];
    sendData(ch, "image/gif", image, is2.read(image));
  }

  public boolean processRequest(String req, SocketChannel ch) throws IOException {
    Debug.d("got key event: " + req);

    if (req.equals("")) {
      Debug.d("sending html page");
      String page = service.htmlpage.replace("12345", Integer.toString(seqNum + 1));
      byte[] content = page.getBytes("UTF-8");
      sendData(ch, "text/html; charset=UTF-8", content, content.length);
      sendKey(FOCUS, true);
      return false;
    }


    if (req.equals("bg.gif")) {
      sendImage(ch, R.raw.bg);
      return false;
    }

    if (req.equals("icon.png")) {
      sendImage(ch, R.raw.icon);
      return false;
    }

    boolean success = true;
    boolean event = false;

    String[] ev = req.split(",", -1);
    boolean http11 = true;
    if (ev[0].charAt(0) == 'H') {
      http11 = false;
      ev[0] = ev[0].substring(1);
    }
    int seq = Integer.parseInt(ev[0]);
    int numKeysRequired = seq - seqNum;
    if (numKeysRequired <= 0) {
      response(ch, "multi");
      return http11;
    }
    int numKeysAvailable = ev.length - 2;
    int numKeys = Math.min(numKeysAvailable, numKeysRequired);

    for (int i = numKeys; i >= 1; i--) {
      Debug.d("Event: " + ev[i]);
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
      response(ch, "multi");
    } else if (success) {
      response(ch, "ok");
    } else {
      response(ch, "problem");
    }
    return http11;
  }
  
  private void response(SocketChannel ch, String val) throws IOException {
    Debug.d(val);
    byte[] content = val.getBytes();
    sendData(ch, "text/plain", content, content.length);
  }
  
  private boolean sendKey(final int code0, final boolean pressed) {
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
  
  private boolean sendChar(final int code) {
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

  private int convertKey(int code) {
    // public static final int KEYCODE_A = 29;
    // ...
    // public static final int KEYCODE_Z = 54;
    if (code >= 65 && code <= 90) {
      return code - 65 + KeyEvent.KEYCODE_A;
    }

    // public static final int KEYCODE_0 = 7;
    // ...
    // public static final int KEYCODE_9 = 16;
    if (code >= 48 && code <= 57) {
      return code - 48 + KeyEvent.KEYCODE_0;
    }

    switch (code) {
      case 9: return KeyEvent.KEYCODE_TAB;
      case 32: return KeyEvent.KEYCODE_SPACE;
      case 188: return KeyEvent.KEYCODE_COMMA;
      case 190: return KeyEvent.KEYCODE_PERIOD;
      case 13: return KeyEvent.KEYCODE_ENTER;
      case 219: return KeyEvent.KEYCODE_LEFT_BRACKET;
      case 221: return KeyEvent.KEYCODE_RIGHT_BRACKET;
      case 220: return KeyEvent.KEYCODE_BACKSLASH;
      case 186: return KeyEvent.KEYCODE_SEMICOLON;
      case 222: return KeyEvent.KEYCODE_APOSTROPHE;
      case 8: return KeyEvent.KEYCODE_DEL;
      case 189: return KeyEvent.KEYCODE_MINUS;
      case 187: return KeyEvent.KEYCODE_EQUALS;
      case 191: return KeyEvent.KEYCODE_SLASH;
      case 18: return KeyEvent.KEYCODE_ALT_LEFT;
      case 16: return KeyEvent.KEYCODE_SHIFT_LEFT;

      // public static final int KEYCODE_DPAD_UP = 19;
      // public static final int KEYCODE_DPAD_DOWN = 20;
      // public static final int KEYCODE_DPAD_LEFT = 21;
      // public static final int KEYCODE_DPAD_RIGHT = 22;
      // public static final int KEYCODE_DPAD_CENTER = 23;
      // arrow keys
      case 38: return KeyEvent.KEYCODE_DPAD_UP;
      case 40: return KeyEvent.KEYCODE_DPAD_DOWN;
      case 37: return KeyEvent.KEYCODE_DPAD_LEFT;
      case 39: return KeyEvent.KEYCODE_DPAD_RIGHT;
      // Insert
      case 112: return KeyEvent.KEYCODE_DPAD_CENTER;
      case 45: return KeyEvent.KEYCODE_DPAD_CENTER;
      
      
      // ESC
      case 27: return KeyEvent.KEYCODE_BACK;
      // Home
      case 36: return KeyEvent.KEYCODE_HOME;
      case 113: return KeyEvent.KEYCODE_MENU;
      // case x: return KeyEvent.KEYCODE_CALL;
      // case x: return KeyEvent.KEYCODE_ENDCALL;
      
      // F9, F10
      case 121: return KeyEvent.KEYCODE_VOLUME_UP;
      case 120: return KeyEvent.KEYCODE_VOLUME_DOWN;
      case FOCUS: return FOCUS;

      default: return KeyEvent.KEYCODE_UNKNOWN;
    }

    // case x: return KeyEvent.KEYCODE_SOFT_LEFT;
    // case x: return KeyEvent.KEYCODE_SOFT_RIGHT;
    // case x: return KeyEvent.KEYCODE_STAR;
    // case x: return KeyEvent.KEYCODE_POUND;
    // case x: return KeyEvent.KEYCODE_POWER;
    // case x: return KeyEvent.KEYCODE_CAMERA;
    // case x: return KeyEvent.KEYCODE_CLEAR;
    // case x: return KeyEvent.KEYCODE_ALT_RIGHT;
    // case x: return KeyEvent.KEYCODE_SHIFT_RIGHT;
    // case x: return KeyEvent.KEYCODE_SYM;
    // case x: return KeyEvent.KEYCODE_EXPLORER;
    // case x: return KeyEvent.KEYCODE_ENVELOPE;
    // case x: return KeyEvent.KEYCODE_GRAVE;
    // case x: return KeyEvent.KEYCODE_AT;
    // case x: return KeyEvent.KEYCODE_NUM;
    // case x: return KeyEvent.KEYCODE_HEADSETHOOK;
    // case x: return KeyEvent.KEYCODE_FOCUS;
    // case x: return KeyEvent.KEYCODE_PLUS;
    // case x: return KeyEvent.KEYCODE_NOTIFICATION;
    // case x: return KeyEvent.KEYCODE_SEARCH;
    // case x: return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    // case x: return KeyEvent.KEYCODE_MEDIA_STOP;
    // case x: return KeyEvent.KEYCODE_MEDIA_NEXT;
    // case x: return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    // case x: return KeyEvent.KEYCODE_MEDIA_REWIND;
    // case x: return KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
    // case x: return KeyEvent.KEYCODE_MUTE;
    //
    // META_ALT_ON = 2;
    // META_ALT_LEFT_ON = 16;
    // META_ALT_RIGHT_ON = 32;
    // META_SHIFT_ON = 1;
    // META_SHIFT_LEFT_ON = 64;
    // META_SHIFT_RIGHT_ON = 128;
    // META_SYM_ON = 4;
  }

}
