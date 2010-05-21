package com.volosyukivan;

import java.io.IOException;
import java.io.InputStream;

public class HttpRequestParser {
  InputStream is;
  private static final int size = 4096; 
  byte[] buffer = new byte[size];
  
  public String getRequest(InputStream is) throws IOException {
    int offset = 0;
    int lineLen = 0;
    
    String req = null;
    while (true) {
      int r = is.read(buffer, offset, size - offset);
      if (r < 0) {
        throw new IOException("request unfinished");
      }
      for (int i = offset; i < offset + r; i++) {
        if (buffer[i] == 10) {
          // Debug.d("line len = " + lineLen + " offset = " + offset);
          if (lineLen < 2) return parseRequest(req);
          else if (req == null) {
            req = new String(buffer, 0, i, "UTF-8");
          }
          lineLen = 0;
        } else {
          lineLen++;
        }
        
      }
      offset += r;
      if (offset == size) {
        if (req == null) throw new IOException("request is too large");
        offset = 0;
      }
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