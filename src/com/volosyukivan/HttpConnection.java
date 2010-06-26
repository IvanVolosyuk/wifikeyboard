package com.volosyukivan;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class HttpConnection {

  private HttpServer server;
  private ByteBuffer in = ByteBuffer.allocate(BUFSIZE);
  private byte[] buffer = new byte[BUFSIZE];
  private int offset;
  private static final int BUFSIZE = 1024;
  private String req = null;
  private int lineLen = 0;
  private SocketChannel ch;

  public HttpConnection(HttpServer server, SocketChannel ch) {
    this.server = server;
    this.ch = ch;
  }

  /**
   * New data available for the connection.
   * @param ch
   * @return false means connection closed, true connection is still open
   */
  public boolean process() {
    int r = 0;
    try {
      in.clear();
      r = ch.read(in);
      if (r == -1) return false;
      in.flip();
      
      while (r > 0) {
        int bufAvailable = BUFSIZE - offset;
        int size = Math.min(bufAvailable, r);
        in.get(buffer, offset, size);
        newData(size);
        r -= size;
      }
      return true;
    } catch (IOException e) {
      Debug.e("Reading http request failed", e);
      return false;
    }
  }
  private void newData(int r) throws IOException {
    // this is not a loop, it's a way to restart processing new request
    restart: while (true) {
      Debug.d(String.format("Request:'%s'", new String(buffer, offset, r)));
      int start = offset;
      for (int i = offset; i < offset + r; i++) {
        if (buffer[i] == 10) {
          lineLen += i - start;
          // Debug.d("line len = " + lineLen + " offset = " + offset);
          if (lineLen < 2) {
            // Request end, going to process
            String currentRequest = req;
            req = null;
            int remainingChars = offset + r - (i + 1);
            System.arraycopy(buffer, i + 1, buffer, 0, remainingChars);
            server.processRequest(currentRequest, ch);
            offset = 0;
            r = remainingChars;
            lineLen = 0;
            continue restart;
          }
          else if (req == null) {
            req = parseRequest(new String(buffer, 0, i, "UTF-8"));
          }
          lineLen = 0;
          start = i + 1;
        }
      }
      lineLen += offset + r - start;
      offset += r;
      if (offset == BUFSIZE) {
        if (req == null) throw new IOException("request is too large");
        offset = 0;
      }
      break;
    }
  }
  
  private String parseRequest(String line) throws IOException {
    String[] parts = line.split(" ");
    if (parts.length != 3) {
      throw new IOException("malformed req: " + line);
    }
    String url = parts[1];
    String[] urlParts = url.split("/", -2);
    return urlParts[urlParts.length - 1];
  }

}
