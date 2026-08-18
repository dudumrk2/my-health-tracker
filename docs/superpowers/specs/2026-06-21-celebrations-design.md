# Design: Celebration Gestures (Celebrations)

**Date:** 2026-06-21
**Status:** Approved (design) — pending implementation plan
**Branch:** `feat/celebrations`

## Goal

Add joyful, GIF-like celebration moments (fireworks, applause, confetti, medal,
trophy) that appear when the user hits meaningful health milestones. Each
celebration is a full-screen animated overlay with a short encouraging message,
a gentle sound, and a light haptic. Each milestone celebrates **once** — it
never replays when the user simply reopens a screen.

This is a personal, single-user app. Celebrations are an ephemeral UI delight,
not a synced health record.

## Triggers (exact thresholds)

| # | Event | Condition | Dedup key |
|---|-------|-----------|-----------|
| 1 | Step goal | `steps >= goal.steps` for the day | `steps-{date}` |
| 2 | 2nd workout this week | weekly workout count crosses 1 → 2 | `workout2-{weekId}` |
| 3 | 4th workout this week | weekly workout count crosses 3 → 4 | `workout4-{weekId}` |
| 4a | Great meal | `quality.processedScore == 1` | `meal-great-{mealId}` |
| 4b | Good meal | `quality.processedScore <= 2` **and** `quality.insulinImpact == "low"` | `meal-good-{mealId}` |
| 5 | Calorie goal yesterday | yesterday had `>= 3` meals **and** total calories within **±10%** of `goal.caloriesKcal` | `calorie-{yesterday}` |

**Approved decisions:**
- **Trigger 5 ("calorie goal met"):** yesterday's total calories must fall within
  a symmetric **±10%** band around `goal.caloriesKcal` (eating far under the goal
  is not "meeting the goal"). Only evaluated when at least 3 meals were logged
  that day.
- **Triggers 2 & 3 ("same week"):** the week is a **calendar week starting
  Sunday** (Israel locale). `weekId` is derived from the date (e.g.
  `yyyy-Www` anchored to the Sunday of that week). The workout count includes
  **both** Health Connect–synced workouts and manually added workouts.
- **Trigger 4a vs 4b precedence:** if a meal qualifies as "great" (4a) it fires the
  great-meal celebration only; 4b is the fallback for `processedScore <= 2 &&
  insulinImpact == "low"` that is not already "great". A single meal never fires both.

## Architecture

A single celebration layer, decoupled from the screens that detect the
conditions.

```
CelebrationEvent (sealed)   Describes a celebration: type, candidate animation
                            assets, message text, sound flag.

CelebrationRules            Pure functions that evaluate raw state → optional
                            CelebrationEvent (no Android deps; fully unit-tested).

CelebrationStore            Local DataStore (Preferences). Tracks "already
                            celebrated" dedup keys. Single source of truth for
                            once-only behavior.

CelebrationController       Singleton in AppContainer. `tryCelebrate(event)`:
                            checks CelebrationStore; if the key is new, marks it
                            and emits the event on a SharedFlow. Exposes
                            `events: SharedFlow<CelebrationEvent>`.

CelebrationOverlay          Composable hosted in MainScreen. Collects
                            controller.events, renders the Lottie animation +
                            message, plays sound/haptic, auto-dismisses.
```

### Detection placement

The existing ViewModels already hold the relevant state, so they own detection
(delegating the actual decision to the pure `CelebrationRules`):

- `ActivityViewModel` — trigger 1 (steps), triggers 2 & 3 (weekly workout count).
- `AddMealViewModel` — triggers 4a / 4b, evaluated when an analyzed meal is saved.
- `FoodViewModel` (or `DashboardViewModel`) — trigger 5 (yesterday's calorie goal),
  evaluated from the loaded meals + goals.

Each ViewModel calls `CelebrationController.tryCelebrate(event)` when its pure
rule returns a non-null event. The controller handles dedup, so ViewModels stay
free of persistence concerns.

### Why DataStore (local), not Firestore

The "already celebrated" flag is a per-device display concern, not health data.
It does not need cross-device sync and must not pollute the `users/{uid}`
Firestore model. DataStore Preferences is the right fit.

## Assets (Lottie)

Animations are bundled locally in `app/src/main/res/raw/` — no runtime download,
works offline. Format: plain **Lottie JSON** (`.json`), loaded via
`LottieCompositionSpec.RawRes`. The user downloads these from LottieFiles.

| File | Content |
|------|---------|
| `celeb_confetti.json` | Colorful confetti burst |
| `celeb_fireworks.json` | Exploding fireworks |
| `celeb_clap.json` | Clapping hands / applause |
| `celeb_muscle.json` | Flexed arm / strength |
| `celeb_medal.json` | Medal / award badge |
| `celeb_trophy.json` | Winner trophy cup |
| `celeb_chime.mp3` | Short, gentle success chime (~1s) |

### Animation mapping (variety via randomness)

Each celebration type maps to a **list** of candidate animations; one is chosen at
random per firing so the same milestone does not always look identical.

| Trigger | Candidate animations | Tone |
|---------|---------------------|------|
| Step goal (1) | `confetti`, `fireworks` | strong |
| Workout 2nd/4th (2,3) | `clap`, `muscle` | medium |
| Great meal (4a) | `fireworks`, `medal` | strong |
| Good meal (4b) | `clap`, `confetti` | gentle |
| Calorie goal (5) | `confetti`, `trophy` | strong |

The code degrades gracefully when an asset is missing (placeholder / skip), so
work can proceed before all six files are added.

## Sound & haptics

- Short chime via `SoundPool`, played **only when** `AudioManager.ringerMode ==
  RINGER_MODE_NORMAL` (respects silent/vibrate).
- Light haptic via `VibratorManager` (or `Vibrator` on older APIs) on appearance.

## Overlay UX

- Semi-transparent full-screen overlay above all content (hosted in `MainScreen`'s
  root `Box`, above the floating nav bar).
- Centered Lottie animation + short Hebrew encouragement
  (e.g. "כל הכבוד! עמדת ביעד הצעדים 🎉").
- Auto-dismiss when the animation completes (~2.5s) or on tap.
- Non-blocking once dismissed; does not intercept touches after it fades.
- Respects `themePreference` (text colors from the theme).

### Message copy (Hebrew, suggestive tone — no medical claims)

| Trigger | Message |
|---------|---------|
| Step goal | כל הכבוד! עמדת ביעד הצעדים היום 🎉 |
| 2nd workout | אימון שני השבוע — ממשיכים בכיף! 💪 |
| 4th workout | ארבעה אימונים השבוע, אלוף! 🔥 |
| Great meal | ארוחה מצוינת! בחירה נקייה ובריאה 🥬 |
| Good meal | ארוחה טובה, כל הכבוד! 👏 |
| Calorie goal | עמדת ביעד הקלורי אתמול — מעולה! 🏆 |

## Testing

- **`CelebrationRules` (pure):** unit tests per threshold, including edge cases —
  workout count 1→2 and 3→4, calorie ±10% band boundaries, exactly 3 meals,
  `processedScore` boundaries, 4a-vs-4b precedence.
- **`CelebrationStore` dedup:** asserts a key fires once and is suppressed
  thereafter.
- No tests for Lottie rendering or sound (UI / system-context concerns).

## Dependencies

- New: `com.airbnb.android:lottie-compose` (Lottie rendering for Compose).
- New: AndroidX DataStore Preferences (if not already present) for
  `CelebrationStore`.

## Out of scope

- Cross-device sync of celebration state.
- User-configurable celebration toggles / settings (could be a later addition).
- Server/Cloud Function involvement — this is entirely client-side.
</content>
</invoke>
