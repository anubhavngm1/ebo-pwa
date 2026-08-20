package com.ebostay.app;

import android.webkit.JavascriptInterface;

/**
 * Exposed to WebView as window.EboNative
 * Presence of this object tells the PWA to skip web push / install banners.
 */
public class EboNativeBridge {
    private final MainActivity activity;

    public EboNativeBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public boolean isNative() {
        return true;
    }

    @JavascriptInterface
    public String getPlatform() {
        return "android";
    }

    @JavascriptInterface
    public String getFcmToken() {
        return activity.getFcmToken();
    }

    @JavascriptInterface
    public String getAppVersion() {
        return "1.0.0";
    }
}
