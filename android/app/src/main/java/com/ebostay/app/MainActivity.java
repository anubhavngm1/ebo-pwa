package com.ebostay.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Native shell for EBO Stay customer PWA.
 * Firebase/FCM is optional — missing google-services must NOT crash the app.
 */
public class MainActivity extends AppCompatActivity {

    private static final String TAG = "EboStay";
    private static final String PWA_URL = "https://www.ebostay.com/pwa/";
    private static final int FILE_CHOOSER_REQ = 1001;
    private static final int NOTIF_PERM_REQ = 1002;

    private WebView webView;
    private ProgressBar progress;
    private View splashView;
    private ValueCallback<Uri[]> filePathCallback;
    private String fcmToken = "";
    private boolean splashHidden = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Splash theme is set in Manifest — window shows branded bg immediately (no black flash)
        super.onCreate(savedInstanceState);
        try {
            setContentView(R.layout.activity_main);
            webView = findViewById(R.id.webview);
            progress = findViewById(R.id.progress);
            splashView = findViewById(R.id.splash);
            setupWebView();
            requestNotificationPermission();
            // Create heads-up notification channel as early as possible
            try { EboFirebaseMessagingService.ensureChannel(this); } catch (Throwable ignored) {}
            // FCM only if Firebase is configured — never crash
            safeInitFcm();
            webView.loadUrl(resolveAppUrl(getIntent()));
            // Safety: hide splash after 8s even if page stuck
            if (splashView != null) {
                splashView.postDelayed(this::hideSplash, 8000);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Fatal in onCreate", t);
            finish();
        }
    }

    private void hideSplash() {
        if (splashHidden || splashView == null) return;
        splashHidden = true;
        try {
            splashView.animate()
                    .alpha(0f)
                    .setDuration(320)
                    .withEndAction(() -> {
                        splashView.setVisibility(View.GONE);
                        // Restore normal window background after splash
                        getWindow().setBackgroundDrawableResource(android.R.color.white);
                    })
                    .start();
        } catch (Throwable t) {
            splashView.setVisibility(View.GONE);
        }
    }

    private void setupWebView() {
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setUserAgentString(s.getUserAgentString() + " EboStayApp/1.0 EBOStayApp/1.0");
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.addJavascriptInterface(new EboNativeBridge(this), "EboNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (uri == null) return false;
                String scheme = uri.getScheme() == null ? "" : uri.getScheme();
                String host = uri.getHost() == null ? "" : uri.getHost();
                String url = uri.toString();

                // Deep link back from Google OAuth (Custom Tab / external browser)
                if ("ebostay".equalsIgnoreCase(scheme)) {
                    handleOAuthDeepLink(uri);
                    return true;
                }

                // google-login.php MUST stay in WebView (sets is_app via EboStayApp UA + state).
                // Only Google's own auth pages open in Chrome so the phone account picker works.
                // After login, google-callback redirects to ebostay://oauth-success?token=...
                boolean isGoogleAuth = host.contains("accounts.google")
                        || host.contains("accounts.youtube.com")
                        || (host.contains("google.") && (url.contains("/o/oauth2")
                            || url.contains("ServiceLogin")
                            || url.contains("signin/oauth")
                            || url.contains("AccountChooser")));
                if (isGoogleAuth) {
                    try {
                        Intent i = new Intent(Intent.ACTION_VIEW, uri);
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(i);
                    } catch (Exception e) {
                        Log.w(TAG, "open Google auth failed", e);
                    }
                    return true;
                }

                // Stay in WebView for our site + payment gateways
                if (host.contains("ebostay.com") || host.contains("payu")
                        || host.contains("paypal") || host.contains("razorpay")
                        || host.contains("checkout.razorpay")) {
                    return false;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, uri));
                } catch (Exception ignored) {}
                return true;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                if (progress != null) progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (progress != null) progress.setVisibility(View.GONE);
                hideSplash();
                try { CookieManager.getInstance().flush(); } catch (Throwable ignored) {}
                view.evaluateJavascript(
                        "window.__EBO_NATIVE__=true;window.EboNativeApp=true;", null);
                if (fcmToken != null && !fcmToken.isEmpty()) {
                    injectFcmToken(fcmToken);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (progress == null) return;
                progress.setProgress(newProgress);
                progress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> cb,
                                             FileChooserParams fileChooserParams) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    startActivityForResult(fileChooserParams.createIntent(), FILE_CHOOSER_REQ);
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
                return true;
            }
        });
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIF_PERM_REQ);
            }
        }
    }

    /** Firebase is optional — placeholder google-services must not kill the app. */
    private void safeInitFcm() {
        try {
            Class.forName("com.google.firebase.FirebaseApp");
            com.google.firebase.FirebaseApp.initializeApp(this);
            com.google.firebase.messaging.FirebaseMessaging.getInstance().getToken()
                    .addOnCompleteListener(task -> {
                        if (!task.isSuccessful() || task.getResult() == null) {
                            Log.w(TAG, "FCM token unavailable (Firebase not configured?)");
                            return;
                        }
                        fcmToken = task.getResult();
                        injectFcmToken(fcmToken);
                    });
        } catch (Throwable t) {
            Log.w(TAG, "Firebase/FCM disabled: " + t.getMessage());
        }
    }

    private void injectFcmToken(String token) {
        if (webView == null || token == null || token.isEmpty()) return;
        String safe = token.replace("\\", "\\\\").replace("'", "\\'");
        // Expose token + auto-register with server so Admin push reaches this device
        String js = "window.__EBO_FCM_TOKEN__='" + safe + "';"
                + "window.__EBO_NATIVE__=true;"
                + "if(window.onEboFcmToken){try{window.onEboFcmToken('" + safe + "');}catch(e){}}"
                + "(function(t){try{"
                + "fetch('/pwa/api/fcm.php?action=register',{method:'POST',credentials:'same-origin',"
                + "headers:{'Content-Type':'application/json'},"
                + "body:JSON.stringify({token:t,platform:'android'})}).catch(function(){});"
                + "}catch(e){}})('" + safe + "');";
        webView.post(() -> webView.evaluateJavascript(js, null));
    }


    /** Extract segment after /name/ in path, e.g. /package/goa-tour/ → goa-tour */
    private static String pathSegmentAfter(String path, String name) {
        if (path == null || name == null) return null;
        String marker = "/" + name + "/";
        int i = path.toLowerCase().indexOf(marker);
        if (i < 0) return null;
        String rest = path.substring(i + marker.length());
        int slash = rest.indexOf('/');
        if (slash >= 0) rest = rest.substring(0, slash);
        rest = rest.trim();
        if (rest.isEmpty() || rest.contains(".php")) return null;
        return rest;
    }

    /** Handle ebostay://oauth-success?token=... from Google callback */
    private void handleOAuthDeepLink(Uri uri) {
        if (uri == null) return;
        String host = uri.getHost() == null ? "" : uri.getHost();
        // ebostay://oauth-success?token=xxx  → host is "oauth-success"
        if ("oauth-success".equalsIgnoreCase(host) || "oauth".equalsIgnoreCase(host)) {
            String token = uri.getQueryParameter("token");
            if (token != null && !token.isEmpty() && webView != null) {
                String loginUrl = "https://www.ebostay.com/pwa/index.html?gauth_token="
                        + Uri.encode(token);
                Log.d(TAG, "OAuth success — loading PWA with gauth_token");
                webView.loadUrl(loginUrl);
            }
        }
    }

    private String resolveAppUrl(Intent intent) {
        if (intent == null || intent.getData() == null) return PWA_URL;
        Uri uri = intent.getData();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();

        // App opened via ebostay://oauth-success?token=...
        if ("ebostay".equalsIgnoreCase(scheme)) {
            String host = uri.getHost() == null ? "" : uri.getHost();
            if ("oauth-success".equalsIgnoreCase(host) || "oauth".equalsIgnoreCase(host)) {
                String token = uri.getQueryParameter("token");
                if (token != null && !token.isEmpty()) {
                    return "https://www.ebostay.com/pwa/index.html?gauth_token=" + Uri.encode(token);
                }
            }
            return PWA_URL;
        }

        String host = uri.getHost() == null ? "" : uri.getHost();
        if (!host.contains("ebostay.com")) return PWA_URL;
        String path = uri.getPath() == null ? "/" : uri.getPath();
        String query = uri.getEncodedQuery() == null ? "" : ("?" + uri.getEncodedQuery());
        String fragment = uri.getEncodedFragment() == null ? "" : ("#" + uri.getEncodedFragment());

        // Pretty URLs: /package/{slug}/  or  package.php?slug=
        String pkgSlug = pathSegmentAfter(path, "package");
        if (pkgSlug == null || pkgSlug.isEmpty()) {
            pkgSlug = uri.getQueryParameter("slug");
            if (pkgSlug != null && !path.contains("package")) pkgSlug = null;
        }
        // Only treat as single-package page (not /packages/ list)
        boolean isPackageDetail = path.contains("/package/")
                || path.endsWith("package.php")
                || (path.contains("package.php") && uri.getQueryParameter("slug") != null);
        if (isPackageDetail) {
            if (pkgSlug == null || pkgSlug.isEmpty()) {
                pkgSlug = uri.getQueryParameter("slug");
            }
            if (pkgSlug != null && !pkgSlug.isEmpty()
                    && !pkgSlug.equals("packages") && !pkgSlug.contains("tour-packages")) {
                return "https://www.ebostay.com/pwa/?package=" + Uri.encode(pkgSlug);
            }
        }
        if (path.equals("/packages") || path.equals("/packages/") || path.contains("packages.php")
                || path.matches(".*/packages/?")) {
            return "https://www.ebostay.com/pwa/?page=packages";
        }

        // Pretty URLs: /hotel/{slug}/  or  hotel-detail.php?id= / ?slug=
        String hotelSlug = pathSegmentAfter(path, "hotel");
        if (path.contains("hotel-detail") || path.contains("/hotel/")
                || (path.contains("hotel.php") && !path.contains("hotels"))) {
            String id = uri.getQueryParameter("id");
            if (id == null || id.isEmpty()) id = uri.getQueryParameter("hotel_id");
            if (id != null && !id.isEmpty()) {
                return "https://www.ebostay.com/pwa/?hotel_id=" + Uri.encode(id);
            }
            String hSlug = hotelSlug;
            if (hSlug == null || hSlug.isEmpty()) hSlug = uri.getQueryParameter("slug");
            if (hSlug != null && !hSlug.isEmpty() && !hSlug.equals("detail")) {
                return "https://www.ebostay.com/pwa/?hotel_slug=" + Uri.encode(hSlug);
            }
            return "https://www.ebostay.com/pwa/?page=hotels";
        }
        if (path.equals("/hotels") || path.equals("/hotels/") || path.contains("hotels.php")) {
            return "https://www.ebostay.com/pwa/?page=hotels";
        }

        // Activity: /activity.php?slug= or path
        if (path.contains("activity")) {
            String aSlug = uri.getQueryParameter("slug");
            if (aSlug == null || aSlug.isEmpty()) aSlug = pathSegmentAfter(path, "activity");
            if (aSlug != null && !aSlug.isEmpty()) {
                return "https://www.ebostay.com/pwa/?activity=" + Uri.encode(aSlug);
            }
        }

        if (path.equals("/") || path.isEmpty()) {
            return PWA_URL + (query.isEmpty() ? "" : query) + fragment;
        }
        if (path.startsWith("/pwa")) {
            return "https://www.ebostay.com" + path + query + fragment;
        }
        // Any other site link: open PWA home (keeps user in app)
        return PWA_URL;
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQ) {
            if (filePathCallback == null) return;
            Uri[] results = null;
            if (resultCode == Activity.RESULT_OK && data != null && data.getDataString() != null) {
                results = new Uri[]{Uri.parse(data.getDataString())};
            }
            filePathCallback.onReceiveValue(results);
            filePathCallback = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (webView == null || intent == null) return;
        Uri data = intent.getData();
        if (data != null && "ebostay".equalsIgnoreCase(data.getScheme())) {
            handleOAuthDeepLink(data);
            return;
        }
        webView.loadUrl(resolveAppUrl(intent));
    }

    public String getFcmToken() {
        return fcmToken != null ? fcmToken : "";
    }
}
