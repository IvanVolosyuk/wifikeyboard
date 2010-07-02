package com.volosyukivan;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public abstract class HttpServer extends Thread {

  // syncronized between main and network thread
  private boolean isDone;
  
  // private for network thread
  private Selector selector;
  private ServerSocketChannel ch;
  private Object event;
  

  public HttpServer(ServerSocketChannel ch) {
    this.ch = ch;
  }
  
  public void postEvent(Object event) {
    // FIXME: race condition
    this.event = event;
    if (selector != null) selector.wakeup();
  }
  protected void setResponse(KeyboardHttpConnection con, ByteBuffer out) {
    con.key.interestOps(SelectionKey.OP_WRITE);
    con.outputBuffer= out;
  }
  
  @Override
  public void run() {
    Debug.d("HttpServer started listening");
    try {
      selector = Selector.open();

      ch.configureBlocking(false);
      SelectionKey serverkey = ch.register(selector, SelectionKey.OP_ACCEPT);

      while (!isDone()) {
        Debug.d("waiting for event");
        selector.select();
        Object ev;
        synchronized (this) {
          if (isDone) break;
          ev = event;
          event = null;
        }
        if (ev != null) {
          onEvent(ev);
        }
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
              HttpConnection con = newConnection(client);
              clientkey.attach(con);
              con.setKey(clientkey);
              
            }
          } else {
            HttpConnection conn = (HttpConnection) key.attachment();
            try {
              int prevState, newState;
              if (key.isReadable()) {
                prevState = HttpConnection.SELECTOR_WAIT_FOR_NEW_INPUT; 
                Debug.d("processing read event");
                newState = conn.processReadEvent();
                Debug.d("finished read event");
              } else if (key.isWritable()) {
                prevState = HttpConnection.SELECTOR_WAIT_FOR_OUTPUT_BUFFER; 
                Debug.d("processing write event");
                newState = conn.processWriteEvent();
                Debug.d("finished write event");
              } else {
                continue;
              }
              if (newState == prevState) continue;
              key.interestOps(
                  (newState == HttpConnection.SELECTOR_WAIT_FOR_NEW_INPUT ? SelectionKey.OP_READ : 0) |
                  (newState == HttpConnection.SELECTOR_WAIT_FOR_OUTPUT_BUFFER ? SelectionKey.OP_WRITE : 0));
            } catch (IOException io) {
              Debug.d("CONNECTION CLOSED from " + 
                  conn.getClient().socket().getRemoteSocketAddress());
              if (key != null) key.cancel();
              conn.getClient().close();
            }
          }
        }
      }
        
      Debug.d("Server socket close");
      selector.close();
      ch.close();
    } catch (IOException e) {
      Debug.e("network loop terminated", e);
    }
  }
  
  private final synchronized boolean isDone() {
    return isDone;
  }
  
  public synchronized void finish() {
    isDone = true;
  }
  
  public abstract HttpConnection newConnection(SocketChannel ch);
  protected abstract void onEvent(Object ev);
}
