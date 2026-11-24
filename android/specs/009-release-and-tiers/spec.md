# Spec 009 — GitHub release, and Free (FOSS) vs Pro

Status: plan, 2026-11-23 (user: "get this up on github soon using my baconwrappedbitcoin account. We
should have a pro tier if anything costs me money to run, otherwise all FOSS").

## Principle

Everything that runs on the tablet is free and open source (GPL-3.0), forever. Pro exists only to pay
for things the user runs and pays for on others' behalf. Anyone can self-host the backend with their own
keys and get every Pro feature for free; Pro is the convenience of using the hosted one.

## What costs money to run (backend/, on Vercel)

| Service | Used for | Cost driver | Tier |
|---------|----------|-------------|------|
| Gemini (LLM, `GEMINI_MODEL`) | `api/analyze`: turn a YouTube video into a speed/incline workout | Per request, the main cost | **Pro** (hosted); free with your own key (self-host) |
| YouTube Data API | `api/search`: video search | Free quota (10k units/day), then blocked | Free, rate-limited per install; Pro gets a higher limit |
| Upstash Redis (`KV_*`) | `api/programs`: community library, ratings | Free tier, then per request/storage | Free to browse and share; Pro if it outgrows the free tier |
| Vercel hosting | All of the above | Free tier, then usage | — |

## Free (FOSS, no account)

Treadmill control and all safety rules; workouts, editor, Browse, community library (browse, save,
rate, share); YouTube scenery playback; heart-rate strap and Hold zone; profiles, themes, history;
manual video search (rate-limited); everything in spec 008 that runs on the tablet.

## Pro (hosted convenience; optional)

- Video → workout analysis on the hosted backend (the LLM cost).
- Higher video-search limit.
- Later, only if it costs money: cloud backup/sync of profiles and history.
Self-hosting: `backend/` + `.env.example` documents the keys; the app has a "Backend URL" setting so a
self-hoster points OpenFit at their own deployment and gets every Pro feature free.

## Mechanics (to decide before building)

- Payment: Google Play Billing (if on Play) or a Stripe checkout giving a Pro key; the backend checks
  the key on `api/analyze`. No accounts or emails needed for the free tier.
- Until Pro exists: the hosted analyze endpoint gets a small free daily allowance per install id.

## GitHub release steps

1. [waived] Key rotation: not needed per the user (no keys are in the repo).
2. [done] Third-party catalogue import and reverse-engineering notes are kept out of the public repo.
3. [you] Sign in: run `gh auth login` in a terminal as baconwrappedbitcoin (OpenFit never types
   passwords or tokens).
4. Prepare: split handoff.md into a public DEVELOPMENT.md and a private, git-ignored handoff; add
   CONTRIBUTING.md; check the .gitignore keeps out third-party app files, `re/`, catalogue data, `.env*`,
   builds; scan for secrets again.
5. [you OK] `git init`, first commit, create `baconwrappedbitcoin/openfit` (public), push.
6. Add a GitHub Release with the debug APK (later a signed release build), and the README's safety note.
