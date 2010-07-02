package com.volosyukivan;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public final class KeyboardHttpConnection extends HttpConnection {

  private KeyboardHttpServer server;

  public KeyboardHttpConnection(KeyboardHttpServer server, SocketChannel ch) {
    super(ch);
    this.server = server;
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

  protected ByteBuffer processRequest(String req) {
    Debug.d("got key event: " + req);

    if (req.equals("")) {
      Debug.d("sending html page");
      String page = server.getPage();
      try {
        byte[] content = page.getBytes("UTF-8");
        server.sendKey(KeyboardHttpServer.FOCUS, true);
        return sendData("text/html; charset=UTF-8", content, content.length);
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException("UTF-8 unsupported");
      }
    }
    
    if (req.equals("wait")) {
      server.waitingConnections.add(this);
      return null;
    }

    if (req.equals("bg.gif")) {
      return sendImage(R.raw.bg);
    }

    if (req.equals("icon.png")) {
      return sendImage(R.raw.icon);
    }
    
    if (req.equals("test")) {
      StringBuilder res = new StringBuilder();
      for (int i = 0; i < 10000; i++) {
        res.append("Quick brown fox jump over lazy dog.\n");
      }
      String r = res.toString();
      return sendData("text/plain", r.getBytes(), r.getBytes().length);
    }

    String response = server.processKeyRequest(req);
    Debug.d(response);
    byte[] content = response.getBytes();
    return sendData("text/plain", content, content.length);
  }
}
