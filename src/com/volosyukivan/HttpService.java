package com.volosyukivan;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ServerSocketChannel;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;

public class HttpService extends Service {
  RemoteKeyListener listener;
  String htmlpage;
  int port;
  ServerSocketChannel socket;

  final IBinder mBinder = new RemoteKeyboard.Stub() {
    //@Override
    public void registerKeyListener(final RemoteKeyListener listener)
        throws RemoteException {
      HttpService.this.listener = listener;
    }
    //@Override
    public void unregisterKeyListener(final RemoteKeyListener listener)
        throws RemoteException {
      if (HttpService.this.listener == listener) {
        HttpService.this.listener = null;
        Debug.d("Removed listener");
      }
    }

    @Override
    public int getPort() throws RemoteException {
      return port;
    }
  };
  
  private ServerSocketChannel makeSocket() {
    ServerSocketChannel ch;

    try {
      ch = ServerSocketChannel.open();
      ch.socket().bind(new java.net.InetSocketAddress(7777));
      return ch;
    } catch (IOException e) {}
    
    for (int i = 1; i < 9; i++) {
      try {
        ch = ServerSocketChannel.open();
        ch.socket().bind(new java.net.InetSocketAddress(i * 1111));
        return ch;
      } catch (IOException e) {}
    }
    for (int i = 2; i < 64; i++) {
      try {
        ch = ServerSocketChannel.open();
        ch.socket().bind(new java.net.InetSocketAddress(i * 1000));
        return ch;
      } catch (IOException e) {}
    }
    try {
      ch = ServerSocketChannel.open();
      ch.socket().bind(new java.net.InetSocketAddress(7777));
      return ch;
    } catch (Throwable t) {
      throw new RuntimeException(t);
    }
  }
  
  @Override
  public void onCreate() {
    super.onCreate();
    Debug.d("HttpService started");

    socket = makeSocket();
    port = socket.socket().getLocalPort();
    server = new HttpServer(this, socket);
    InputStream is = getResources().openRawResource(R.raw.key);
    int pagesize = 32768;
    byte[] data = new byte[pagesize];
    
    try {
      int offset = 0;
      while (true) {
        int r = is.read(data, offset, pagesize - offset);
        if (r < 0) break;
        offset += r;
        if (offset >= pagesize) throw new IOException("page is too large to load");
      }
      htmlpage = new String(data, 0, offset);
    } catch (IOException e) {
      throw new RuntimeException("failed to load html page", e);
    }
    server.start();
  }
  
  @Override
  public void onDestroy() {
    server.finish();
    try {
      socket.close();
    } catch (IOException e) {
    }
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }
  
  HttpServer server;
}
