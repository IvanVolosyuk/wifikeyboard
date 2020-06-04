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
import java.io.InvalidObjectException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import com.volosyukivan.HttpConnection.ConnectionFailureException;

import android.os.Handler;
import android.util.Log;


public abstract class HttpServer extends Thread {

  // private for network thread
  private Selector selector;
  private ServerSocketChannel ch;

  // FIXME: get rid of?
  private Handler handler;
  

  public HttpServer(ServerSocketChannel ch) {
    this.handler = new Handler();
    this.ch = ch;
    try {
      System.setProperty("java.net.preferIPv6Addresses", "false");
      selector = Selector.open();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
  
  abstract class Action {
    public abstract Object run();
  }
  
  private class ActionRunner implements Runnable {
    private Action action;
    private boolean finished; 
    private Object actionResult;
    
    private void setAction(Action action) {
      this.action = action;
      this.finished = false;
    }
    
    public void run() {
      actionResult = action.run();
      synchronized (this) {
        finished = true;
        notify();
      }
    }
    
    public synchronized Object waitResult() {
      while (!finished) {
        try {
          wait();
        } catch (InterruptedException e) {
          actionResult = null;
          return null;
        }
      }
      return actionResult;
    }
  };
  ActionRunner actionRunner = new ActionRunner();
  
  /**
   * Invoke from network thread and execute action on main thread (synchronized).
   * @param action to run on main thread
   * @return object return by the action
   */
  public Object runAction(Action action) {
    actionRunner.setAction(action);
    handler.post(actionRunner);
    return actionRunner.waitResult();
  }

  interface Update extends Runnable {}
  
  ArrayList<Update> pendingUpdates = new ArrayList<Update>();
  
  public void postUpdate(Update update) {
    pendingUpdates.add(update);
    try {
    selector.wakeup();
    } catch (Throwable t) {}
  }
  
  protected void setResponse(KeyboardHttpConnection con, ByteBuffer out) {
    try {
    con.key.interestOps(SelectionKey.OP_WRITE);
    con.outputBuffer = out;
    } catch (Exception e) {
      Log.e("wifikeyboard", "setResponse failed for hang connection", e);
    }
  }
  
  @Override
  public void run() {
//    Debug.d("HttpServer started listening");
    try {
      ch.configureBlocking(false);
      SelectionKey serverkey = ch.register(selector, SelectionKey.OP_ACCEPT);
      final ArrayList<Update> newUpdates = new ArrayList<Update>();
      Action checkUpdates = new Action() {
        @Override
        public Object run() {
          for (Update u : pendingUpdates) {
            newUpdates.add(u);
          }
          pendingUpdates.clear();
          return null;
        }
      };

      while (true) {
        newUpdates.clear();
        runAction(checkUpdates);
        for (Update u : newUpdates) {
          u.run();
        }
//        Debug.d("waiting for event");
        selector.select();
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
              HttpConnection.ConnectionState prevState, newState;
              if (key.isReadable()) {
                prevState = HttpConnection.ConnectionState.SELECTOR_WAIT_FOR_NEW_INPUT; 
//                Debug.d("processing read event");
//                long start = System.currentTimeMillis();
                newState = conn.newInput();
//                long end = System.currentTimeMillis();
//                Debug.d("delay = " + (end - start));

//                int size = android.os.Debug.getThreadAllocSize();
//                android.os.Debug.stopAllocCounting();
//                Log.d("wifikeyboard", "finished read event, allocs = " + size);
//                android.os.Debug.startAllocCounting();

              } else if (key.isWritable()) {
                prevState = HttpConnection.ConnectionState.SELECTOR_WAIT_FOR_OUTPUT_BUFFER; 
//                Debug.d("processing write event");
                newState = conn.newOutputBuffer();
//                Log.d("wifikeyboard", "finished write event");
              } else {
                continue;
              }
              if (newState == prevState) continue;
              key.interestOps(
                  (newState == HttpConnection.ConnectionState.SELECTOR_WAIT_FOR_NEW_INPUT ? SelectionKey.OP_READ : 0) |
                  (newState == HttpConnection.ConnectionState.SELECTOR_WAIT_FOR_OUTPUT_BUFFER ? SelectionKey.OP_WRITE : 0));
            } catch (IOException io) {
//              Debug.d("CONNECTION CLOSED from " + 
//                  conn.getClient().socket().getRemoteSocketAddress());
              if (key != null) key.cancel();
              conn.getClient().close();
            } catch (ConnectionFailureException e) {
              if (key != null) key.cancel();
              conn.getClient().close();
            } catch (NumberFormatException e) {
              // FIXME: move to ConnectionFailureException
              if (key != null) key.cancel();
              conn.getClient().close();
            }
          }
        }
      }
        
    } catch (IOException e) {
      Debug.e("network loop terminated", e);
    } catch (NetworkThreadStopException e) {
      Debug.e("network thread stop requested", e);
    }
    try {
      selector.close();
    } catch (Throwable t) {}
    try {
      ch.close();
    } catch (Throwable t) {}
    onExit();
  }
  
  class NetworkThreadStopException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    public NetworkThreadStopException(String msg) {
      super(msg);
    }
  }
  
  public synchronized void finish() {
    postUpdate(new Update() {
      @Override
      public void run() {
        throw new NetworkThreadStopException("network thread stop requested");
      }
    });
  }
  
  public abstract HttpConnection newConnection(SocketChannel ch);
  /**
   * Called on the end of network thread.
   */
  protected abstract void onExit();
}
