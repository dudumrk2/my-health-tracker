# Prompt — פייז 3: תובנות AI מאוחדות

> הדבק ב-Claude Code ב-session נקי, מתוך תיקיית הפרויקט, **אחרי שפייז 1 ו-2 הושלמו ועובדים**.
> ודא ש-`CLAUDE.md` ו-`HLD-health-tracker.md` בתיקייה.

---

קרא קודם את `CLAUDE.md` ו-`HLD-health-tracker.md` (במיוחד `generateInsights` בסעיף Function Contracts, ו-Contract B בסעיף Prompt Contracts). פייז 1+2 קיימים — בנה עליהם.

**המשימה:** ממש את פייז 3 — קריאת AI מאוחדת אחת (`generateInsights`) שמייצרת תובנות ממוקדות לכל דף, עם פלט מפוצל today/tomorrow.

**צד השרת (Cloud Function `generateInsights`):**
1. **שלושה triggers, אותה לוגיקה ואותו prompt בכולם:**
   - **ערב** (Cloud Scheduler) — כותב את שני הבלוקים: `today` + `tomorrow` (דגשים למחר).
   - **15:00** (Cloud Scheduler) — אם הוכנסו נתונים באותו יום, מעדכן **רק את `today`**.
   - **רענון ידני** (HTTPS Callable, מאומת Auth + App Check) — מעדכן **רק את `today`**.
2. לוגיקה: קרא `profile` (מין/משקל/גובה), `healthDaily/{today}`, אגד `meals` + `water` של היום — **כל הנתונים שנאספו עד כה**.
3. בנה prompt אחיד לפי Contract B. בקש פלט JSON מפוצל: `today` { general, nutrition, activity, sleep } + `tomorrow` { nutrition, activity, sleep } + `disclaimer`. כל שדה = משפט אחד ממוקד.
4. קרא ל-Gemini via Vertex AI (צד שרת בלבד). פרסר.
5. כתוב ל-`users/{uid}/insights/{today}` — idempotent. **קריטי:** triggers של 15:00/ידני אסור שידרסו את `tomorrow` שנוצר בערב — עדכן רק את שדה `today`.
6. בכשל / פלט לא-תקין → **אל תדרוס insights קיים**; סמן לריטריי. Resilience כמו פייז 2 (timeout 15s, retry מוגבל).

**צד הלקוח (Android):**
1. הצגת משפט תובנה ממוקד **ליד כל מדד** בשלושת הדפים (דשבורד/פעילות/אוכל), נקרא מ-`insights/{today}`.
2. **לוגיקת today/tomorrow:** בבוקר (לפני 15:00 / לפני שיש today) הצג את דגשי `tomorrow` שנוצרו אמש ("הדגשים שלך להיום"). מ-15:00 ואילך / אחרי רענון — הצג את `today` המעודכן.
3. **כפתור רענון** בסרגל העליון של כל דף → קורא ל-Callable, מציג מצב טעינה, מרענן את `today`.
4. מצב "התובנות עדיין לא מוכנות".

**טסטים (חובה):**
- שרת: איגוד נתוני יום (כולל חלקי/ריק), פרסור פלט מפוצל, **אי-דריסת tomorrow ב-trigger של 15:00/ידני**, נוכחות disclaimer, אי-דריסה בכשל.
- לקוח: בחירת today מול tomorrow לפי שעה/זמינות, מצב טעינה ברענון, מצב "לא מוכן".

**אסור:** אל תשנה לוגיקת בריאות (פייז 1) או ארוחות (פייז 2) מעבר לקריאה. אל תכניס טון רפואי קובע. אל תמציא יעדים מספריים. אל תכניס מדדי גוף (bodyMeasurements) ל-prompt.

**עצור ובקש ממני** לפני: פריסת ה-Function, הגדרת שני Cloud Scheduler jobs (ערב + 15:00) + Pub/Sub topics, App Check. אלה פעולות ידניות שלי — ספק לי את הפקודות המדויקות.

**סיום:** הרץ build + טסטים. דווח [✓ הושלם] / [⚠ חסום: סיבה]. אל תסמן הושלם עד שהכל עובר.

שאל אותי אם משהו לא ברור לפני שתתחיל.
