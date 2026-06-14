# Prompt — פייז אינטגרציה: חיבור UI ללוגיקה ולשמירת מידע

> הדבק ב-Claude Code ב-session נקי, מתוך `D:\AICode\my-health-tracker`.
> כל הפייזים (1, 1.5, UI, 2, 3) הושלמו בנפרד. מטרת השלב: לחבר את שכבת ה-UI (שרצה על mock) לשכבות האמיתיות הקיימות.
> ודא ש-`CLAUDE.md` ומסמכי `docs/` בתיקייה.

---

קרא קודם את `CLAUDE.md`, `HLD-health-tracker.md`, ו-`screens-spec.md`. אל תסטה ממודל הנתונים ומכללי הברזל.

**המשימה:** לחבר את כל האפליקציה לזרימה עובדת מקצה-לקצה. ה-UI קיים עם mock; הלוגיקה (Auth, Health Connect, Firestore, Cloud Functions `analyzeMeal` ו-`generateInsights`) קיימת. צריך לחווט ביניהם.

## העיקרון המנחה

ה-UI נבנה עם ViewModels ו-state hoisting במכוון. החיבור = **החלפת ה-FakeRepository / mock providers ב-repository אמיתי**, מאחורי אותם interfaces, בלי לשכתב Composables. אם אתה מוצא את עצמך משנה UI מהותית — עצור ובדוק אם יש דרך לשמר את ה-interface.

## מה לחבר (לפי זרימות)

### 1. התחברות (Auth)
- חבר את מסך הכניסה ל-Firebase Auth אמיתי (Google Sign-In). לחיצה על "המשך עם Google" → זרימת OAuth אמיתית.
- ניהול מצב התחברות: משתמש מחובר → ישר לטאבים; לא מחובר → מסך כניסה.
- אחרי כניסה ראשונה (אין profile ב-Firestore) → מסך פרטים אישיים. אחרת → דשבורד.
- Logout מהפרופיל/הגדרות.

### 2. פרופיל
- מסך הפרטים האישיים שומר/קורא מ-`users/{uid}/profile` אמיתי (כולל gender).

### 3. בריאות (Health Connect → Firestore → UI)
- חבר את טאב פעילות ואת כרטיסי הבריאות בדשבורד ל-`healthDaily/{date}` האמיתי.
- אימון ידני נשמר ל-Firestore (source="manual") ומופיע בתצוגה לצד אימוני Health Connect.
- בורר התאריך טוען נתונים אמיתיים לכל יום.

### 4. אכילה + מים (UI ↔ analyzeMeal ↔ Firestore)
- מסך הוספת ארוחה קורא ל-Cloud Function `analyzeMeal` האמיתי (במקום ה-mock עם ההשהיה). כל ה-states נשארים: טעינה, תוצאה הניתנת לעריכה, שגיאה→fallback ידני.
- ארוחה נשמרת ל-`meals` ומופיעה ביומן הארוחות ובסיכום התזונתי של טאב אוכל.
- כפתור הוספת מים מעדכן `water/{date}` אמיתי ומשתקף בתצוגה.

### 5. תובנות AI (generateInsights ↔ Firestore ↔ UI)
- כרטיסי התובנות בכל הדפים נקראים מ-`insights/{today}` האמיתי.
- לוגיקת today/tomorrow: בבוקר מציג tomorrow (אמש); מ-15:00/אחרי רענון מציג today.
- כפתור הרענון בכל דף קורא ל-Callable של `generateInsights` ומרענן את today.

## ניהול מצב ושגיאות (חשוב לחיבור אמיתי)

- כל מסך: מצב טעינה אמיתי (בזמן קריאת Firestore/Function), מצב שגיאה (כשל רשת/Function), ומצב ריק.
- offline: Firestore עם תמיכת offline — ודא שהאפליקציה לא קורסת ללא רשת ומציגה נתונים מ-cache.
- אין קריאות חוסמות ב-main thread — Coroutines לכל I/O.

## טסטים (חובה)

- זרימת התחברות מלאה: כניסה → ניתוב לפי קיום profile → logout.
- זרימת ארוחה מקצה-לקצה: קלט → analyzeMeal → עריכה → שמירה → הופעה ביומן (עם Function mock בטסט).
- חיבור healthDaily אמיתי לתצוגה.
- קריאת insights והצגת today/tomorrow לפי שעה.
- מצבי טעינה/שגיאה/ריק בכל זרימה.

## אסור

- אל תשכתב את ה-Composables של ה-UI — רק החלף את שכבת הנתונים מאחוריהם.
- אל תכניס מפתחות/גישת Gemini ללקוח — קריאות AI רק דרך ה-Functions הקיימים.
- אל תשמור תמונות ארוחה.
- אל תשנה את חוזי ה-Functions (analyzeMeal, generateInsights) — חבר אליהם כמו שהם.

## עצור ובקש ממני (פעולות ידניות)

- כל הגדרה/פריסה ב-Firebase או GCP שעוד לא בוצעה (Functions deploy, App Check, Vertex AI, Cloud Scheduler).
- כל `git push` — הצג פקודות ובקש אישור.

## סיום

- הרץ `./gradlew assembleDebug` + כל הטסטים. דווח [✓ הושלם] / [⚠ חסום: סיבה].
- ספק רשימת בדיקה ידנית קצרה (smoke test) שאני יכול לעבור עליה במכשיר: כניסה, רישום ארוחה, הוספת מים, הוספת אימון, צפייה בתובנות, רענון.

אם משהו לא ברור או אם אתה מגלה שה-mock וה-interface האמיתי לא תואמים — עצור ושאל לפני ששובר UI קיים.
