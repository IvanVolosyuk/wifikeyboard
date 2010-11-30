package com.volosyukivan;

import com.volosyukivan.RemoteKeyListener;
import com.volosyukivan.PortUpdateListener;

 // Declare the interface.
interface RemoteKeyboard {
    void registerKeyListener(RemoteKeyListener listener);
    void unregisterKeyListener(RemoteKeyListener listener);
    void setPortUpdateListener(PortUpdateListener listener);
    void startTextEdit(String content);
    void stopTextEdit();
}