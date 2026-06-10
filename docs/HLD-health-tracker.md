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
2. הגדרת פרופיל אישי ראשוני — שנת לידה, משקל, גובה (ובהמשך מין/יעד אם תרצה). הגיל מחושב משנת הלידה.
3. קריאת נתוני בריאות בסיסיים מ-Health Connect — צעדים, שינה, אימונים.
4. סנכרון נתוני הבריאות ל-Firestore לצורך איגוד וסיכום.
5. רישום ארוחה בטקסט חופשי → ניתוח AI → תוצאה מובנית (מנה, קלוריות, מאקרו).
6. רישום ארוחה בתמונה → Gemini vision → אותה תוצאה מובנית. **התמונה לא נשמרת.**
7. סיכום יומי אוטומטי בערב (Cloud Scheduler) עם משוב "טוב/לשפר" + דיסקליימר רפואי.

**Nice-to-have (מחוץ ל-MVP, לשלבים הבאים):**
- סוגי בריאות נוספים (דופק, קלוריות שריפה, חמצן בדם).
- מגמות שבועיות/חודשיות וגרפים.
- יעדים אישיים והתראות.
- עריכה ידנית של תוצאת ניתוח AI לפני שמירה.

### 3. Non-Functional Requirements

- **Scale:** משתמש יחיד (אתה). אין דרישות concurrency. עיצוב פשוט, לא multi-tenant ברמת תשתית — אבל מודל הנתונים תחת `users/{uid}` כך שניתן להרחיב.
- **Latency:** ניתוח ארוחה < ~5 שניות מקצה לקצה מקובל. סיכום יומי רץ ברקע, לא רגיש ל-latency.
- **Availability:** שירות אישי. Firebase מנוהל; אין SLA פורמלי נדרש.
- **Security & Privacy:**
  - מפתחות Gemini/Vertex לעולם לא בצד הלקוח — רק ב-Cloud Functions.
  - נתוני Health Connect נקראים מקומית; רק נתונים מעובדים נשלחים ל-Firestore.
  - תמונות אוכל לא נשמרות בשום מקום — נשלחות לניתוח, התוצאה נשמרת, התמונה נזרקת.
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
│   ├── weightKg: number
│   ├── heightCm: number
│   ├── createdAt: timestamp
│   └── updatedAt: timestamp
│
├── healthDaily/{yyyy-MM-dd} (document per day)
│   ├── date: string (yyyy-MM-dd)
│   ├── steps: number
│   ├── sleepMinutes: number
│   ├── sleepSessions: [{ start, end, stages? }]
│   ├── workouts: [{ type, durationMin, startTime }]
│   ├── syncedAt: timestamp
│   └── source: "health_connect"
│
├── meals/{mealId} (document per meal)
│   ├── date: string (yyyy-MM-dd)
│   ├── loggedAt: timestamp
│   ├── inputType: "text" | "image"
│   ├── description: string        (קלט המשתמש או תיאור Gemini)
│   ├── items: [{ name, quantity, calories, proteinG, carbsG, fatG }]
│   ├── totals: { calories, proteinG, carbsG, fatG }
│   └── aiModel: string            (לתיעוד גרסת מודל)
│
└── summaries/{yyyy-MM-dd} (document per day)
    ├── date: string
    ├── generatedAt: timestamp
    ├── highlights: [string]       ("מה טוב")
    ├── improvements: [string]     ("מה לשפר")
    ├── narrative: string          (פסקת סיכום קצרה)
    ├── disclaimer: string
    └── inputsSnapshot: { steps, sleepMinutes, mealTotals }
```

הערה: `healthDaily` ו-`summaries` ממופתחים לפי תאריך → idempotent (כתיבה חוזרת מעדכנת ולא מכפילה).

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

#### generateDailySummary
**Trigger:** Pub/Sub message מ-Cloud Scheduler (ערב, שעה קבועה).

**Logic:**
1. לכל משתמש פעיל — לקרוא `profile`, `healthDaily/{today}`, ולאגד את `meals` של היום.
2. לבנות prompt עם הנתונים + הוראת ניסוח (מפורט + דיסקליימר, ללא טון רפואי קובע).
3. לקרוא ל-Gemini, לפרסר את הפלט ל-`highlights` / `improvements` / `narrative`.
4. לכתוב ל-`summaries/{today}` (idempotent).

**אין request/response ללקוח** — הלקוח קורא את `summaries/{today}` מ-Firestore.

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
| **Fallback (summary)** | כשל ל-Gemini → לדלג על היום, לסמן לריטריי בריצה הבאה; לא להשאיר מסמך חלקי. |
| **App Check** | קריאות ללא טוקן App Check תקף נדחות. |

### 6. Observability

- **Logging:** Cloud Functions → Cloud Logging מובנה (JSON). כל קריאה עם requestId, uid, durationMs, מודל. ללא PII רגיש.
- **Metrics:** מוני הצלחה/כשל לכל Function, latency ניתוח, שימוש במכסת Gemini.
- **Alerting (אופציונלי):** התראה אם generateDailySummary נכשל יותר מ-N ימים רצופים.

### 7. Prompt Contracts

מגדיר את ה**מבנה** של כל קריאת AI — תפקיד, קלט, פורמט פלט, אילוצים והתנהגות בכשל. הניסוח המילולי המלא (wording, few-shot, טון) נכתב ב-agent blueprints ומנוהל תחת version control לצד קוד ה-Functions, לא כאן — כדי שלא יתיישן.

#### Contract A — ניתוח ארוחה (analyzeMeal)

| היבט | הגדרה |
|------|-------|
| **Role** | מנתח תזונה שמזהה פריטי מזון ומעריך ערכים תזונתיים |
| **Input** | תיאור טקסט *או* תמונה + פרופיל (משקל/גובה לאומדן מנות) |
| **Output format** | JSON בלבד לפי סכמת `items[]` + `totals`. ללא טקסט עוטף, ללא Markdown |
| **Constraints** | אומדן כשאין ודאות; דגל `lowConfidence: true` כשהזיהוי לא בטוח; יחידות מטריות; אם אין מזון בתמונה — להחזיר `items: []` |
| **Safety** | לא לאבחן, לא להמליץ על דיאטה; רק זיהוי ואומדן |
| **On failure** | פלט לא-תקין/לא-JSON → הפונקציה דוחה ומחזירה שגיאה ידידותית; המשתמש יכול להזין ידנית |

#### Contract B — סיכום יומי (generateDailySummary)

| היבט | הגדרה |
|------|-------|
| **Role** | מאמן בריאות תומך שנותן משוב יומי כללי |
| **Input** | פרופיל + `healthDaily/{today}` (צעדים/שינה/אימונים) + איגוד `meals` של היום |
| **Output format** | JSON: `highlights[]` (מה טוב), `improvements[]` (מה לשפר), `narrative` (פסקה קצרה) |
| **Constraints** | טון תומך ולא שיפוטי; מבוסס רק על נתוני היום; ללא יעדים מספריים קשיחים שלא נמסרו |
| **Safety** | **לא ייעוץ רפואי/תזונתי מותאם**; כולל `disclaimer` קבוע; להימנע מטון קובע ("אתה חייב"), להעדיף הצעות ("כדאי לשקול") |
| **On failure** | כשל/פלט לא-תקין → לא לכתוב מסמך חלקי; לסמן לריטריי בריצה הבאה |

---

## Phase 3 — Phased Execution

| פייז | תוכן | מה רץ בסוף |
|------|------|-----------|
| **פייז 1** (ממוזג 0+1) | פרויקט Kotlin + Compose, Firebase Auth (Google), מסך פרופיל (שנת לידה/משקל/גובה) ל-Firestore, הרשאות Health Connect, קריאת צעדים/שינה/אימונים, סנכרון ל-Firestore (WorkManager), מסך תצוגה | אפליקציה שמתחברת, שומרת פרופיל, ומציגה נתוני בריאות יומיים |
| **פייז 2** | מסך רישום ארוחה (טקסט + מצלמה), פונקציית `analyzeMeal`, אינטגרציית Gemini vision, שמירת תוצאה מובנית ל-`meals`, App Check | אפשר לרשום ארוחה ולראות פירוק תזונתי |
| **פייז 3** | פונקציית `generateDailySummary`, Cloud Scheduler + Pub/Sub, prompt הסיכום, מסך סיכום יומי | סיכום ערב אוטומטי "טוב/לשפר" מופיע בכל בוקר |

**עקרון הפירוק:** כל פייז משאיר אפליקציה שרצה. פייז 1 הוא היסוד הטכני (הרשאות + מכשיר) ולכן ראשון. פייז 3 צורך את הפלט של 1+2 ולכן אחרון.

---

## Phase 4 — Agent Blueprints

ה-blueprint של פייז 1 נמצא במסמך נפרד: `blueprint-phase-1.md`. blueprints לפייזים 2 ו-3 ייכתבו בהמשך.

</div>
