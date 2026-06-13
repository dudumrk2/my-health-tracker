<div dir="rtl">

# MyHealthTracker — מסמכי תכנון ופרומפטים

ערכת המסמכים לפיתוח אפליקציית MyHealthTracker. כל המסמכים מעודכנים ועקביים נכון לגרסה זו.

## איך לשים בגיט

הנח את התוכן בשורש ה-clone `D:\AICode\my-health-tracker`:
- `CLAUDE.md` → **בשורש הפרויקט** (Claude Code קורא אותו אוטומטית בכל session).
- `docs/` → תיקיית מסמכי הייחוס.
- `prompts/` → הפרומפטים להרצה (לא חובה בגיט, אבל נוח שיהיו שם).

## מבנה

```
CLAUDE.md                      ← זיכרון הפרויקט (סטאק, מודל נתונים, כללי ברזל). בשורש.
docs/
├── HLD-health-tracker.md      ← מסמך התכנון המלא (מקור האמת)
├── screens-spec.md            ← מפרט המסכים (RTL, light/dark, mobile)
└── blueprint-phase-1.md       ← מפרט מימוש מפורט לפייז 1
prompts/
├── prompt-phase-1.5.md        ← patch: gender + אימון ידני (על קוד פייז 1 הקיים)
├── prompt-phase-ui.md         ← שכבת ה-UI המלאה (Compose + mock data)
├── prompt-phase-2.md          ← יומן אכילה + מים + analyzeMeal (Gemini)
└── prompt-phase-3.md          ← תובנות AI מאוחדות (generateInsights)
```

## מצב נוכחי

- **פייז 1 (תשתית + בריאות)** — ✅ כבר מומש (לפני התוספות gender / אימון ידני).

## סדר הרצה מומלץ

לכל פרומפט: פתח **session נקי** ב-Claude Code, מתוך תיקיית הפרויקט, והדבק את תוכן הקובץ. ודא ש-`CLAUDE.md` ומסמכי `docs/` בתיקייה. בסוף כל שלב — ודא שה-build והטסטים עוברים לפני המעבר הבא.

1. **prompt-phase-1.5** — משלים את פייז 1 הקיים: מוסיף gender לפרופיל ואימון ידני.
2. **prompt-phase-ui** — בונה את כל מסכי ה-UI עם mock data (UI-first).
3. **prompt-phase-2** — מחבר את יומן האכילה ל-analyzeMeal האמיתי (Gemini), ומוסיף יומן מים.
4. **prompt-phase-3** — מוסיף את תובנות ה-AI המאוחדות (generateInsights) ומחבר אותן למסכים.

> הערה: ה-UI ב-phase-ui משתמש ב-mock; פייזים 2-3 מחליפים בהדרגה את ה-mock בלוגיקה אמיתית. אם תעדיף גרסה היברידית של phase-ui (שמשתמשת ב-Auth/Health Connect האמיתיים מפייז 1 ו-mock רק לשאר) — בקש לעדכן את הפרומפט.

## פעולות ידניות שלך (לא הסוכן)

- יצירת/חיבור פרויקט Firebase, `google-services.json`, הפעלת Auth/Firestore/Functions
- פריסת Cloud Functions, הפעלת Vertex AI API, App Check
- הגדרת Cloud Scheduler jobs (ערב + 15:00) ל-generateInsights
- כל `git push` — הסוכן יבקש אישור לפני

## עקרונות ליבה (מתוך CLAUDE.md)

- מפתחות AI רק בצד השרת (Cloud Functions), לעולם לא בלקוח.
- תמונות אוכל לא נשמרות — רק התוצאה המנותחת.
- אין ייעוץ רפואי — תובנות כלליות עם דיסקליימר, טון מציע ולא קובע.
- מדדי גוף = מעקב עצמי בלבד, לא מוזנים ל-AI.
- RTL מלא + light/dark בכל המסכים.

</div>
