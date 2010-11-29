package com.volosyukivan;

import com.volosyukivan.RemoteKeyListener;

 // Declare the interface.
interface RemoteKeyboard {
    void registerKeyListener(RemoteKeyListener listener);
    void unregisterKeyListener(RemoteKeyListener listener);
    int getPort();
    void startTextEdit(String content);
    void stopTextEdit();
}