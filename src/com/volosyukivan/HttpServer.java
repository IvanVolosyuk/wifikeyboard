package com.volosyukivan;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

import android.util.Log;

public abstract class HttpServer extends Thread {

  // synchronized between main and network thread
  private boolean isDone;
  
  // private for network thread
  private Selector selector;
  private ServerSocketChannel ch;
  private Object event;

  boolean finished;
  

  public HttpServer(ServerSocketChannel ch) {
    this.ch = ch;
    try {
      selector = Selector.open();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  public void postEvent(Object event) {
    // FIXME: race condition
    this.event = event;
    if (selector != null) selector.wakeup();
  }
  
  protected void setResponse(KeyboardHttpConnection con, ByteBuffer out) {
    con.key.interestOps(SelectionKey.OP_WRITE);
    con.outputBuffer = out;
  }
  
  @Override
  public void run() {
//    Debug.d("HttpServer started listening");
    try {
      ch.configureBlocking(false);
      SelectionKey serverkey = ch.register(selector, SelectionKey.OP_ACCEPT);

      while (!isDone()) {
//        Debug.d("waiting for event");
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
//        Debug.d("got an event");
        Set<SelectionKey> keys = selector.selectedKeys();

        for (Iterator<SelectionKey> i = keys.iterator(); i.hasNext();) {
          SelectionKey key = i.next();
          i.remove();

          if (key == serverkey) {
            if (key.isAcceptable()) {
              SocketChannel client = ch.accept();
//              Debug.d("NEW CONNECTION from " + client.socket().getRemoteSocketAddress());
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
//                Debug.d("processing read event");
                long start = System.currentTimeMillis();
                newState = conn.processReadEvent();
                long end = System.currentTimeMillis();
                Debug.d("delay = " + (end - start));

//                int size = android.os.Debug.getThreadAllocSize();
//                android.os.Debug.stopAllocCounting();
//                Log.d("wifikeyboard", "finished read event, allocs = " + size);
//                android.os.Debug.startAllocCounting();

              } else if (key.isWritable()) {
                prevState = HttpConnection.SELECTOR_WAIT_FOR_OUTPUT_BUFFER; 
//                Debug.d("processing write event");
                newState = conn.processWriteEvent();
                Log.d("wifikeyboard", "finished write event");
              } else {
                continue;
              }
              if (newState == prevState) continue;
              key.interestOps(
                  (newState == HttpConnection.SELECTOR_WAIT_FOR_NEW_INPUT ? SelectionKey.OP_READ : 0) |
                  (newState == HttpConnection.SELECTOR_WAIT_FOR_OUTPUT_BUFFER ? SelectionKey.OP_WRITE : 0));
            } catch (IOException io) {
//              Debug.d("CONNECTION CLOSED from " + 
//                  conn.getClient().socket().getRemoteSocketAddress());
              if (key != null) key.cancel();
              conn.getClient().close();
            } catch (NumberFormatException e) {
              if (key != null) key.cancel();
              conn.getClient().close();
            }
          }
        }
      }
        
//      Debug.d("Server socket close");
      selector.close();
      ch.close();
    } catch (IOException e) {
      Debug.e("network loop terminated", e);
    }
    synchronized (this) {
      finished = true;
      notifyAll();
    }
  }
  
  private final synchronized boolean isDone() {
    return isDone;
  }
  
  public synchronized void finish() {
    isDone = true;
    selector.wakeup();
    while (!finished)
      try {
        wait();
      } catch (InterruptedException e) {
        break;
      }
  }
  
  public abstract HttpConnection newConnection(SocketChannel ch);
  protected abstract void onEvent(Object ev);
}
