# Firebase setup — EBO Stay native notifications

## 1. Create project (Firebase Console)

1. Open https://console.firebase.google.com/
2. **Add project** → name: `ebo-stay` (or any name)
3. Google Analytics: optional (can skip)
4. Create project

## 2. Add Android app

1. Project overview → **Add app** → Android
2. **Android package name** (exact):
   ```
   com.ebostay.app
   ```
3. App nickname: `EBO Stay`
4. SHA-256 (optional for FCM, required later for some APIs):
   ```
   E2:0E:F7:71:59:89:DD:10:34:E5:3E:6B:5A:2A:BB:FE:32:51:DF:29:BA:EC:A9:2D:76:33:25:9C:11:B3:73:71
   ```
   (this is our debug keystore; add Play App Signing SHA when you publish)
5. Register app → **Download `google-services.json`**

## 3. Put config in the app

### Option A — GitHub (recommended for CI builds)

1. Open downloaded `google-services.json` in a text editor
2. Copy **entire** file contents
3. GitHub repo → **Settings → Secrets and variables → Actions**
4. New secret:
   - Name: `GOOGLE_SERVICES_JSON`
   - Value: paste full JSON
5. Push any small commit (or **Actions → Build Android APK → Run workflow**)
6. New APK will include Firebase

### Option B — Local file in repo

Replace:
```
android/app/google-services.json
```
with the downloaded file, commit & push.

**Important:** do not commit if the repo is public and you care about leaking the file — prefer GitHub Secret.

## 4. Enable Cloud Messaging

Firebase Console → **Build → Cloud Messaging** (or Engage → Messaging)  
No extra switch needed for basic FCM; just ensure the Android app is registered.

## 5. Test notification

1. Install the new APK (uninstall old first)
2. Open app once (so FCM token is generated)
3. Firebase Console → **Messaging → Create your first campaign → Firebase Notification messages**
4. Title/body → **Send test message** → paste FCM token  
   Token log: Android Studio Logcat filter `EboStay` or from JS `window.__EBO_FCM_TOKEN__`

## 6. Server (website) — send FCM later

When a booking is confirmed, your PHP backend should call FCM HTTP v1 API with the device token.  
We can wire `onEboFcmToken` in the PWA to save the token to your DB after login.

## Checklist

- [ ] Firebase project created
- [ ] Android app `com.ebostay.app` registered
- [ ] `google-services.json` added (secret or file)
- [ ] New APK built & installed
- [ ] Test notification received

## Server key (required to *send* notifications)

1. Firebase Console → Project settings → **Cloud Messaging**
2. If "Cloud Messaging API (Legacy)" is disabled:
   - Open Google Cloud Console → enable **Firebase Cloud Messaging API**
   - Create an API key / use Server key shown under Legacy
3. On server create `includes/fcm_config.php`:

```php
<?php
define('FCM_SERVER_KEY', 'YOUR_SERVER_KEY');
define('FCM_CRON_SECRET', 'random_secret_for_cron');
```

4. Cron (abandoned search, every 15 min):
```
*/15 * * * * curl -s "https://www.ebostay.com/api/fcm-cron.php?key=random_secret_for_cron"
```

5. Admin send UI: https://www.ebostay.com/admin-pwa/push.html (while logged in as admin)
