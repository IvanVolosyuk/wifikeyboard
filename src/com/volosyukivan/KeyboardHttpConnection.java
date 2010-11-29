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
  
    private byte[][] patterns;
    RequestHandler[] handlers;

  public KeyboardHttpConnection(final KeyboardHttpServer server, SocketChannel ch) {
    super(ch);
    this.server = server;
    // This initalization should be done only once, but it references 'server',
    // too bad, need to look at this
    class HandlerInit {
      ArrayList<byte[]> patterns = new ArrayList<byte[]>();
      ArrayList<RequestHandler> handlers = new ArrayList<RequestHandler>();
      
      public void add(String pattern, RequestHandler handler) {
        patterns.add(pattern.getBytes());
        handlers.add(handler);
      }
    };
    HandlerInit handler = new HandlerInit();

    handler.add("key", new RequestHandler(null) {
      @Override
      public ByteBuffer processQuery() {
        String response = server.processKeyRequest(
            new String(request, cmdEnd + 1, queryEnd));
        
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
//        Debug.d(response);
        byte[] content = response.getBytes();
        buffer = sendData("text/plain", content, content.length);
        cache.put(response, buffer);
        return buffer;
      }
    });
    
    // Default page
    handler.add("", new RequestHandler(null) {
      @Override
      public ByteBuffer processQuery() {
        String page = server.getPage();
        try {
          byte[] content = page.getBytes("UTF-8");
          server.sendKey(KeyboardHttpServer.FOCUS, true);
          return sendData("text/html; charset=UTF-8", content, content.length);
        } catch (UnsupportedEncodingException e) {
          throw new RuntimeException("UTF-8 unsupported");
        }
      }
    });
    
    handler.add("wait", new RequestHandler(null) {
      @Override
      public ByteBuffer processQuery() {
        server.addWaitingConnection(KeyboardHttpConnection.this);
        return null;
      }
    });
    
    handler.add("text", new RequestHandler(null) {
      @Override
      public ByteBuffer processQuery() {
        byte[] text = null;
        try {
          text = ((String)(server.getText())).getBytes("UTF-8");
        } catch (UnsupportedEncodingException e) {
        }
        if (text == null) {
          // FIXME: error handling?
          text = new byte[0];
        }
        return sendData("text/plain; charset=UTF-8", text, text.length);
      }
    });

    
    handler.add("bg.gif", new RequestHandler(null) {
      @Override
      public ByteBuffer processQuery() {
        return sendImage(R.raw.bg);
      }
    });
    
    handler.add("icon.png", new RequestHandler(null) {
      @Override
      public ByteBuffer processQuery() {
        return sendImage(R.raw.icon);
      }
    });
    
    handler.add("form", new RequestHandler(new HeaderMatcher(
        "Content-Type", "Content-Length"
    )) {
      @Override
      public ByteBuffer processQuery() {
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
    });
    
    this.handlers = handler.handlers.toArray(new RequestHandler[0]);
    this.patterns = handler.patterns.toArray(new byte[0][0]);
  }
  
  private static final byte LETTER_SPACE = " ".getBytes()[0];
  private static final byte LETTER_SLASH = "/".getBytes()[0];
  private static final byte LETTER_QUESTION = "?".getBytes()[0];
  private int queryEnd;
  private int cmdEnd;
  
  @Override
  public RequestHandler lookupRequestHandler() {
    byte[] request = this.request;
    
    queryEnd = 0;
    for (int i = requestLength - 1; i >= 0; i--) {
      if (request[i] == LETTER_SPACE) {
        queryEnd = i;
        break;
      }
    }
    
    int cmdStart = 0;
    for (int i = queryEnd; i >= 0; i--) {
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
    
    int nhandlers = handlers.length;
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
      return handlers[i];
    }
    
    return handlers[0];
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
}
