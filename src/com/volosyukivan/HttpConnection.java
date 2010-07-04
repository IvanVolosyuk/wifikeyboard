package com.volosyukivan;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public abstract class HttpConnection {

  private ByteBuffer in = ByteBuffer.allocate(BUFSIZE);
  private byte[] buffer = new byte[BUFSIZE];
  private int offset;
  private static final int BUFSIZE = 1024;
  private String req = null;
  private int lineLen = 0;
  private SocketChannel ch;
  private int pendingReadDataLen;
  ByteBuffer outputBuffer;
  SelectionKey key;
  
//  private static final int SELECTOR_CLOSE_CONNECTION = 0;
  public static final int SELECTOR_WAIT_FOR_NEW_INPUT = 1; 
  public static final int SELECTOR_WAIT_FOR_OUTPUT_BUFFER = 2;
  public static final int SELECTOR_WAIT_IO_MASK =
    SELECTOR_WAIT_FOR_NEW_INPUT | SELECTOR_WAIT_FOR_OUTPUT_BUFFER;
  public static final int SELECTOR_NO_WAIT = 4;

  public HttpConnection(SocketChannel ch) {
    this.ch = ch;
  }

  public SocketChannel getClient() {
    return ch;
  }

  /**
   * New data available for the connection.
   * @param ch
   * @return SELECTOR_* new state of connection.
   */
  public int processReadEvent() throws IOException {
    int r = 0;
      in.clear();
      int bufAvailable = BUFSIZE - offset;
      in.limit(bufAvailable);
      r = ch.read(in);
      if (r == -1) throw new IOException("connection closed on client side");
      if (r == 0) return SELECTOR_WAIT_FOR_NEW_INPUT;

      in.flip();
      
      int size = r;
      in.get(buffer, offset, size);
      return newDataReceived(size); 
  }
  
  public int processPendingReadData() throws IOException {
    if (pendingReadDataLen == 0)
      return SELECTOR_WAIT_FOR_NEW_INPUT;
    
    int r = pendingReadDataLen;
    pendingReadDataLen = 0;
    return newDataReceived(r);
  }


  private int newDataReceived(int r) throws IOException {
    if (pendingReadDataLen != 0) throw new RuntimeException("overwritten some data");
    
    // this is not a loop, it's a way to restart processing new request
    restart: while (true) {
//      Debug.d(String.format("Request:'%s'", new String(buffer, offset, r)));
      int start = offset;
      for (int i = offset; i < offset + r; i++) {
        if (buffer[i] != 10) {
          continue;
        }
        
        // Executes when found <CR> character:
        lineLen += i - start;
        // Debug.d("line len = " + lineLen + " offset = " + offset);
        if (lineLen < 2) {
          // Request end, going to process
          String currentRequest = req;
          req = null;
          int remainingChars = offset + r - (i + 1);
          System.arraycopy(buffer, i + 1, buffer, 0, remainingChars);
          offset = 0;
          r = remainingChars;
          lineLen = 0;
          ByteBuffer output = processRequest(currentRequest);
          if (output == null) {
            return SELECTOR_NO_WAIT;
          }
          ch.write(output);
          if (output.hasRemaining()) {
            pendingReadDataLen = r;
            outputBuffer = output;
            return SELECTOR_WAIT_FOR_OUTPUT_BUFFER;
          }

          continue restart;
        }
        else if (req == null) {
          req = parseRequest(new String(buffer, 0, i, "UTF-8"));
        }
        lineLen = 0;
        start = i + 1;

      }
      lineLen += offset + r - start;
      offset += r;
      if (offset == BUFSIZE) {
        if (req == null) throw new IOException("request is too large");
        offset = 0;
      }
      break;
    }
    return SELECTOR_WAIT_FOR_NEW_INPUT;
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

  public int processWriteEvent() throws IOException {
    ch.write(outputBuffer);
    if (outputBuffer.hasRemaining()) {
      return SELECTOR_WAIT_FOR_OUTPUT_BUFFER;
    }
    outputBuffer = null;
    return processPendingReadData();
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
