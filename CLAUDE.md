# CLAUDE.md — Personal Health Tracker (Android)

> קובץ זיכרון פרויקט עבור Claude Code. נקרא אוטומטית בכל session.
> מקור האמת המלא: `HLD-health-tracker.md` ו-`blueprint-phase-*.md`. קרא אותם לפני עבודה.

## פרטי פרויקט

- **שם:** MyHealthTracker (display name / module)
- **מיקום מקומי:** `D:\AICode\my-health-tracker` (clone קיים של הריפו — git כבר מאותחל ומחובר ל-origin)
- **Version control:** git מקומי, remote `origin` = `https://github.com/dudumrk2/my-health-tracker.git`. ה-push דורש אישור.

## מה אנחנו בונים

אפליקציית אנדרואיד אישית למעקב בריאות:
- קוראת נתוני פעילות ושינה מ-Health Connect (מקומית במכשיר)
- מאפשרת רישום ארוחות בטקסט או בתמונה → ניתוח Gemini → ערכים תזונתיים
- מפיקה סיכום יומי אוטומטי בערב ("מה טוב / מה לשפר")

משתמש יחיד (אישי). אין דרישות scale/concurrency.

## Tech Stack — מחייב

- **Client:** Android Native, Kotlin (latest stable), minSdk 28+
- **UI:** Jetpack Compose, ארכיטקטורת MVVM, Coroutines + Flow
- **Auth:** Firebase Auth — Google Sign-In
- **DB:** Cloud Firestore (KTX SDK), תמיכת offline מובנית
- **Health:** Health Connect SDK (androidx.health.connect:connect-client)
- **Backend logic:** Cloud Functions (2nd gen) — מחזיק את כל קריאות ה-AI
- **AI:** Gemini via Vertex AI — נקרא **רק** מ-Cloud Functions, לעולם לא מהלקוח
- **Scheduling:** Cloud Scheduler + Pub/Sub (לסיכום היומי)
- **Background:** WorkManager (סנכרון Health Connect → Firestore)
- **Abuse protection:** Firebase App Check על קריאות Functions

## מודל נתונים (Firestore) — הכל תחת users/{uid}

```
users/{uid}
├── profile           : { birthYear, gender, weightKg, heightCm, primaryGoal, activityLevel, focusAreas?, goalOverrides?, themePreference, createdAt, updatedAt }
├── healthDaily/{date}: { date, steps, sleepMinutes, sleepSessions[{start,end,stages?}], workouts[{type,durationMin,startTime,source}], syncedAt, source }
├── meals/{mealId}    : { date, loggedAt, inputType, description, items[], totals, aiModel }
├── water/{date}      : { date, amountMl, updatedAt }   (כמות מצטברת במ"ל, idempotent — הגדלה ב-FieldValue.increment)
├── bodyMeasurements/{date} : { date, weightKg?, waistCm?, hipCm?, notes?, loggedAt }  (מעקב עצמי ידני, לא מוזן ל-AI)
└── insights/{date}   : { date, today{general,nutrition,activity,sleep}, tomorrow{nutrition,activity,sleep}, disclaimer, trigger, generatedAt }
```
- `sleepSessions[].stages`: אופציונלי — `[{ stage: "awake"|"light"|"deep"|"rem", start, end }]`, תלוי במקור הסנכרון.
- `workout.source`: `"health_connect"` | `"manual"`. `healthDaily.source`: `"health_connect"` | `"mixed"`.
- `gender`: `"male"` | `"female"` | `"other"` — מועבר ל-Gemini כהקשר בניתוח ארוחות ובתובנות.
- `primaryGoal`: `"lose"|"maintain"|"gain"`; `activityLevel`: `"sedentary"|"light"|"moderate"|"very"|"extra"` (מקדם TDEE). שניהם נבחרים ע"י המשתמש בהרשמה.
- `focusAreas`: מערך הצהרה-עצמית אופציונלי (למשל `"menopause"`) — **לעולם לא מוסק מגיל/מין**, רק קלט ישיר. נכלל ב-prompt רק אם הוצהר.
- `goalOverrides`: דריסה ידנית אופציונלית של יעדים מחושבים `{ caloriesKcal?, steps?, proteinG?, sleepHours?, waterMl? }` — גוברת על החישוב ב-`GoalCalculator`.
- `bodyMeasurements`: מעקב עצמי בלבד. **אינו** נכלל ב-prompt-ים ל-Gemini (Contracts A/B) בשלב זה.
- `insights`: קריאת AI מאוחדת אחת (`generateInsights`), פלט מפוצל. trigger ערב כותב today+tomorrow; 15:00/ידני מעדכנים today בלבד.
- `{date}` בפורמט `yyyy-MM-dd`. מסמכים ממופתחי-תאריך → כתיבה idempotent (עדכון, לא הכפלה).
- הגיל מחושב מ-`birthYear`, לא נשמר ישירות.

## כללי ברזל (חלים על כל הפייזים)

1. **מפתחות AI לעולם לא בצד הלקוח.** כל קריאת Gemini עוברת דרך Cloud Function.
2. **תמונות אוכל לא נשמרות.** נשלחות לניתוח, התוצאה נשמרת, התמונה נזרקת.
3. **Firestore Security Rules:** משתמש ניגש רק ל-`users/{uid}` שלו.
4. **בקש רק הרשאות Health Connect בשימוש בפועל** (עיקרון המינימום).
5. **אין ייעוץ רפואי.** סיכום ומשוב כללי בלבד, עם דיסקליימר קבוע, טון מציע ("כדאי לשקול") ולא קובע.
6. **ללא קריאות חוסמות ב-main thread** — Coroutines בכל I/O.
7. **טסטים חובה בכל פייז.** פייז לא "הושלם" עד שהטסטים עוברים.

## בידוד פייזים

- **פייז 1:** תשתית + בריאות. אסור לגעת ביומן אכילה / Gemini / Functions / סיכום.
- **פייז 2:** יומן אכילה + analyzeMeal. בונה על פייז 1. אסור לגעת בסיכום היומי.
- **פייז 3:** סיכום יומי + Scheduler. צורך את הפלט של 1+2.

## פעולות שהמשתמש מבצע ידנית (לא Claude Code)

- יצירת ריפו מרוחק ב-GitHub וחיבורו (`git remote add` + `push` ראשון)
- הקמת פרויקט Firebase בקונסולה, הפעלת Auth + Firestore + Functions
- הורדת `google-services.json` והנחתו ב-`app/`
- פריסת Cloud Functions (`firebase deploy --only functions`)
- הגדרת Cloud Scheduler job (פייז 3)
- הפעלת Vertex AI API בפרויקט GCP

כשמגיעים לשלב כזה — עצור, סמן בבירור, ובקש מהמשתמש לבצע לפני המשך.

## הרצה ובדיקה

- Build: `./gradlew assembleDebug`
- טסטים: `./gradlew test` (unit) + `./gradlew connectedAndroidTest` (instrumented)
- Firestore/Auth בטסטים: Firebase Local Emulator Suite
