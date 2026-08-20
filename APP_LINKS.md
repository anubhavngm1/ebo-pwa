# Open ebostay.com in the app (Android App Links)

When the app is installed, links like `https://www.ebostay.com/...` open **in the app**, not Chrome.

## 1. Upload to website (required)

Upload this file so it is publicly available:

```
https://www.ebostay.com/.well-known/assetlinks.json
https://ebostay.com/.well-known/assetlinks.json
```

Source in repo: `web-patch/.well-known/assetlinks.json`

**Content-Type** must be `application/json` (no login wall).

## 2. Put your signing key SHA-256

### Debug APK (GitHub Actions / local debug)
```bash
keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android
```
Copy **SHA256:** line into `assetlinks.json`.

### Play Store / release keystore
```bash
keytool -list -v -keystore your-release.keystore -alias YOUR_ALIAS
```
Or Play Console → App integrity → App signing key certificate → SHA-256.

You can list **both** debug + release fingerprints in the JSON array.

## 3. Rebuild & install app

Push this repo update → wait for Actions APK → install on phone.

## 4. Verify

Phone (with app installed):
```bash
adb shell pm get-app-links com.ebostay.app
```
Should show `www.ebostay.com` / `ebostay.com` as **verified**.

Or open in Chrome: `https://www.ebostay.com/pwa/` — should offer / open **EBO Stay**.

## Note
- First install after uploading `assetlinks.json` may need a few minutes for Google to verify.
- If verification fails, check JSON is exact JSON (no BOM), HTTPS, and SHA-256 matches the APK you installed.
