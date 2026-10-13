# Momentum Android

Native Android app for Momentum: a Persian-only monthly nutrition and training plan.
Built with Kotlin + Jetpack Compose and published on Cafe Bazaar first.

This app and the website are **separate products**. They share only the server:
the Supabase project (database, auth, Edge Functions) that lives in the website repo
[`pooriapm/Momentum`](https://github.com/pooriapm/Momentum) under `supabase/`.

```text
C:\Users\poori\
├── Momentum\            website (React/Vite) + shared server (supabase/)
└── Momentum-android\    this repo: native Android app
```

## What the app does

| Screen | Content |
| --- | --- |
| Sign in | Email + password, same accounts as the website |
| Onboarding | ~20 short screens: goal, body, health screening, food, training, consents, review, then the plan is built |
| امروز (Today) | Calories and macros eaten vs target, next step, daily check-in, meals (pick option, mark eaten, recipe sheet), workout |
| برنامه (Plan) | 30-day calendar strip and the chosen day's meals and workout |
| پیشرفت (Progress) | Weekly adherence bars and weight trend from check-ins |
| حساب من (Me) | Membership (Cafe Bazaar), safety, privacy, terms, sign out |

Everything is right-to-left Persian with the Vazirmatn typeface, Persian digits and Jalali dates.

### Onboarding

Answers use the website's keys and are saved after every screen to the same
`onboarding_drafts` row, so a user can start on the phone and finish on the website
(or the other way round). The final step runs `complete-onboarding` and
`generate-monthly-plan` exactly like the website. Health answers that need a
professional (urgent symptoms, pregnancy, eating-disorder risk, high-risk conditions)
stop automatic planning, as on the website.

Not in the app yet (still on the website): importing an outside plan and uploading a
body-composition report.

### Design system

Material 3 components with the website's dark tokens: one question per screen with
a determinate progress bar and a pinned primary button, selectable cards and chips with
TalkBack roles, segmented buttons for yes/no and 1–5 scores, bottom sheets for meal
detail and check-in, a Jalali date picker (the M3 date picker is Gregorian only), and
48dp+ touch targets. Shared pieces live in `ui/components/Components.kt`.

## Payments

One consumable in-app product (`membership_month`, 490,000 toman) = 30 days of
membership. No auto-renewal; paying early extends the current period.

1. The app buys the product with [Poolakey](https://github.com/cafebazaar/Poolakey);
   the purchase payload is the account id.
2. The app sends the purchase token to the `payments` Edge Function
   (`action: bazaar-verify`).
3. The server validates the token with the Cafe Bazaar developer API and grants
   the period (`grant_paid_month`). The client never grants membership itself.
4. Only after the server confirms does the app consume the purchase. If the app
   closes in between, the unconsumed purchase is verified again on next launch.

The website sells the same membership through Zarinpal; both end up in the same
`payments` and `entitlements` tables.

## Setup

Requirements: JDK 17+ and the Android SDK (platform 36).

1. Copy `momentum.properties.example` to `momentum.properties` (git-ignored) and fill in:
   - `supabaseUrl`, `supabaseAnonKey`: the same public values the website uses.
   - `bazaarRsaKey`: Cafe Bazaar panel → in-app billing → RSA public key.
   - `bazaarProductId`: must match `BAZAAR_PRODUCT_ID` on the server.
2. Create `local.properties` with `sdk.dir=C:\\Users\\<you>\\Android\\Sdk` (Android Studio does this for you).
3. Build:

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/`.

### Server configuration (in the website repo)

The `payments` Edge Function needs these secrets: `PAYMENTS_MASTER_ENABLED=true`,
`BAZAAR_PACKAGE_NAME=com.pooriapm.momentum`, `BAZAAR_PRODUCT_ID`,
`BAZAAR_CLIENT_ID`, `BAZAAR_CLIENT_SECRET`, `BAZAAR_REFRESH_TOKEN`
(see `supabase/.env.example` there).

## Release

- `applicationId` is `com.pooriapm.momentum` and is permanent once published on Bazaar.
- Bump `versionCode`/`versionName` in `app/build.gradle.kts` for every upload.
- Sign release builds with a keystore kept outside the repo (`*.jks` and `*.keystore` are ignored).

```bash
./gradlew assembleRelease
```

## Project layout

```text
app/src/main/java/com/pooriapm/momentum/
├── MainActivity.kt        entry point, connects billing
├── data/Api.kt            Supabase auth (GoTrue REST) + Edge Function calls
├── data/Models.kt         dashboard response models
├── billing/BazaarBilling.kt   Poolakey purchase → server verify → consume
├── onboarding/            questionnaire model, state and screens
└── ui/                    tabs, sheets, components, theme, Persian formatting
```

Fonts: Vazirmatn (SIL Open Font License, `licenses/Vazirmatn-OFL.txt`).

No automated tests by design: the goal is a fast launch to see whether people
use the app. Verify changes by building and running it.
