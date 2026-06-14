# Prompt — פייז 3 (Handoff מוכן ליישום)

> הדבק ב-Claude Code ב-session נקי, מתוך תיקיית הפרויקט (`my-health-tracker`).
> זהו handoff אחרי שלב תכנון — ההחלטות העיצוביות כבר סגורות (ראה "החלטות עיצוב סגורות").
> קרא קודם: `CLAUDE.md`, `docs/HLD-health-tracker.md` (סעיף Function Contracts + Contract B),
> ו-`prompts/prompt-phase-3.md`. פייז 1+2 קיימים ועובדים — בנה עליהם, אל תשבור אותם.

---

**המשימה:** ממש את פייז 3 — קריאת AI מאוחדת אחת (`generateInsights`) שמייצרת תובנות ממוקדות
לכל דף, עם פלט מפוצל `today`/`tomorrow`. עבוד ב-TDD (טסט נכשל → קוד → ירוק), פר כללי הפרויקט.

> שים לב: ה-HLD עדיין מתאר את המודל הישן (`generateDailySummary` / `summaries`).
> **מקור האמת הוא `CLAUDE.md` + `prompts/prompt-phase-3.md`** — `generateInsights` / `insights/{date}`
> עם פיצול `today`/`tomorrow`. במקרה סתירה — לך לפי `CLAUDE.md`.

## החלטות עיצוב סגורות (אל תשנה בלי לשאול)

1. **ניתוב מסמכים (today/tomorrow):** ריצת הערב של יום D כותבת
   `today → insights/{D}` (סיכום D) **וגם** `tomorrow → insights/{D+1}` (דגשים ל-D+1).
   כך `insights/{today}.tomorrow` נכתב אמש, וה-trigger של 15:00/ידני נוגע **רק** ב-`today`
   ולעולם לא דורס את `tomorrow`. **הלקוח קורא רק את `insights/{today}`** — שני הבלוקים שם.
2. **בחירת משתמש ב-triggers מתוזמנים:** איטרציה על אוסף `users/` (אין auth context בתזמון).
   כשל למשתמש בודד → log + דילוג, לא פטאלי לשאר.
3. **מיקום תובנות (משפט לכל קטגוריה):**
   Dashboard → `general` · Activity → `activity` + `sleep` · Food → `nutrition`.
   משפט אחד ממוקד ליד קבוצת המדדים הרלוונטית בכל דף. **בלי** להרחיב את הסכמה מעבר ל-4 הקטגוריות.
4. **מנגנון תזמון:** `onSchedule` מ-`firebase-functions/v2/scheduler` (לא Pub/Sub ידני).
   הוא מקצה אוטומטית את ה-Cloud Scheduler + Pub/Sub ב-deploy, והתזמון חי בקוד ובגרסאות.

## צד שרת — מבנה (מראה את תבנית פייז 2)

```
functions/src/insights/
  aggregate.ts        # קריאת profile + healthDaily/{D} + meals(D) + water/{D} → DayData (חלקי/ריק)
  insightsPrompt.ts   # system instruction לפי Contract B + RESPONSE_SCHEMA {today, tomorrow}
  insightsParse.ts    # פרסור פלט מפוצל, ולידציה לכל שדה קטגוריה, חיבור disclaimer קבוע
  writeInsights.ts    # כתיבה idempotent עם merge ברמת שדה (today בלבד מול today+tomorrow)
  core.ts             # runInsightsForUser(uid, date, mode) — תזמור aggregate→Gemini→write
functions/src/generateInsights.ts   # 3 ה-triggers
functions/src/index.ts               # ייצוא 3 הפונקציות
```

**שלושה triggers, אותה לוגיקה ואותו prompt; ה-mode קובע רק מה נכתב:**
- `generateInsightsEvening` — `onSchedule` ערב, mode `evening`: `today→insights/{D}` + `tomorrow→insights/{D+1}`.
- `generateInsightsMidday` — `onSchedule` 15:00, mode `todayOnly`: אם יש נתונים ל-D → `today→insights/{D}`
  ב-merge (משמר את `tomorrow` שנכתב אמש).
- `generateInsightsManual` — `onCall` (Auth + App Check), mode `todayOnly`: כמו midday, על ה-uid המאומת, לפי דרישה.

**כללי ברזל לצד שרת:**
- `writeInsights` משתמש ב-`set(..., {merge:true})` עם מפות ברמת שדה — `todayOnly` כותב **רק** את `today`.
  ערב = שתי כתיבות (today→D, tomorrow→D+1).
- **disclaimer = קבוע בצד שרת** (`DISCLAIMER_HE`), תמיד נכתב, לא נסמך על פלט המודל.
- Resilience בשימוש חוזר ב-`vertexClient` הקיים: `callWithRetry` + `withTimeout` (Vertex 15s, פונקציה 60s,
  retry רק על 429/503/timeout). בכשל Gemini / פרסור לא-תקין → **לא כותבים כלום** (משמרים insights קיים);
  הריצה המתוזמנת הבאה מייצרת מחדש. אין מסמכים חלקיים.
- Gemini via Vertex AI **בצד שרת בלבד**. אין מפתחות בלקוח.

## צד לקוח (Android)

```
data/insights/
  model/DailyInsights.kt          # DailyInsights(date, today: InsightToday?, tomorrow: InsightTomorrow?,
                                  #   disclaimer, trigger, generatedAt)
                                  # InsightToday(general, nutrition, activity, sleep)
                                  # InsightTomorrow(nutrition, activity, sleep)
  InsightsRepository.kt           # interface, StateFlow<DailyInsights?>
  FirestoreInsightsRepository.kt  # snapshot listener מודע-auth על insights/{today} (תבנית meals)
  FunctionsInsightsRefresher.kt   # עטיפת callable ל-generateInsightsManual (תבנית FunctionsMealAnalyzer)
```

- `AppContainer`: הוסף `insightsRepository` + `insightsRefresher`.
- **פונקציית בחירה טהורה** `pickInsight(today, tomorrow, category)` (unit-tested):
  אם שדה ה-`today` של הקטגוריה לא ריק → הצג אותו;
  אחרת אם יש `tomorrow` → הצג עם תווית **"הדגשים שלך להיום"**;
  אחרת → **"התובנות עדיין לא מוכנות"**.
  מבוסס נוכחות (לא שעון קיר) — עמיד וניתן לבדיקה; התנהגות "לפני/אחרי 15:00" נובעת מעצמה
  כי `today` מתמלא רק מ-15:00/רענון.
- **כפתור רענון** בסרגל העליון של כל דף → `viewModel.refresh()` → מצב טעינה → קריאה ל-callable
  (שמעדכן `today`; ה-snapshot listener דוחף את הערך החדש).
- **החלף את ה-advice המזויף**: ב-`FoodViewModel` יש כרגע `aiAdvice`/`refreshAdvice()` מדומים —
  החלף ב-repository + refresher אמיתיים. אותו wiring ל-Dashboard ו-Activity ViewModels.

## Security Rules

הדק את `firestore.rules`: הוסף match מפורש ל-`insights/{date}` שהוא **קריאה-בלבד ללקוח**
(כתיבה דרך Admin SDK ב-Functions עוקפת rules). השאר את כל השאר כפי שהוא.

## טסטים (חובה — פייז לא הושלם עד שהכל ירוק)

**שרת (Jest, `functions/test/`):**
- `aggregate`: יום מלא / חלקי (רק meals בלי health) / ריק → DayData שפוי.
- `insightsParse`: פלט מפוצל תקין מתפרסר; שדה חסר → שגיאה; disclaimer תמיד נוכח.
- `writeInsights`: mode `todayOnly` כותב today בלבד ומשמר `tomorrow` (assertion על merge);
  ערב כותב today→{D} ו-tomorrow→{D+1}.
- כשל/פלט לא-תקין → אין כתיבה (לא דורס insights קיים).
- מיפוי שגיאות.

**לקוח (JUnit):**
- `pickInsight`: today→today · today ריק+tomorrow→tomorrow · שניהם ריקים→"לא מוכן".
- רענון מציב/מנקה מצב טעינה.
- מיפוי תגובת ה-callable ושגיאות.

הרצה: שרת `cd functions && npm test` · לקוח `./gradlew test` · build `./gradlew assembleDebug`.

## אסור

אל תשנה לוגיקת בריאות (פייז 1) או ארוחות (פייז 2) מעבר לקריאה. אל תכניס טון רפואי קובע.
אל תמציא יעדים מספריים. אל תכניס `bodyMeasurements` ל-prompt.

## עצור ובקש ממני (פעולות ידניות שלי — ספק פקודות מדויקות)

לפני: הפעלת Cloud Scheduler API, `firebase deploy --only functions`, רישום App Check,
הפעלת Vertex AI API. אל תריץ אותן בעצמך — עצור, סמן בבירור, ותן לי את הפקודות.

## סיום

הרץ build + כל הטסטים (שרת + לקוח). דווח [✓ הושלם] / [⚠ חסום: סיבה].
אל תסמן הושלם עד שהכל עובר. שאל אם משהו לא ברור לפני שתתחיל.
