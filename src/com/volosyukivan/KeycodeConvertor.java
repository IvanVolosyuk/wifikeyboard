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

import android.view.KeyEvent;

public final class KeycodeConvertor {
  public static int convertKey(int code) {
    // public static final int KEYCODE_A = 29;
    // ...
    // public static final int KEYCODE_Z = 54;
    if (code >= 65 && code <= 90) {
      return code - 65 + KeyEvent.KEYCODE_A;
    }

    // public static final int KEYCODE_0 = 7;
    // ...
    // public static final int KEYCODE_9 = 16;
    if (code >= 48 && code <= 57) {
      return code - 48 + KeyEvent.KEYCODE_0;
    }

    switch (code) {
      case 9: return KeyEvent.KEYCODE_TAB;
      case 32: return KeyEvent.KEYCODE_SPACE;
      case 188: return KeyEvent.KEYCODE_COMMA;
      case 190: return KeyEvent.KEYCODE_PERIOD;
      case 13: return KeyEvent.KEYCODE_ENTER;
      case 219: return KeyEvent.KEYCODE_LEFT_BRACKET;
      case 221: return KeyEvent.KEYCODE_RIGHT_BRACKET;
      case 220: return KeyEvent.KEYCODE_BACKSLASH;
      case 186: return KeyEvent.KEYCODE_SEMICOLON;
      case 222: return KeyEvent.KEYCODE_APOSTROPHE;
      case 8: return KeyEvent.KEYCODE_DEL;
      case 189: return KeyEvent.KEYCODE_MINUS;
      case 187: return KeyEvent.KEYCODE_EQUALS;
      case 191: return KeyEvent.KEYCODE_SLASH;
      case 18: return KeyEvent.KEYCODE_ALT_LEFT;
      case 16: return KeyEvent.KEYCODE_SHIFT_LEFT;

      // public static final int KEYCODE_DPAD_UP = 19;
      // public static final int KEYCODE_DPAD_DOWN = 20;
      // public static final int KEYCODE_DPAD_LEFT = 21;
      // public static final int KEYCODE_DPAD_RIGHT = 22;
      // public static final int KEYCODE_DPAD_CENTER = 23;
      // arrow keys
      case 38: return KeyEvent.KEYCODE_DPAD_UP;
      case 40: return KeyEvent.KEYCODE_DPAD_DOWN;
      case 37: return KeyEvent.KEYCODE_DPAD_LEFT;
      case 39: return KeyEvent.KEYCODE_DPAD_RIGHT;
      // Insert
      case 112: return KeyEvent.KEYCODE_DPAD_CENTER;
      case 45: return KeyEvent.KEYCODE_DPAD_CENTER;
      
      
      // ESC
      case 27: return KeyEvent.KEYCODE_BACK;
      case 116: return KeyEvent.KEYCODE_BACK;
      // Home
      case 113: return KeyEvent.KEYCODE_MENU;
      
      case 114: return KeyEvent.KEYCODE_SEARCH;
      // case x: return KeyEvent.KEYCODE_CALL;
      // case x: return KeyEvent.KEYCODE_ENDCALL;
      
      // F9, F10
      case 121: return KeyEvent.KEYCODE_VOLUME_UP;
      case 120: return KeyEvent.KEYCODE_VOLUME_DOWN;
      case KeyboardHttpServer.FOCUS: return KeyboardHttpServer.FOCUS;
      case 36: return WiFiInputMethod.KEY_HOME;
      case 35: return WiFiInputMethod.KEY_END;
      case 17: return WiFiInputMethod.KEY_CONTROL;
      case 46: return WiFiInputMethod.KEY_DEL;

      default: return -1;
    }

    // case x: return KeyEvent.KEYCODE_SOFT_LEFT;
    // case x: return KeyEvent.KEYCODE_SOFT_RIGHT;
    // case x: return KeyEvent.KEYCODE_STAR;
    // case x: return KeyEvent.KEYCODE_POUND;
    // case x: return KeyEvent.KEYCODE_POWER;
    // case x: return KeyEvent.KEYCODE_CAMERA;
    // case x: return KeyEvent.KEYCODE_CLEAR;
    // case x: return KeyEvent.KEYCODE_ALT_RIGHT;
    // case x: return KeyEvent.KEYCODE_SHIFT_RIGHT;
    // case x: return KeyEvent.KEYCODE_SYM;
    // case x: return KeyEvent.KEYCODE_EXPLORER;
    // case x: return KeyEvent.KEYCODE_ENVELOPE;
    // case x: return KeyEvent.KEYCODE_GRAVE;
    // case x: return KeyEvent.KEYCODE_AT;
    // case x: return KeyEvent.KEYCODE_NUM;
    // case x: return KeyEvent.KEYCODE_HEADSETHOOK;
    // case x: return KeyEvent.KEYCODE_FOCUS;
    // case x: return KeyEvent.KEYCODE_PLUS;
    // case x: return KeyEvent.KEYCODE_NOTIFICATION;
    // case x: return KeyEvent.KEYCODE_SEARCH;
    // case x: return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    // case x: return KeyEvent.KEYCODE_MEDIA_STOP;
    // case x: return KeyEvent.KEYCODE_MEDIA_NEXT;
    // case x: return KeyEvent.KEYCODE_MEDIA_PREVIOUS;
    // case x: return KeyEvent.KEYCODE_MEDIA_REWIND;
    // case x: return KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
    // case x: return KeyEvent.KEYCODE_MUTE;
    //
    // META_ALT_ON = 2;
    // META_ALT_LEFT_ON = 16;
    // META_ALT_RIGHT_ON = 32;
    // META_SHIFT_ON = 1;
    // META_SHIFT_LEFT_ON = 64;
    // META_SHIFT_RIGHT_ON = 128;
    // META_SYM_ON = 4;
  }
}
