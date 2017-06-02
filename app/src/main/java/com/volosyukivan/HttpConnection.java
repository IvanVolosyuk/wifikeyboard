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
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

import android.util.Log;

public abstract class HttpConnection {
  // Selector data
  private static final int BUFSIZE = 1024;
  SelectionKey key;
  private SocketChannel ch;
  
  // Stream connection fields
  ByteBuffer outputBuffer;
  private ByteBuffer in;
  {
    in = ByteBuffer.allocate(BUFSIZE);
    in.limit(0);
  }
  
  enum ConnectionState {
    SELECTOR_WAIT_FOR_NEW_INPUT,
    SELECTOR_WAIT_FOR_OUTPUT_BUFFER,
    SELECTOR_NO_WAIT
  }
  
  class ConnectionFailureException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public ConnectionFailureException(String msg) {
      super(msg);
    }
  };
  
  public HttpConnection(SocketChannel ch) {
    this.ch = ch;
    Log.d("wifikeyboard", "new connection");
  }

  public SocketChannel getClient() {
    return ch;
  }

  /// Transport layer functions
  
  /**
   * New data available for the connection.
   * @param ch
   * @return SELECTOR_* new state of connection.
   */
  public final ConnectionState newInput() throws IOException {
    if (in.hasRemaining()) {
      throw new RuntimeException("data remaining in input");
    }
    in.clear();
    int r = ch.read(in);
    if (r == -1) throw new IOException("connection closed on client side");
    if (r == 0) return ConnectionState.SELECTOR_WAIT_FOR_NEW_INPUT;
    in.flip();
    return newRequestChunk();
  }

  public final ConnectionState newOutputBuffer() throws IOException {
    ch.write(outputBuffer);
    if (outputBuffer.hasRemaining()) {
      return ConnectionState.SELECTOR_WAIT_FOR_OUTPUT_BUFFER;
    }
    outputBuffer = null;
    return newRequestChunk();
  }
  
  /// HTTP layer functions
  
  enum HttpConnectionState {
    READ_REQUEST,
    LOOK_REQUEST_HEADERS,
    SKIP_HEADERS,
    READ_HEADER,
    CONSIDER_FORM_DATA,
    READ_FORM_DATA,
    EXECUTE_REQUEST
  };
  
  // Http connection fields
  
  // Buffer references
  private byte[] data;
  private int offset;
  private int limit;
  
  private HttpConnectionState httpConnectionState = HttpConnectionState.READ_REQUEST;
  private boolean isPost;
  
  private ConnectionState newRequestChunk() throws IOException {
    ConnectionState connectionState = ConnectionState.SELECTOR_WAIT_FOR_NEW_INPUT;
    data = in.array();
    offset = in.position();
    limit = in.limit();
    exit:
    while (true) {
      switch (httpConnectionState) {
        case READ_REQUEST:
          if (offset == limit) break exit;
          httpConnectionState = readRequest();
          break;
        case LOOK_REQUEST_HEADERS:
          isPost = request[0] == LETTER_P;
          headerMatcher = lookupRequestHandler();
          if (headerMatcher == null)
            httpConnectionState = HttpConnectionState.SKIP_HEADERS;
          else {
            httpConnectionState = HttpConnectionState.READ_HEADER;
            int numHeaders = headerMatcher.patterns.length;
            for (int i = 0; i < numHeaders; i++) {
              headerLen[i] = 0;
            }
          }
          break;
        case READ_HEADER:
          if (offset == limit) break exit;
          httpConnectionState = readHeader();
          break;
        case SKIP_HEADERS:
          if (offset == limit) break exit;
          httpConnectionState = skipHeaders();
          break;
        case CONSIDER_FORM_DATA:
          httpConnectionState = considerFormData();
          break;
        case READ_FORM_DATA:
          if (offset == limit) break exit;
          httpConnectionState = readFormData();
          break;
        case EXECUTE_REQUEST:
          httpConnectionState = HttpConnectionState.READ_REQUEST;
          in.position(offset);
          connectionState = executeRequest();
          if (connectionState != ConnectionState.SELECTOR_WAIT_FOR_NEW_INPUT) break exit;
      }
      
    }
    in.position(offset);
    return connectionState;
  }
  
  private static final byte LETTER_P = "P".getBytes()[0];
  private static final byte LETTER_COLUMN = ":".getBytes()[0];
  private static final byte LETTER_CR = "\n".getBytes()[0];
  
  protected byte[] request = new byte[BUFSIZE];
  protected int requestLength = 0;
  
  private HttpConnectionState readRequest() {
    int limit = this.limit;
    int requestLength = this.requestLength;
    byte[] data = this.data;
    
    for (int i = offset; i < limit; i++) {
      byte b = data[i];
      if (b == LETTER_CR) {
        // request end
        this.requestLength = requestLength;
        this.offset = i + 1;
        return HttpConnectionState.LOOK_REQUEST_HEADERS;
      } else {
        try {
          request[requestLength++] = b;
        } catch (ArrayIndexOutOfBoundsException e) {
          throw new ConnectionFailureException("request is too large");
        }
      }
    }
    this.offset = limit;
    this.requestLength = requestLength;
    return HttpConnectionState.READ_REQUEST;
  }
  
  private HeaderMatcher headerMatcher;
  private int matchedChars = 0;
  private int matchedMatcher = 0;
  
  enum HeaderState {
    MATCH_HEADER_NAME,
    SKIP_HEADER_NAME,
    STORE_HEADER_DATA,
    SKIP_HEADER_DATA,
    NO_MORE_HEADERS
  }

  private HeaderState headerState = HeaderState.MATCH_HEADER_NAME;
  protected byte[][] header = new byte[2][256];
  protected int[] headerLen = new int[2];
  private byte[] currentHeader;
  private int currentHeaderLen;

  private HttpConnectionState readHeader() {
    while (offset != limit) {
      switch (headerState) {
      case MATCH_HEADER_NAME:
        headerState = matchHeaderName();
        break;
      case SKIP_HEADER_NAME:
        headerState = skipHeaderName();
        if (headerState == HeaderState.NO_MORE_HEADERS) {
          headerState = HeaderState.MATCH_HEADER_NAME;
          return HttpConnectionState.CONSIDER_FORM_DATA;
        }
        break;
      case SKIP_HEADER_DATA:
        headerState = skipHeaderData();
        break;
      case STORE_HEADER_DATA:
        headerState = storeHeaderData();
        break;
      }
    }
    return HttpConnectionState.READ_HEADER;
  }
  
  // match bbde
  // abc   [ matched = 0]
  // bbce  [ matched=2 skip = 0]
  // ...
  // e, skip=0   (failed) 
  // bbcd, skip=3 (match not possible, skip to next)
  // bbx, skip=2  (try match)
  private HeaderState matchHeaderName() {
    byte[] data = this.data;
    int limit = this.limit;
    int matchedChars = this.matchedChars;
    int matchedMatcher = this.matchedMatcher;
    
    HeaderMatcher matcher = headerMatcher;
    byte[][] patterns = matcher.patterns;
    int npatterns = patterns.length;
    byte[] pattern = patterns[matchedMatcher];
    int i;
    
    outer:
    for (i = offset; i < limit; ) {
      byte b = data[i];
      while (true) {
        if (pattern[matchedChars] == b) {
          matchedChars++;
          i++;
          if (b == LETTER_COLUMN) {
            // fully matched
            this.offset = i + 1;
            this.matchedChars = 0;
            this.currentHeader = this.header[this.matchedMatcher];
            this.currentHeaderLen = 0;
            return HeaderState.STORE_HEADER_DATA;
          }
          continue outer;
        }
        
        inner:
        while (true) {
          matchedMatcher++;
          if (matchedMatcher >= npatterns)
            return HeaderState.SKIP_HEADER_NAME;
          int similarity = headerMatcher.similarity[matchedMatcher];
          if (similarity < matchedChars) {
            this.matchedChars = matchedChars;
            return HeaderState.SKIP_HEADER_NAME;
          } else if (similarity > matchedChars) {
            // will not match, skip this pattern
            continue inner;
          } else {
            // try match this pattern
            pattern = patterns[matchedMatcher];
            continue outer;
          }
        }
      }
    }
    offset = i;
    this.matchedChars = matchedChars;
    this.matchedMatcher = matchedMatcher;
    return HeaderState.MATCH_HEADER_NAME;
  }
  
  private HeaderState skipHeaderName() {
    byte[] data = this.data;
    for (int i = offset; i < limit; i++,matchedChars++) {
      byte b = data[i];
      if (b == LETTER_COLUMN) {
        this.offset = i + 1;
        return HeaderState.SKIP_HEADER_DATA;
      } else if (b == LETTER_CR) {
        if (matchedChars > 1) {
          throw new ConnectionFailureException("header format error");
        }
        this.offset = i + 1;
        return HeaderState.NO_MORE_HEADERS;        
      }
    }
    this.matchedChars = 0;
    this.matchedMatcher = 0;
    return HeaderState.MATCH_HEADER_NAME;
  }
  
  private HeaderState skipHeaderData() {
    byte[] data = this.data;
    int limit = this.limit;
    for (int i = offset; i < limit; i++) {
      byte b = data[i];
      if (b == LETTER_CR) {
        this.offset = i + 1;
        this.matchedChars = 0;
        this.matchedMatcher = 0;
        return HeaderState.MATCH_HEADER_NAME;
      }
    }
    this.offset = limit;
    return HeaderState.SKIP_HEADER_DATA;
  }
  
  private HeaderState storeHeaderData() {
    byte[] data = this.data;
    int limit = this.limit;
    int currentHeaderLen = this.currentHeaderLen;
    byte[] currentHeader = this.currentHeader;
    for (int i = offset; i < limit; i++) {
      byte b = data[i];
      if (b == LETTER_CR) {
//        Log.d("wifikeyboard", "header value = " + new String(currentHeader, 0, currentHeaderLen));
        this.header[this.matchedMatcher] = currentHeader;
        this.headerLen[this.matchedMatcher] = currentHeaderLen;
        this.offset = i + 1;
        this.matchedChars = 0;
        this.matchedMatcher = 0;
        return HeaderState.MATCH_HEADER_NAME;
      } else {
        currentHeader[currentHeaderLen++] = b;
      }
    }
    this.offset = limit;
    this.currentHeaderLen = currentHeaderLen;
    return HeaderState.STORE_HEADER_DATA;
  }

  
  private int skipLineLen = 0;
  private HttpConnectionState skipHeaders() {
    byte[] data = this.data;
    int skipLineLen = this.skipLineLen;
    int limit = this.limit;
    for (int i = offset; i < limit; i++) {
      if (data[i] == LETTER_CR) {
        if (skipLineLen < 2) {
          // no more headers
          offset = i + 1;
          this.skipLineLen = 0;
          return HttpConnectionState.CONSIDER_FORM_DATA;
        } else {
          skipLineLen = 0;
        }
      } else {
        skipLineLen++;
      }
    }
    this.offset = limit;
    this.skipLineLen = skipLineLen;
    return HttpConnectionState.SKIP_HEADERS;
  }
  
  
  
//  = new RequestHandler(new HeaderMatcher("Accept-Language","Accept-Encoding")) {
//    @Override
//    public ByteBuffer processQuery(byte[] query, int offset, int limit) {
//      return null;
//    }
//  };
  
  public abstract HeaderMatcher lookupRequestHandler();
  
  // FIXME: dummy form data implementation
  byte[] formData = new byte[BUFSIZE];
  int formDataLength = 0;
  private int formDataExpectedLength = 0;

  private HttpConnectionState readFormData() {
    byte[] data = this.data;
    int limit = this.limit;
    int formDataLength = this.formDataLength;
    int formDataExpectedLength = this.formDataExpectedLength;
    
    for (int i = offset; i < limit; i++) {
      byte b = data[i];
      try {
        formData[formDataLength++] = b;
      } catch (ArrayIndexOutOfBoundsException e) {
        throw new ConnectionFailureException("request is too large");
      }
      if (formDataLength == formDataExpectedLength) {
        // request end
        this.formDataLength = formDataLength;
        this.offset = i + 1;
//        Log.d("wifikeyboard", "Form data: " + new String(formData, 0, formDataLength));
        return HttpConnectionState.EXECUTE_REQUEST;
      } else {
      }
    }
    this.offset = limit;
    this.formDataLength = formDataLength;
    return HttpConnectionState.READ_FORM_DATA;
  }
  
  private static final byte[] ACCEPTED_CONTENT_TYPE =
    "application/x-www-form-urlencoded".getBytes();
  private static byte LETTER_ZERO = "0".getBytes()[0];
  
  private HttpConnectionState considerFormData() {
    if (!isPost) {
      return HttpConnectionState.EXECUTE_REQUEST;
    }
    
    byte[] contentType = header[0]; 
//    if (ACCEPTED_CONTENT_TYPE.length != headerLen[0]) {
//      throw new ConnectionFailureException("unsupported content type");
//    }
//    int len = ACCEPTED_CONTENT_TYPE.length;
//    for (int i = 0; i < len; i++) {
//      if (ACCEPTED_CONTENT_TYPE[i] != contentType[i])
//        throw new ConnectionFailureException("unsupported content type");
//    }
    
    int contentLen = 0;
    int len = headerLen[0] - 1;
    byte[] contentLenHeader = header[0];
    for (int i = 0; i < len; i++) {
      contentLen = (contentLen * 10) + contentLenHeader[i] - LETTER_ZERO;
    }
    
    this.formDataExpectedLength = contentLen;
    
    if (contentLen == 0) {
      return HttpConnectionState.EXECUTE_REQUEST;
    }
    if (formData.length < contentLen) {
      formData = new byte[contentLen];
    }
    return HttpConnectionState.READ_FORM_DATA;
  }
  
  protected abstract ByteBuffer requestHandler();
  
  private ConnectionState executeRequest() throws IOException {
    ByteBuffer output = requestHandler();
    // Cleanup
    requestLength = 0;
    formDataLength = 0;
    
    if (output == null) {
      return ConnectionState.SELECTOR_NO_WAIT;
    }
    
    // FIXME: check that this doesn't block
    ch.write(output);
    
    if (output.hasRemaining()) {
      outputBuffer = output;
      return ConnectionState.SELECTOR_WAIT_FOR_OUTPUT_BUFFER;
    }
    
    return ConnectionState.SELECTOR_WAIT_FOR_NEW_INPUT;
  }
  
  static class HeaderMatcher {
    byte[][] patterns;
    int[] similarity;
    public HeaderMatcher(String...strings) {
      patterns = new byte[strings.length][];
      similarity = new int[strings.length];
      String prevString = null;
      int nstr = 0;
      
      for (String s : strings) {
        int match = 0;
        if (prevString != null) {
          int len = Math.min(s.length(), prevString.length());
          for (int i = 0; i < len; i++) {
            if (s.charAt(i) != prevString.charAt(i)) break;
            match++;
          }
        }
        similarity[nstr] = match;
        patterns[nstr++] = (s + ":").getBytes();
        prevString = s;
      }
    }
  };
  
//  @Deprecated
//  private String parseRequest(String line) {
//    String[] parts = line.split(" ");
//    if (parts.length != 3) {
//      throw new ConnectionFailureException("malformed req: " + line);
//    }
//    String url = parts[1];
//    String[] urlParts = url.split("/", -2);
//    return urlParts[urlParts.length - 1];
//  }

//  /**
//   * Obtain response for the request.
//   * @param url
//   * @return
//   */
//  @Deprecated
//  protected abstract ByteBuffer processRequest(String url);

  public void setKey(SelectionKey clientkey) {
    this.key = clientkey;
  }
}
