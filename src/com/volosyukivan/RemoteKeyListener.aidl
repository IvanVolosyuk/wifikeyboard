package com.volosyukivan;

interface RemoteKeyListener {
    void keyEvent(int code, boolean pressed);
    void charEvent(char code);
}