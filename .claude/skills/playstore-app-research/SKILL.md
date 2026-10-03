---
name: playstore-app-research
description: Research Google Play to find app niches with high search demand and weak competition, then plan an original, policy-compliant app that can earn through ads or in-app purchases. Use when the user asks which apps are most searched or most used on the Play Store, wants app ideas that can make money, wants to build "an app like X", or wants to publish and monetize an Android app.
---

# Play Store App Research

Find app niches with high demand and weak competition, then plan an original, policy-compliant app the user can build, publish and monetize.

## Hard Rule: Inspired By, Never Copied

Google Play removes copycat apps and can terminate the developer account. Never plan an app that reuses another app's:

- name, icon, logo, screenshots or store listing text
- code, images, sounds or other assets
- branding, in a way that could make a user think it is the original app or made by the same company

What is allowed and expected: solving the same user problem with your own name, design and code, plus a clear improvement. If the user asks for "the same app", explain this rule once, then continue with the compliant version.

Policy references to cite:
- Impersonation: https://support.google.com/googleplay/android-developer/answer/9888374
- Intellectual property: https://support.google.com/googleplay/android-developer/answer/9888072
- Spam and minimum functionality: https://support.google.com/googleplay/android-developer/answer/9899034

## Step 1: Collect Demand Signals

Use whatever web access the session has (WebSearch, WebFetch). Play Store pages may be blocked by the network policy; then use secondary sources and say so.

Sources, in order of value:
1. Category download rankings: AppBrain (https://www.appbrain.com/stats/android-market-app-categories), 42matters, AppMagic, Sensor Tower blog posts.
2. Play Store top charts by category: https://play.google.com/store/apps/category/TOOLS (also PRODUCTIVITY, PHOTOGRAPHY, EDUCATION, PERSONALIZATION).
3. Search autocomplete: what Play Store and Google suggest after typing "pdf", "photo", "qr", "scanner", "converter", "remove", "editor".
4. Leader app pages: install count, rating, number of reviews, last update date.

## Step 2: Score Each Niche

For each candidate niche, record the market leader and its Play Store link (`https://play.google.com/store/apps/details?id=<package>`), then score 1 to 5 on:

| Factor | 5 means |
|---|---|
| Demand | leaders have 100M+ installs, many people search for it |
| Weak leaders | leaders rated under 4.3, or heavy ads, or not updated in a year |
| Solo buildable | one developer can ship it in 2 to 6 weeks, no server needed |
| Offline | works without a backend, which keeps costs at zero |
| Monetization | ads fit naturally (repeat use) or a clear paid upgrade exists |
| Policy safety | no sensitive permissions (SMS, call log, accessibility, all-files access, VPN) |

Drop any niche that needs restricted permissions, user accounts with server costs, or licensed content. Rank by total score.

## Step 3: Read Leader Reviews

For the top 3 niches, read the 1 to 3 star reviews of the leaders. The common complaints (too many ads, paywall, missing feature, crashes, needs internet) are the differentiator for the new app.

## Step 4: Define the Original App

For the chosen niche, write:
- Original app name: check it does not resemble any leader's name or trademark.
- One-line value proposition: what it does better than the leaders.
- MVP feature list: at most 5 features.
- Tech stack: Kotlin or React Native/Expo; load the react-native-skills skill when using React Native.
- Monetization: AdMob banner plus interstitial at natural breaks, no more than one interstitial per 3 actions; optional one-time "remove ads" purchase.
- ASO: title of at most 30 characters, short description of at most 80 characters, and 5 to 10 target keywords taken from Step 1.

## Step 5: Publish Checklist

1. Google Play Console developer account (one-time 25 USD fee). New personal accounts must run a closed test with at least 12 testers for 14 days before production access.
2. Privacy policy at a public URL.
3. Data safety form that matches the app's real data use, including AdMob.
4. Target API level that meets the current Play requirement.
5. Signed Android App Bundle (.aab) using Play App Signing.
6. Store listing: original icon (512x512), feature graphic (1024x500), at least 2 screenshots, content rating questionnaire.
7. AdMob account linked; use test ad unit IDs until release, and never click your own ads.

## Output Format

Report to the user in their language:
1. A ranked table of niches: niche, leader app with Play Store link, leader installs and rating, score, why.
2. A recommended niche with the reason.
3. An original app plan from Step 4.
4. Sources used, as links. Say which numbers come from secondary sources and could not be checked against the live Play Store.
