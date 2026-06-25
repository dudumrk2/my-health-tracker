<div dir="rtl">

# High-Level Design — אפליקציית מעקב בריאות אישי

> מסמך תכנון (HLD). אין כאן קוד מימוש — ארכיטקטורה, חוזים ופירוק לפייזים בלבד.

---

## Phase 1 — Requirement Analysis & Scope

### 1. System Goal (משפט אחד)

אפליקציית אנדרואיד אישית שאוספת נתוני פעילות ושינה מ-Health Connect, מאפשרת רישום ארוחות בטקסט או בתמונה עם ניתוח AI, ומפיקה סיכום יומי אוטומטי של "מה טוב ומה כדאי לשפר".

### 2. Functional Requirements

**MVP (נכלל):**
1. הרשמה והתחברות עם Google (Firebase Auth).
2. הגדרת פרופיל אישי ראשוני — שנת לידה, מין, משקל, גובה. הגיל מחושב משנת הלידה. מין ומשקל/גובה משמשים כהקשר לניתוח AI.
2a. בחירת **מטרת שימוש** (ירידה / שמירה / עלייה במשקל) ושדה **הצהרה-עצמית** אופציונלי (תחומים שהמשתמש מסמן בעצמו). היעדים מחושבים מהפרופיל וניתנים לדריסה ידנית. **האפליקציה לעולם לא מסיקה מצב רפואי מגיל/מין — רק מהצהרת המשתמש.**
3. קריאת נתוני בריאות בסיסיים מ-Health Connect — צעדים, שינה (כולל שלבי שינה אופציונליים: עמוקה/קלה/REM אם המקור מסנכרן), אימונים.
4. הוספת אימון ידנית (סוג, משך, תאריך/שעה) — בנוסף לקריאה אוטומטית מ-Health Connect.
5. סנכרון נתוני הבריאות ל-Firestore לצורך איגוד וסיכום.
6. רישום ארוחה בטקסט חופשי → ניתוח AI → תוצאה מובנית (מנה, קלוריות, מאקרו).
7. רישום ארוחה בתמונה → Gemini vision → אותה תוצאה מובנית. **התמונה לא נשמרת.**
8. סיכום יומי אוטומטי בערב (Cloud Scheduler) עם משוב "טוב/לשפר" + דיסקליימר רפואי.
9. מעקב מדדי גוף — רישום ידני תקופתי (משקל / היקף מותן / היקף ירכיים), עם תצוגת מגמה לאורך זמן. **מעקב עצמי בלבד — אינו מוזן ל-AI ואינו חלק מהסיכום היומי.**

**Nice-to-have (מחוץ ל-MVP, לשלבים הבאים):**
- סוגי בריאות נוספים (דופק, קלוריות שריפה, חמצן בדם).
- גרפי מגמה למדדי גוף (מעבר לתצוגה בסיסית).
- יעדים אישיים והתראות.
- עריכה ידנית של תוצאת ניתוח AI לפני שמירה.
- שילוב מגמת מדדי גוף בסיכום היומי (הרחבה עתידית — דורשת החלטה מודעת מראש).

### 3. Non-Functional Requirements

- **Scale:** משתמש יחיד (אתה). אין דרישות concurrency. עיצוב פשוט, לא multi-tenant ברמת תשתית — אבל מודל הנתונים תחת `users/{uid}` כך שניתן להרחיב.
- **Latency:** ניתוח ארוחה < ~5 שניות מקצה לקצה מקובל. סיכום יומי רץ ברקע, לא רגיש ל-latency.
- **Availability:** שירות אישי. Firebase מנוהל; אין SLA פורמלי נדרש.
- **Security & Privacy:**
  - מפתחות Gemini/Vertex לעולם לא בצד הלקוח — רק ב-Cloud Functions.
  - נתוני Health Connect נקראים מקומית; רק נתונים מעובדים נשלחים ל-Firestore.
  - תמונות אוכל לעולם לא עולות/נשמרות בענן. ניתן לשמור אותן מקומית במכשיר בלבד. תהליך הניתוח מתבצע ברקע: ארוחה נוצרת בסטטוס analyzing (create-pending), נשלחת לניתוח דרך WorkManager (הכולל מנגנון ניסיונות חוזרים), ומתעדכנת ל-complete או failed. בסיום התהליך, אם המשתמש טרם צפה בתוצאה (unseen-result interception), מוקפצת התראת צפייה בכניסה הבאה לאפליקציה.
  - Firestore Security Rules: משתמש ניגש רק ל-`users/{uid}` שלו.
  - App Check על קריאות ל-Cloud Functions למניעת שימוש לרעה.

### 4. Tech Stack & Tooling

| Component | Technology | Details |
|-----------|-----------|---------|
| **Client** | Android Native — Kotlin | Jetpack Compose ל-UI, MVVM, Coroutines/Flow |
| **Health Data** | Health Connect SDK | קריאה מקומית: Steps, SleepSession, ExerciseSession |
| **Auth** | Firebase Auth | Google Sign-In |
| **Database** | Cloud Firestore | מסמכים תחת `users/{uid}`, תמיכת offline מובנית |
| **Backend Logic** | Cloud Functions (2nd gen) | TypeScript/Node או Python; מחזיק את כל קריאות ה-AI |
| **AI** | Gemini via Vertex AI | vision לניתוח תמונה, טקסט לסיכום; נקרא רק מ-Functions |
| **Scheduling** | Cloud Scheduler + Pub/Sub | טריגר ערב לסיכום היומי |
| **Background Sync** | WorkManager (Android) | סנכרון Health Connect → Firestore תקופתי |
| **Abuse Protection** | Firebase App Check | מאמת שקריאות ל-Functions מגיעות מהאפליקציה |

### 5. Out of Scope

- iOS / HealthKit — אנדרואיד בלבד בשלב זה.
- חיבור API ישיר ל-Garmin/Zepp בענן — מסתמכים על סנכרון היצרן ל-Health Connect.
- אחסון תמונות אוכל.
- ייעוץ רפואי/תזונתי מותאם אישית — המשוב כללי ועם דיסקליימר.
- שיתוף נתונים בין משתמשים / רשת חברתית.

### 6. Success Criteria

- הרשאות Health Connect ניתנות ונתוני צעדים/שינה/אימון נקראים ומוצגים.
- רישום ארוחה (טקסט ותמונה) מחזיר תוצאה מובנית תוך ~5 שניות ונשמר ב-Firestore.
- סיכום יומי נוצר אוטומטית בערב וזמין כשפותחים את האפליקציה.
- מפתח AI לא מופיע באף מקום בצד הלקוח (נבדק בבנייה).
- כל קריאת Firestore מוגבלת למשתמש המחובר (נבדק מול Security Rules).

---

## Phase 2 — Architecture & Data Flow

### 1. System Components

| Component | Responsibility | Technology | Dependencies |
|-----------|----------------|-----------|--------------|
| **Android App** | UI, פרופיל, קריאת Health Connect, קלט ארוחה, תצוגת סיכום | Kotlin + Compose | Firebase SDK, Health Connect |
| **Health Sync Worker** | סנכרון תקופתי של נתוני בריאות ל-Firestore | WorkManager | Health Connect, Firestore |
| **analyzeMeal Function** | מקבל טקסט/תמונה → Gemini → JSON מובנה → Firestore | Cloud Function | Vertex AI, Firestore |
| **generateDailySummary Function** | מאוגד מופעל ערב → איגוד נתוני יום → Gemini → סיכום | Cloud Function | Cloud Scheduler, Firestore, Vertex AI |
| **Firestore** | אחסון פרופיל, בריאות, ארוחות, סיכומים | Cloud Firestore | — |
| **Firebase Auth** | זהות משתמש | Firebase Auth | — |

### 2. Data Model (Firestore)

מודל מסמכים, הכל תחת המשתמש:

```
users/{uid}
├── profile (document)
│   ├── birthYear: number
│   ├── gender: string              ("male" | "female")  2014 05e905d305d4 05d705d505d105d4
│   ├── weightKg: number
│   ├── heightCm: number
│   ├── primaryGoal: string         ("lose" | "maintain" | "gain") — מטרת שימוש, נבחרת ע"י המשתמש
│   ├── activityLevel: string       ("sedentary"|"light"|"moderate"|"very"|"extra") — נבחר ע"י המשתמש; מקדם TDEE
│   ├── focusAreas: [string]?       — שדה הצהרה-עצמית אופציונלי שהמשתמש מסמן בעצמו
│   │                                 (למשל "menopause", "muscle_gain"). לעולם לא מוסק אוטומטית.
│   ├── goalOverrides: {            — דריסה ידנית של יעדים מחושבים (כל שדה אופציונלי)
│   │       caloriesKcal?, steps?, proteinG?, sleepHours?, waterMl? }
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
│
├── healthDaily/{yyyy-MM-dd} (document per day)
│   ├── date: string (yyyy-MM-dd)
│   ├── steps: number
│   ├── sleepMinutes: number
│   ├── sleepSessions: [{ start, end, stages?: [{ stage: "awake"|"light"|"deep"|"rem", start, end }] }]
│   ├── workouts: [{ type, durationMin, startTime, source }]  ("health_connect" | "manual")
│   ├── syncedAt: timestamp
│   └── source: "health_connect" | "mixed"                   ("mixed" אם יש גם ידני)
│
├── meals/{mealId} (document per meal)
│   ├── date: string (yyyy-MM-dd)
│   ├── loggedAt: timestamp
│   ├── inputType: "text" | "image"
│   ├── description: string        (קלט המשתמש או תיאור Gemini)
│   ├── items: [{ name, quantity, calories, proteinG, carbsG, fatG }]
│   ├── totals: { calories, proteinG, carbsG, fatG }
│   ├── aiModel: string            (לתיעוד גרסת מודל)
│   ├── status: "analyzing" | "complete" | "failed"
│   ├── seen: boolean
│   ├── localImagePath: string?    (מקומית בלבד)
│   ├── note: string?
│   └── failureReason: string?
│
├── bodyMeasurements/{yyyy-MM-dd} (document per entry — מעקב עצמי, ידני)
│   ├── date: string (yyyy-MM-dd)
│   ├── weightKg: number?
│   ├── waistCm: number?
│   ├── hipCm: number?
│   ├── notes: string?
│   └── loggedAt: timestamp
│
├── water/{yyyy-MM-dd} (document per day)
│   ├── date: string (yyyy-MM-dd)
│   ├── cups: number               (מונה כוסות; כל "+ מים" מגדיל ב-1)
│   └── updatedAt: timestamp
│
└── insights/{yyyy-MM-dd} (document per day — תובנות AI מאוחדות)
    ├── date: string
    ├── today: { general, nutrition, activity, sleep }    (משפט אחד לכל שדה)
    ├── tomorrow: { nutrition, activity, sleep }           (דגשים, נוצרים בערב)
    ├── disclaimer: string
    ├── trigger: "evening" | "afternoon" | "manual"
    └── generatedAt: timestamp
```

הערה: `healthDaily`, `water` ו-`insights` ממופתחים לפי תאריך → idempotent (כתיבה חוזרת מעדכנת ולא מכפילה). ב-`insights`: trigger ערב כותב today+tomorrow; 15:00/ידני מעדכנים today בלבד.

### 3. Function Contracts

#### analyzeMeal
**Trigger:** HTTPS Callable (נקרא מהאפליקציה, מאומת ב-Auth + App Check)

**Request:**
```json
{
  "inputType": "text | image",
  "text": "string (אם inputType=text)",
  "imageBase64": "string (אם inputType=image, לא נשמר)",
  "date": "yyyy-MM-dd"
}
```

**Response (200):**
```json
{
  "mealId": "string",
  "description": "string",
  "items": [
    { "name": "string", "quantity": "string",
      "calories": 0, "proteinG": 0, "carbsG": 0, "fatG": 0 }
  ],
  "totals": { "calories": 0, "proteinG": 0, "carbsG": 0, "fatG": 0 }
}
```

**Errors:** `INVALID_ARGUMENT` (קלט חסר), `UNAUTHENTICATED`, `RESOURCE_EXHAUSTED` (מכסת Gemini), `INTERNAL` (כשל ניתוח). הפונקציה לעולם לא מחזירה את הודעת השגיאה הגולמית של Gemini ללקוח.

**הנחיה ל-Gemini:** להחזיר JSON בלבד לפי הסכמה, ללא טקסט עוטף. אם זיהוי לא ודאי — להחזיר אומדן עם דגל `lowConfidence`.

#### generateInsights (קריאת AI מאוחדת)
**Triggers (שלושה, אותה לוגיקה ואותו prompt בכולם):**
1. **ערב** (Cloud Scheduler) — מייצר תובנות סיום-יום + דגשים למחר. הדגשים מוצגים בבוקר.
2. **15:00** (Cloud Scheduler) — אם הוכנסו נתונים באותו יום, מרענן את תובנות ה-`today`.
3. **רענון ידני** (HTTPS Callable) — כפתור בכל דף; מפעיל את אותה לוגיקה on-demand.

**Logic:**
1. לקרוא `profile` (כולל מין/משקל/גובה), `healthDaily/{today}`, לאגד `meals` ו-`water` של היום.
2. לבנות prompt אחיד עם *כל* הנתונים שנאספו עד כה, ולבקש פלט מפוצל (today + tomorrow).
3. לקרוא ל-Gemini, לפרסר, ולכתוב ל-`insights/{today}` (idempotent).

**פלט מפוצל (נשמר ב-`insights/{date}`):**
```json
{
  "today": {
    "general":   "string",
    "nutrition": "string",
    "activity":  "string",
    "sleep":     "string"
  },
  "tomorrow": {
    "nutrition": "string",
    "activity":  "string",
    "sleep":     "string"
  },
  "disclaimer": "string",
  "generatedAt": "timestamp",
  "trigger": "evening | afternoon | manual"
}
```

**עדכון חלקי — חשוב:** trigger של ערב כותב את *שני* הבלוקים (today + tomorrow). triggers של 15:00 ורענון ידני מעדכנים **רק את `today`** ומשאירים את `tomorrow` שנוצר בערב הקודם ללא שינוי — כדי שהדגשים-להיום שמוצגים בבוקר לא יידרסו.

**תצוגה בלקוח:** בבוקר הדפים מציגים את `tomorrow` (שנוצר אמש = "הדגשים שלך להיום"). מ-15:00 ואילך / אחרי רענון, מוצג גם `today` המעודכן ליד כל מדד. הלקוח קורא `insights/{today}` מ-Firestore.

**Callable (רענון ידני) — Request:** `{ "date": "yyyy-MM-dd" }`. **Response:** אובייקט ה-insights המעודכן (בלוק today).

### 4. Error Handling

מעטפת שגיאה אחידה שכל Function מחזירה ללקוח (callable):
```json
{ "error": { "code": "string", "message": "string (בטוח להצגה)", "requestId": "string" } }
```
- שגיאות קלט (4xx-equivalent) → log ברמת WARN עם requestId ו-uid.
- כשלי Gemini/פנימי → log ERROR עם trace מלא; ללקוח מוחזרת הודעה גנרית.
- לעולם לא ללוגג: imageBase64, תוכן תמונה, מפתחות.

### 5. Resilience (קריאות ל-Gemini)

| היבט | מדיניות |
|------|---------|
| **Timeout** | קריאת Vertex AI: 15s. Function כולה: 60s. |
| **Retry** | Exponential backoff, max 2 ניסיונות, רק על 429/503/timeout. לא על שגיאות קלט. |
| **Fallback (analyzeMeal)** | כשל ניתוח → להחזיר שגיאה ידידותית; לאפשר למשתמש להזין ערכים ידנית. |
| **Fallback (insights)** | כשל ל-Gemini → לא לדרוס insights קיים; לסמן לריטריי בריצה הבאה. |
| **App Check** | קריאות ללא טוקן App Check תקף נדחות. |

### 6. Observability

- **Logging:** Cloud Functions → Cloud Logging מובנה (JSON). כל קריאה עם requestId, uid, durationMs, מודל. ללא PII רגיש.
- **Metrics:** מוני הצלחה/כשל לכל Function, latency ניתוח, שימוש במכסת Gemini.
- **Alerting (אופציונלי):** התראה אם generateInsights נכשל יותר מ-N ימים רצופים.

### 7. Prompt Contracts

מגדיר את ה**מבנה** של כל קריאת AI — תפקיד, קלט, פורמט פלט, אילוצים והתנהגות בכשל. הניסוח המילולי המלא (wording, few-shot, טון) נכתב ב-agent blueprints ומנוהל תחת version control לצד קוד ה-Functions, לא כאן — כדי שלא יתיישן.

#### Contract A — ניתוח ארוחה (analyzeMeal)

| היבט | הגדרה |
|------|-------|
| **Role** | מנתח תזונה שמזהה פריטי מזון ומעריך ערכים תזונתיים |
| **Input** | תיאור טקסט *או* תמונה + פרופיל (מין/משקל/גובה לאומדן מנות) |
| **Output format** | JSON בלבד לפי סכמת `items[]` + `totals`. ללא טקסט עוטף, ללא Markdown |
| **Constraints** | אומדן כשאין ודאות; דגל `lowConfidence: true` כשהזיהוי לא בטוח; יחידות מטריות; אם אין מזון בתמונה — להחזיר `items: []` |
| **Safety** | לא לאבחן, לא להמליץ על דיאטה; רק זיהוי ואומדן |
| **On failure** | פלט לא-תקין/לא-JSON → הפונקציה דוחה ומחזירה שגיאה ידידותית; המשתמש יכול להזין ידנית |

#### Contract B — תובנות מאוחדות (generateInsights)

| היבט | הגדרה |
|------|-------|
| **Role** | מאמן בריאות תומך שנותן משפטי תובנה ממוקדים, אחד לכל תחום |
| **Input** | פרופיל (מין/גיל/משקל/גובה) + `healthDaily/{today}` (צעדים/שינה/אימונים כולל ידניים) + איגוד `meals` + `water` של היום. **כל הנתונים שנאספו עד כה.** |
| **Output format** | JSON מפוצל: `today` { general, nutrition, activity, sleep } + `tomorrow` { nutrition, activity, sleep } + `disclaimer`. כל שדה = משפט אחד ממוקד. ללא טקסט עוטף |
| **Constraints** | טון תומך ולא שיפוטי; משפט אחד קצר לכל שדה; מבוסס על הנתונים שנמסרו בלבד; ללא יעדים מספריים קשיחים שלא נמסרו |
| **Safety** | **לא ייעוץ רפואי/תזונתי מותאם**; `disclaimer` קבוע; טון מציע ("כדאי לשקול") ולא קובע; ללא קידום "ספוט רדקשן" |
| **On failure** | כשל/פלט לא-תקין → לא לדרוס insights קיים; לסמן לריטריי בריצה הבאה |
| **עדכון בלוקים** | trigger ערב כותב today+tomorrow; triggers 15:00/ידני מעדכנים today בלבד |

---

## Phase 3 — Phased Execution

| פייז | תוכן | מה רץ בסוף |
|------|------|-----------|
| **פייז 1** (ממוזג 0+1) | פרויקט Kotlin + Compose, Firebase Auth (Google), מסך פרופיל (שנת לידה/מין/משקל/גובה) ל-Firestore, הרשאות Health Connect, קריאת צעדים/שינה/אימונים, הוספת אימון ידנית, סנכרון ל-Firestore (WorkManager), מסך תצוגה | אפליקציה שמתחברת, שומרת פרופיל, ומציגה נתוני בריאות יומיים כולל אימון ידני |
| **פייז 2** | מסך רישום ארוחה (טקסט + מצלמה), פונקציית `analyzeMeal`, אינטגרציית Gemini vision, שמירת תוצאה מובנית ל-`meals`, App Check | אפשר לרשום ארוחה ולראות פירוק תזונתי |
| **פייז 3** | פונקציית `generateInsights` (קריאה מאוחדת, פלט מפוצל today/tomorrow), Cloud Scheduler (ערב + 15:00) + רענון ידני (callable + כפתור), prompt התובנות, הצגת משפט ממוקד ליד כל מדד בדפים | תובנות AI ממוקדות בכל דף, דגשים בבוקר, רענון אוטומטי ב-15:00 וידני |

**עקרון הפירוק:** כל פייז משאיר אפליקציה שרצה. פייז 1 הוא היסוד הטכני (הרשאות + מכשיר) ולכן ראשון. פייז 3 צורך את הפלט של 1+2 ולכן אחרון.

---

## Phase 4 — Agent Blueprints

ה-blueprint של פייז 1 נמצא במסמך נפרד: `blueprint-phase-1.md`. blueprints לפייזים 2 ו-3 ייכתבו בהמשך.

</div>
