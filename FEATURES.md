# StudyPulse features (6.6)

| Feature | Where |
| --- | --- |
| Gemini-style opening glow with the Pulsy owl | `Splash.kt`, `Mascot.kt` |
| Pulsy, the 3D-style owl mascot (bobs, blinks, flaps; cheers when Premium is active) | `Mascot.kt` (splash and Premium screen) |
| Free / Monthly / Yearly plans, Google Play Billing | `PlayBilling.kt`, `Billing.kt`, `AppVM.kt` (premium section) |
| Busy screen: silver hovering logo for sign in/out, blurred app with a spinning-logo task pill for everything else | `Busy.kt`, `AppVM.kt` (`busyBegin`, `busyFinish`, `busyDo`) |
| Ads on the Free plan | `Ads.kt` |
| YouTube player (6.6: real embed host, IFrame API, loading and error screen with Try again / Open in YouTube) | `Youtube.kt`, `Reader.kt` (`WebScreen`, `VideoBridge`) |
| Topic difficulty, courses, books | `SmartResources.kt`, `Resources.kt`, `TopicLinks.kt` |

## Google Play Billing setup
1. Play Console > your app > Monetize > Subscriptions. Create two subscriptions:
   - product ID `studypulse_monthly`, base plan 1 month, price Rs 99
   - product ID `studypulse_yearly`, base plan 1 year, price Rs 900
   Activate both.
2. Upload a signed build to the Internal testing track and add your Google account as a tester and a license tester (Setup > License testing) so test purchases are free.
3. Install the app from the Play testing link. Purchases do not work in a sideloaded debug APK; the Premium screen says so.
- Prices on the Premium screen come from Play. Plans renew until cancelled in Google Play. The phone re-reads the Play subscription at every sign-in and app start.
- For stronger protection, verify purchase tokens on a server with the Play Developer API (the app currently trusts the Play library on the device).

## Other settings (optional, in `local.properties`)
- `YOUTUBE_API_KEY`: real video search per topic; videos fall back to a YouTube search without it. Restrict the key to this app in Google Cloud.
- `ADMOB_APP_ID`, `ADMOB_BANNER_ID`: your AdMob IDs. Google's test IDs are used until set.
- Free plan limit: `Entitlement.FREE_PLANS_PER_MONTH` in `Billing.kt` (3).

## Behaviour
- Video chip: the plan's own YouTube link, else the top API result, else a YouTube search. Playback uses `https://www.yout-ube-nocookie.com/embed/ID?playlist=ID&autoplay=1&iv_load_policy=3&loop=1&start=`. The host is the `EMBED_HOST` constant in `Youtube.kt`; YouTube's real privacy domain is `www.youtube-nocookie.com`, so switch it there if videos do not play.
- A topic is ADVANCED when keywords, length or duration push `TopicClassifier.score` to 2 or more. Advanced topics get a Courses chip (Coursera, Udemy and edX searches, Premium only) and 1 to 2 books from a built-in shelf.
- Yearly "priority processing": `Entitlement.priority()` exists, but nothing is queued yet, so it has no effect.

## Busy screen
- Shown for: sign in (Google, phone code, verify), sign out, reading and building a plan, the sample plan, starting and ending a study session, taking a day off, rebuilding the plan, changing the start date, adding or downloading a PDF, and opening Google Play to subscribe.
- It stays until the action is finished AND at least `BUSY_MIN_MS` (10 seconds, top of `Busy.kt`) have passed. Set it to a smaller number to shorten every action at once.
- Not shown for tiny taps such as ticking a topic done. To add it to another action: `vm.busyDo("Saving") { ...work... }`, or `vm.busyBegin("Saving")` at the start and `vm.busyFinish()` at the end.
- Background blur needs Android 12 or newer; older phones get the dark cover only.

## Release signing (for Google Play)
Add to `local.properties` (use forward slashes in the path):
```
STORE_FILE=D:/keys/studypulse.jks
STORE_PASSWORD=your-store-password
KEY_ALIAS=studypulse
KEY_PASSWORD=your-key-password
```
Then run the updater with `-Aab` to get `app/build/outputs/bundle/release/app-release.aab`. Keep the keystore and its passwords safe and backed up.

## Video, upload screen and night mode (6.2)
- Every video (in-app YouTube player, or a site's own full-screen button) plays full screen and landscape; leaving the video restores the normal orientation and system bars. Code: `WebScreen` in `Reader.kt`.
- Upload screen: no explanations. The title "Upload your plan" is typed letter by letter, held, backspaced and repeated (`TypingTitle` in `AccountScreens.kt`). The Browse files box has a moving RGB outline and glow (silver shimmer in night mode).
- Night mode: Settings > Night mode. Black and silver only, saved on the phone. All colours go through `C` in `Theme.kt` (`Night.kt` holds the switch).


## 6.3: extraction engine and Phase 1
| Feature | Where |
| --- | --- |
| Engines, tried in order: day-by-day plan (rules, exact) > smart AI engine > syllabus rules | `Syllabus.kt` (`Extractor`, `AiExtract`, `SyllabusRules`, `DraftCheck`) |
| OCR for scanned PDFs and photos (Google ML Kit, on the phone) | `Ocr.kt`, `PlanSource.kt` |
| Editable topic list (rename, reorder, merge, delete, add), then exam date and daily time | `ImportStages.kt`, `AppVM.kt` (draft functions) |
| Scheduler and "does it fit?" check (the code decides the schedule, never the AI) | `Syllabus.kt` (`Scheduler`) |
| Reschedule missed topics within the daily time | `Progress.kt` (`Engine.reschedule`) |
| Streak, weekly goal, badges, readiness screen (tap the streak at the top) | `Progress.kt`, `Readiness.kt` |
| 10-second check-in after a session (finished? easy / okay / hard) | `Readiness.kt` (`CheckinDialog`) |
| Pulsy moods (cheer, sad) | `Mascot.kt` |
| Study goals in Settings and the engine switch | `Readiness.kt` (`GoalsCard`) |

## Smart (AI) engine setup
Both engines are free for everyone and the smart engine runs first by default. It needs a Gemini API key; without one the rules on the phone are used.
1. Create a key at aistudio.google.com/apikey.
2. Add this line to `local.properties` in the project folder: `GEMINI_API_KEY=your_key`
3. In Google Cloud Console > APIs & Services > Credentials, open the key and under API restrictions choose Restrict key > Generative Language API. Do not add an Android app restriction: the app calls the API directly and does not send the package and certificate headers that restriction needs, so every request would be refused.
4. The text of an uploaded syllabus is sent to Google's Gemini API when the smart engine runs. Turn it off in Settings > Study goals > Smart extraction.
- Files that already are a day-by-day plan keep their days, tracks and links and skip the AI.
- Every AI answer is checked (no empty or duplicate topics, minutes 10 to 180, enough topics for the document length), retried once, then the rules take over.
- Daily cap: the app makes at most `AiExtract.DAILY_LIMIT` (6) AI requests a day; after that, or when Google says the free quota is used up, the rules on the phone take over automatically. The count is shown in Settings > Study goals.
- Keep billing switched off on the Google Cloud project that owns the key, so the free quota is a hard stop and nothing can be charged.
- All time numbers are estimates and are labelled that way.


## 6.4: Phase 2 (AI quizzes and spaced revision)
| Feature | Where |
| --- | --- |
| Quiz maker: 5 multiple-choice questions plus 5 flashcards per topic, checked before they are shown | `Quiz.kt` (`QuizApi`, `QuizCheck`) |
| Quiz screen with answer feedback, score, revision date and flashcards | `QuizScreen.kt` |
| Spaced repetition (SM-2 style): under 70% the topic comes back tomorrow, otherwise 1 day, 6 days, then longer | `Quiz.kt` (`Sm2`, `Revision`) |
| Entry points: "Save and take a quiz" after a session, "Revision due" and "Quiz yourself" on the Readiness screen | `Readiness.kt` |
| Daily limit: Free 3 quizzes a day, Premium 15 | `Quiz.kt` (`Quiz.FREE_DAILY`, `Quiz.PREMIUM_DAILY`), `AppVM.kt` |
| Editable topics before the plan is built, OCR for scanned PDFs | already in 6.3 |

- The quiz is written by the same Gemini key as the extraction engine (`GEMINI_API_KEY`). A quiz is kept after it is written, so reviewing it again costs nothing; a failed quiz does not use up the day's allowance.
- There is no Cloud Function: Firebase Cloud Functions need the paid Blaze plan. The quiz code lives in `QuizApi.generate`, so it can move to a function later by changing that one call. Until then the daily limit is counted on the phone: a fair-use limit that clearing app data would reset, not a security check.
- Quizzes are AI-written and can contain mistakes; the screen says so.


## 6.5: Phase 3 (resource quality)
| Feature | Where |
| --- | --- |
| The AI writes search phrases only. Anything that looks like a link or web address is discarded and a plain phrase from the topic is used instead | `Finder.kt` (`QueryWriter`) |
| Real results are fetched and ranked: YouTube (title match, known teaching channel, length, reach, embeddable and public) and Google Books (title match, reader ratings, no summaries or study guides) | `Finder.kt` (`VideoRank`, `BookRank`) |
| Every link is checked before it is shown and again every 7 days | `Finder.kt` (`LinkCheck`, `Finder.find`) |
| "Report broken link" hides that link and searches the topic again | `Resources.kt`, `Finder.kt` (`ResStore.report`) |
| One-line "Why" on every video and book | `Resources.kt` |
| "Free PDF" appears only when Google Books offers the full text and the file itself was fetched and starts with the PDF signature. The old PDF chip is now "PDF search" and says it is only a search | `Finder.kt` (`LinkCheck.isPdf`), `TopicLinks.kt`, `Resources.kt` |

- There is no server. The fetching, ranking and checking run on the phone. Reports stay on the phone too; nobody else sees them.
- Needs `YOUTUBE_API_KEY` (videos) and `GEMINI_API_KEY` (search phrases) in `local.properties`. Google Books needs no key. Without a key the app says so and falls back: a YouTube search for videos, plain phrases for searches.
- Free-quota guards: 40 video lookups a day (each costs about 101 of YouTube's 10,000 free daily units), 20 AI search-phrase requests a day. Results are kept for a week, so reopening a topic costs nothing.
- Courses stay as searches on Coursera, Udemy and edX built from the AI's phrase, and are labelled as searches, because those sites have no free open search interface.
- Ranking is a simple score, not a guarantee of quality. Results are suggestions.

## 6.7: task card and video fix
- Apart from signing in, signing out and sending the code (silver logo, unchanged), every task now shows a small card on the page: no dimming, no blur. The StudyPulse logo turns while the task runs, and every step is listed with what was read (file type, pages, characters, lines, topics, units, days, reminders) before the next step starts.
- Reading a file, building a plan, importing and downloading a PDF all report their steps this way.
- Videos: hardware video layer, browser-style user agent, third-party cookies on, redraw after rotating or returning to the app, and the player's own full-screen button is off (the app already plays full screen).
- 6.7 fix: file reading, plan building and schedule creation show their steps inside the upload page, under the title (no overlay, no blur, no dark screen); each step fades in after the previous one finishes.
- 6.7 fix: the video "Loading video" cover now lifts as soon as the player is ready or playing (the page-to-app message had the wrong signature, so the cover never cleared even though sound played).
- 6.7b: upload-page tasks (reading, building) now show ONE line in the title strip under Back: each step or detail replaces the one before it. Nothing covers the page. Taps for a second task are ignored while one runs.
- 6.7b: videos: plain youtube.com / youtu.be links also use the embed player; a start-up script hides VP9/AV1 so phones that cannot decode them get H.264 (sound with no picture); needs androidx.webkit (added to build.gradle.kts).
- 6.7c: videos work on more phones: three players are tried automatically (scripted player, plain frame, YouTube mobile page); a "No picture? Try another player" button is always on the video; an old Android System WebView is detected with an Update button; "Open in YouTube" stays as the last resort.
- 6.7d: on the upload / building-plan page the Back button and the title strip are now fixed at the top and only the content below scrolls, so the one-line task animation is always in view on every stage (topics, exam date and time, preview). The page also returns to the top when the stage changes.
- 6.7e: videos open with the YouTube page player first (the one that showed the picture on the test phone), then the scripted player, then the plain frame. Close and "Try another player" now sit in their own bar above the video instead of on top of YouTube's header; YouTube's own header and "Open App" banner are hidden, and the video is started automatically.
- 6.7f: the Video button on every topic no longer loads one chosen video. The smart engine (GEMINI_API_KEY) writes a proper YouTube-style lesson title for the topic, it is kept for that topic, and the YouTube results for that title open inside the app. Without the key the title is made from the topic text. The steps (topic, title, opening YouTube) show on the task card. YouTube pages in the app get the same phone-compatibility settings as the video player.
- 6.7g: fixed a build error in the video bar (Reader.kt).
- 6.7h: the Video button now opens the video screen at once and shows its steps (topic, title, opening YouTube) as the same one-line strip used on the upload page; the floating card is gone for this task.
