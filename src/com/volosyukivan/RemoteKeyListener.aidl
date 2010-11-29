package com.volosyukivan;

interface RemoteKeyListener {
    void keyEvent(int code, boolean pressed);
    void charEvent(int code);
    boolean setText(String text);
    String getText();
}