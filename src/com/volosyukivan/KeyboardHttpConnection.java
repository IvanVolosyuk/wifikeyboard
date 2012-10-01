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
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

import android.util.Log;

public final class KeyboardHttpConnection extends HttpConnection {

  private KeyboardHttpServer server;
  
  private static final byte[] Q_KEY = "key".getBytes();
  private static final byte[] Q_FORM = "form".getBytes();
  private static final byte[] Q_TEXT = "text".getBytes();
  private static final byte[] Q_WAIT = "wait".getBytes();
  private static final byte[] Q_DEFAULT = "".getBytes();
  private static final byte[] Q_BG_GIF = "bg.gif".getBytes();
  private static final byte[] Q_ICON_PNG = "icon.png".getBytes();
  
  private static final byte[][] patterns = {
    Q_KEY,
    Q_FORM,
    Q_TEXT,
    Q_WAIT,
    Q_DEFAULT,
    Q_BG_GIF,
    Q_ICON_PNG,
  };
  
  private static final int H_KEY = 0;
  private static final int H_FORM = 1;
  private static final int H_TEXT = 2;
  private static final int H_WAIT = 3;
  private static final int H_DEFAULT = 4;
  private static final int H_BG_GIF = 5;
  private static final int H_ICON_PNG = 6;
  
  private int requestType;
  
  private HeaderMatcher formHeaders = new HeaderMatcher(
      "Content-Type", "Content-Length"
  );
  
  public KeyboardHttpConnection(final KeyboardHttpServer server, SocketChannel ch) {
    super(ch);
    this.server = server;
  }
  
  private static final byte LETTER_SPACE = " ".getBytes()[0];
  private static final byte LETTER_SLASH = "/".getBytes()[0];
  private static final byte LETTER_QUESTION = "?".getBytes()[0];
  private int queryEnd;
  private int cmdEnd;
  
  @Override
  public HeaderMatcher lookupRequestHandler() {
    byte[] request = this.request;
    
//    Log.d("wifikeyboard", "req: " + new String(request, 0, requestLength));
    
    queryEnd = 0;
    for (int i = requestLength - 1; i >= 0; i--) {
      if (request[i] == LETTER_SPACE) {
        queryEnd = i;
        break;
      }
    }
    
    int cmdStart = 0;
    for (int i = queryEnd - 1; i >= 0; i--) {
      if (request[i] == LETTER_SLASH) {
        cmdStart = i + 1;
        break;
      }
    }
    
    cmdEnd = queryEnd;
    for (int i = cmdStart; i < queryEnd; i++) {
      if (request[i] == LETTER_QUESTION) {
        cmdEnd = i;
        break;
      }
    }
    
    requestType = H_DEFAULT;
    int nhandlers = patterns.length;
    int cmdLen = cmdEnd - cmdStart;
    outer:
    for (int i = 0; i < nhandlers; i++) {
      byte[] pattern = patterns[i];
      if (pattern.length != cmdLen) {
        continue;
      }
      
      for (int j = 0; j < cmdLen; j++) {
        if (pattern[j] != request[j + cmdStart]) continue outer; 
      }
      requestType = i;
    }
    
    switch (requestType) {
    case H_FORM: return formHeaders;
    default: return null;
    }
  }
 
  public ByteBuffer sendData(
      String content_type,
      byte[] content,
      int content_length) {
    byte[] headers = String.format("HTTP/1.1 200 OK\n" +
        "Content-Type: %s\n"+
        "Content-Length: %d\n" +
        "\n", content_type, content_length).getBytes();

    ByteBuffer out = ByteBuffer.allocate(headers.length + content_length);
    out.put(headers);
    out.put(content, 0, content_length);
    out.flip();
    return out;
  }
  
  public ByteBuffer sendImage(int resid) {
    InputStream is2 = server.getService().getResources().openRawResource(resid);
    byte[] image = new byte[10240];
    try {
      return sendData("image/gif", image, is2.read(image));
    } catch (IOException e) {
      throw new RuntimeException("failed to load resource");
    }
  }
  
  private static ThreadLocal<Map<String,ByteBuffer>> responseCache =
    new ThreadLocal<Map<String,ByteBuffer>>();

  @Override
  protected ByteBuffer requestHandler() {
    switch (requestType) {
    case H_KEY: return onKeyRequest();
    case H_TEXT: return onTextRequest();
    case H_FORM: return onFormRequest();
    case H_DEFAULT: return onDefaultRequest();
    case H_BG_GIF: return onBgGifRequest();
    case H_ICON_PNG: return onIconPngRequest();
    case H_WAIT: return onWaitRequest();
    default: return onDefaultRequest();
    }
  }

  private ByteBuffer onWaitRequest() {
    server.addWaitingConnection(KeyboardHttpConnection.this);
    return null;
  }

  private ByteBuffer onIconPngRequest() {
    return sendImage(R.raw.icon);
  }

  private ByteBuffer onBgGifRequest() {
    return sendImage(R.raw.bg);
  }

  private ByteBuffer onDefaultRequest() {
    String page = server.getPage();
    try {
      byte[] content = page.getBytes("UTF-8");
      server.sendKey(KeyboardHttpServer.FOCUS, true);
      return sendData("text/html; charset=UTF-8", content, content.length);
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("UTF-8 unsupported");
    }
  }

  private ByteBuffer onFormRequest() {
    String newText = "";
    try {
      newText = new String(formData, 0, formDataLength, "UTF-8");
    } catch (UnsupportedEncodingException e) {
      Log.e("wifikeyboard", "UTF-8", e);
    }
    boolean success = server.replaceText(newText);
    // FIXME: use cached value
    byte[] resp = (success ? "ok" : "fail").getBytes();
    return sendData("text/plain", resp, resp.length);
  }

  private ByteBuffer onTextRequest() {
    byte[] text = null;
    try {
      if (server != null) {
        try {
          text = ((String)(server.getText())).getBytes("UTF-8");
        } catch (NullPointerException e) {
          Log.e("wifikeyboard", "no text", e);
        }
      }
    } catch (UnsupportedEncodingException e) {
    }
    if (text == null) {
      // FIXME: error handling?
      text = new byte[0];
    }
    return sendData("text/plain; charset=UTF-8", text, text.length);
  }

  private ByteBuffer onKeyRequest() {
    String response = server.processKeyRequest(
        new String(request, cmdEnd + 1, queryEnd));
//    Log.d("wifikeyboard", "response = " + response);
    Map<String, ByteBuffer> cache = responseCache.get();
    if (cache == null) {
      cache = new TreeMap<String, ByteBuffer>();
      responseCache.set(cache);
    }
    
    ByteBuffer buffer = cache.get(response);
    if (buffer != null) {
      buffer.position(0);
      return buffer;
    }
//    Debug.d(response);
    byte[] content = response.getBytes();
    buffer = sendData("text/plain", content, content.length);
    cache.put(response, buffer);
    return buffer;
  }
}
