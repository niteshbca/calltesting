# Bharat Dialer

Android phone app: choose an available calling SIM, enter a phone number, place a call, and view the latest 50 entries in the device call log. Tap a history entry to copy its number to the dial field.

## Build an installable debug APK

1. Install Android Studio (with Android SDK 35 and JDK 17).
2. Open this `BharatDialer` directory as a project, then let Gradle sync finish.
3. Select **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. Find the APK at `app/build/outputs/apk/debug/app-debug.apk` and transfer it to your Android phone.
5. Install it and allow Phone and Call logs permissions. Select the actual SIM shown before pressing CALL.

Android 8.0+; physical phone with SIM required. The app requests CALL_PHONE, READ_PHONE_STATE and READ_CALL_LOG. The selected PhoneAccountHandle is passed to TelecomManager.placeCall. Actual SIM routing can vary by device and dialer; verify on your own phone before relying on it. For private APK installation, a debug APK works. Publishing on Google Play may require a default dialer role and additional Call Log policy compliance.

This source ZIP contains no precompiled APK; it has not been compiled or device tested in the current environment.

## Build without Android Studio (GitHub Actions)

1. Sign in to GitHub and create an empty repository (choose Private if you prefer).
2. In the repository, choose **Add file > Upload files**. Extract this ZIP first, then upload all files **inside** `BharatDialer`, including the `.github/workflows/build-apk.yml` folder. Browser uploads may skip hidden `.github`; if so create `.github/workflows/build-apk.yml` through **Add file > Create new file** and paste its content.
3. Commit the files to the `main` branch. Open **Actions > Build Android APK > Run workflow** (or the push to `main` starts it automatically).
4. Once the run succeeds, open it and download the **BharatDialer-debug-apk** artifact. Extract that artifact ZIP; `app-debug.apk` is the installable file.

No Android Studio is required for this method. Internet and a GitHub account are required. GitHub Actions must be enabled for the repository. This workflow builds a debug APK for personal testing; it does not create a release-signed Play Store APK.
