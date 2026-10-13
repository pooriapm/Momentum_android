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

| Tab | Content |
| --- | --- |
| Sign in | Email + password, same accounts as the website |
| امروز (Today) | Today's targets, meals (mark done / undo) and workout |
| برنامه (Plan) | All 30 days of the current plan, Jalali dates |
| پیشرفت (Progress) | Weekly adherence and recorded weight |
| حساب من (Me) | Membership status and purchase through Cafe Bazaar |

Everything is right-to-left and in Persian, with Persian digits and Jalali dates.
First-time setup (the onboarding questionnaire) currently opens on the website;
the plan then shows up in the app.

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
└── ui/                    Compose screens, theme, Persian formatting
```

No automated tests by design: the goal is a fast launch to see whether people
use the app. Verify changes by building and running it.
