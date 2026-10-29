# StudyPulse

A study planner that turns any day-by-day plan into a daily schedule with focus timers, progress tracking and in-app study material. Each person must sign in with Google, uploads their own plan, and gets their own schedule.

## Requirements
- Android Studio Koala (2024.1.1) or newer, JDK 17 or newer (the JetBrains Runtime bundled with Android Studio works)
- Android SDK Platform 34
- A Firebase project for sign-in (free to create, see below)

Open the folder that contains `settings.gradle.kts`, let Gradle sync, then run the `app` configuration. From the command line: `gradlew.bat assembleDebug` (Windows) or `./gradlew assembleDebug`. The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Turn on sign-in (Firebase)
Sign-in is required. Without this file the app builds but stays on the sign-in screen and shows a setup notice, so add the file before using it.

1. Open the Firebase console, create a project, then add an Android app with the package name `com.studypulse.app`.
2. Add your debug SHA-1 and SHA-256 keys to that app. To print them: `keytool -list -v -keystore "%USERPROFILE%\.android\debug.keystore" -alias androiddebugkey -storepass android -keypass android`
3. Build, then Authentication, Sign-in method: enable **Google** (phone sign-in is switched off in the app for now).
4. Download `google-services.json` and put it in the `app` folder (next to `app/build.gradle.kts`). Build again.
5. Phone sign-in: while testing, add test numbers under Authentication, Phone, "Phone numbers for testing" (no SMS is sent). Real SMS delivery depends on the Firebase plan and region, check the current Firebase pricing page.
6. Release builds need the release keystore's SHA-1 and SHA-256 added in step 2 as well.

## Uploading a study plan
After signing in, a person without a plan sees the upload screen. Supported files: PDF (text PDFs), Word (.docx), Excel (.xlsx), CSV and plain text.

- **Tables** (Excel, CSV, Word tables): columns named like Day, Date, Topic or Subject, Hours or Duration, and Link or Resources are recognised. Several topic columns become separate tracks. A Subject column also creates tracks.
- **Text** (Word, PDF, TXT): headings such as `Day 1`, or lines that start with a date. Lines such as `Maths: algebra` become tracks when the same label appears on more than one day. Durations such as `(2h)`, `90 min` or `1h 30m` are read, and links are kept.
- **Dates**: dates in the file are kept, including gaps. When the file has no dates, the plan starts tomorrow and runs one day after the other. The start date can be changed with the calendar before the plan is created and later in Settings.
- Old `.doc` and `.xls` files are not read. Save them as `.docx` or `.xlsx`. Scanned PDFs without text cannot be read.

## Features
- **Today**: greeting, plan day, progress ring, a session card per track with topics and links, "Need a lighter day?" rebalancing.
- **Week**: previous, this and next week, weekly progress, expandable day cards.
- **Profile**: photo or initials, name, sign-in method, streak, topics done, hours studied, current plan, upload a new plan, sign out.
- **Settings**: reminder time per track, plan start date from a calendar, focus lock, plan maintenance, PDF library.
- **Topic links**: every topic has Video, PDF and Read buttons. Video opens a YouTube search for the topic, PDF searches for study material (a tapped PDF is saved to My books), Read opens the link from the plan.
- **Focus lock**: a running timer turns on Do Not Disturb and pins the app until you stop the session.
- **Opening animation**: logo, expanding rings and the name play every time the app is opened.

## Project layout
| Path | Purpose |
| --- | --- |
| `Theme.kt`, `Icons.kt`, `Splash.kt` | Design system, line icons, opening animation |
| `Screens.kt`, `AccountScreens.kt`, `App.kt`, `Reader.kt` | Today, Week, Settings, sign-in, upload, profile, timer, reading screens |
| `Auth.kt`, `Account.kt` | Firebase sign-in, the signed in account |
| `PlanParser.kt`, `DocText.kt`, `PlanSource.kt` | Reading uploaded files and turning them into a plan |
| `Plan.kt`, `Engine.kt`, `Store.kt`, `AppVM.kt` | Plan model, scheduling, local storage, view model |
| `app/src/main/assets/sample_plan.json` | A sample plan |
| `app/src/main/res/font` | Manrope and DM Sans (SIL Open Font License 1.1) |

## Notes
- Each account has its own plan, progress and settings on the phone. Signing out keeps them.
- Android does not let a normal app block other apps. Screen pinning is the platform feature, and you can always leave it by holding Back and Overview.
- Videos stream inside the app. PDFs are stored on the phone. Only PDF books are supported in the library.

## Version 6.0 additions
Opening glow, Free/Monthly/Yearly plans through Google Play Billing, the Pulsy owl mascot, embedded YouTube videos, and course and book suggestions for hard topics. See `FEATURES.md` and `server/README.md`.
