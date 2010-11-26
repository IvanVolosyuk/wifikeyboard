package com.volosyukivan;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public abstract class HttpConnection {
  private static final int BUFSIZE = 1024;
  SelectionKey key;
  private SocketChannel ch;
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
  
  private byte[] data;
  private int offset;
  private int limit;
  
  private HttpConnectionState httpConnectionState = HttpConnectionState.READ_REQUEST;
  private HeaderMatcher headerMatcher;
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
          headerMatcher = lookupHeaderMatcherForRequest();
          if (headerMatcher == null)
            httpConnectionState = HttpConnectionState.SKIP_HEADERS;
          else
            httpConnectionState = HttpConnectionState.READ_HEADER;
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
          if (isPost) httpConnectionState = HttpConnectionState.READ_FORM_DATA;
          else httpConnectionState = HttpConnectionState.EXECUTE_REQUEST;
          break;
        case READ_FORM_DATA:
          if (offset == limit) break exit;
          httpConnectionState = readFormData();
          break;
        case EXECUTE_REQUEST:
          httpConnectionState = HttpConnectionState.READ_REQUEST;
          in.position(offset);
          connectionState = executeRequest();
          if (connectionState != ConnectionState.SELECTOR_WAIT_FOR_NEW_INPUT) break;
      }
      
    }
    in.position(offset);
    return ConnectionState.SELECTOR_WAIT_FOR_NEW_INPUT;
  }
  
  private static final byte LETTER_P = "P".getBytes()[0];
  
  byte[] request = new byte[1024];
  int requestLength = 0;
  
  private HttpConnectionState readRequest() {
    int limit = this.limit;
    int requestLength = this.requestLength;
    
    for (int i = offset; i < limit; i++) {
      byte b = data[i];
      if (b == 10) {
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
  
  private HttpConnectionState readHeader() {
    throw new RuntimeException("not implemented");
//    return HttpConnectionState.READ_HEADER;
  }
  
  int skipLineLen = 0;
  private HttpConnectionState skipHeaders() {
    int skipLineLen = this.skipLineLen;
    int limit = this.limit;
    for (int i = offset; i < limit; i++) {
      if (data[i] == 10) {
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
  
  private HeaderMatcher lookupHeaderMatcherForRequest() {
    return null;
  }

  private HttpConnectionState readFormData() {
    throw new RuntimeException("not implemented");
//    return HttpConnectionState.SKIP_HEADERS;
  }

  private ConnectionState executeRequest() throws IOException {
    String req = parseRequest(new String(request, 0, requestLength));
    ByteBuffer output = processRequest(req);
    // Cleanup
    requestLength = 0;
    
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
  
  class HeaderMatcher {
  };


  private String parseRequest(String line) {
    String[] parts = line.split(" ");
    if (parts.length != 3) {
      throw new ConnectionFailureException("malformed req: " + line);
    }
    String url = parts[1];
    String[] urlParts = url.split("/", -2);
    return urlParts[urlParts.length - 1];
  }

  /**
   * Obtain response for the request.
   * @param url
   * @return
   */
  protected abstract ByteBuffer processRequest(String url);

  public void setKey(SelectionKey clientkey) {
    this.key = clientkey;
  }
}
