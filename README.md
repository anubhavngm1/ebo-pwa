# EBO Stay — Native Android App

WebView shell for `https://www.ebostay.com/pwa/`  
Package: `com.ebostay.app`

- Web push **disabled** inside the app  
- Notifications via **native FCM only**  
- **GitHub Actions** builds the APK on every push to `main`

---

## 1. Create repo on GitHub

1. GitHub → **New repository** → name: `ebo-pwa` (public or private)
2. Do **not** add README if you will push this folder

```bash
unzip ebo-pwa.zip
cd ebo-pwa
git init
git add .
git commit -m "EBO Stay native Android + GitHub Actions build"
git branch -M main
git remote add origin https://github.com/YOUR_USERNAME/ebo-pwa.git
git push -u origin main
```

## 2. GitHub builds APK automatically

After push:

1. Open repo → **Actions** tab  
2. Workflow **Build Android APK** runs  
3. When green → open the run → **Artifacts** → download **`ebo-stay-debug-apk`**

Manual rebuild: Actions → Build Android APK → **Run workflow**

## 3. Optional secrets (Firebase / signed release)

Repo → **Settings → Secrets and variables → Actions**

| Secret | Purpose |
|--------|---------|
| `GOOGLE_SERVICES_JSON` | Full contents of real `google-services.json` |
| `ANDROID_KEYSTORE_BASE64` | `base64 -w0 your.keystore` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias |
| `ANDROID_KEY_PASSWORD` | Key password |

Signed release: Actions → **Build Release APK (signed)** → Run workflow

## 4. Website patch

Upload `web-patch/pwa/index.html` so the site skips web notification banners inside the native app.

## Local build (optional)

Open `android/` in Android Studio, or:

```bash
cd android
gradle wrapper --gradle-version 8.5
./gradlew assembleDebug
```

## Firebase (push notifications)

See **[FIREBASE_SETUP.md](./FIREBASE_SETUP.md)** — create project, download `google-services.json`, add GitHub secret `GOOGLE_SERVICES_JSON`, rebuild APK.
