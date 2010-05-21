package com.volosyukivan;

import com.volosyukivan.RemoteKeyListener;

 // Declare the interface.
interface RemoteKeyboard {
    void registerKeyListener(RemoteKeyListener listener);
}