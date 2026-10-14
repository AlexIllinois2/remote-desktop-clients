package com.iiordanov.bVNC.input;

import android.content.Context;
import android.os.Handler;
import android.view.KeyEvent;

import com.undatech.opaque.RfbConnectable;

public abstract class RemoteKeyboard extends com.undatech.opaque.input.RemoteKeyboard {
    // The remote needs a moment to install clipboard data before a paste can read it.
    private final static int CLIPBOARD_PASTE_DELAY = 250;

    public RemoteKeyboard(RfbConnectable r, Context v, Handler h, boolean debugLog) {
        super(r, v, h, debugLog);
    }

    public abstract void sendMetaKey(MetaKeyBean meta);

    /**
     * Delivers text that the remote keyboard layout cannot produce, such as CJK over RDP, by
     * putting it on the remote clipboard and pasting it.
     */
    public void sendTextViaClipboard(String text) {
        rfb.writeClientCutText(text);
        try {
            Thread.sleep(CLIPBOARD_PASTE_DELAY);
        } catch (InterruptedException ignored) {
            // Paste regardless; the wait only lets the clipboard land on the remote side.
        }
        int keyCode = KeyEvent.KEYCODE_V;
        processLocalKeyEvent(keyCode, new KeyEvent(KeyEvent.ACTION_DOWN, keyCode), CTRL_MASK);
        processLocalKeyEvent(keyCode, new KeyEvent(KeyEvent.ACTION_UP, keyCode), CTRL_MASK);
    }
}
