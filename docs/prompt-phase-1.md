# Prompt — פייז 1: תשתית + בריאות

> הדבק את זה ב-Claude Code ב-session נקי, מתוך תיקיית הפרויקט.
> ודא שהקבצים `CLAUDE.md`, `HLD-health-tracker.md`, `blueprint-phase-1.md` נמצאים בתיקייה.

---

קרא קודם את `CLAUDE.md`, `HLD-health-tracker.md`, ו-`blueprint-phase-1.md`. הם מקור האמת — אל תסטה מהסטאק, ממודל הנתונים, או מכללי הברזל.

**המשימה:** ממש את פייז 1 — תשתית + שכבת בריאות — של אפליקציית האנדרואיד.

**הקמת פרויקט (תחילה):**
- עבוד בתוך ה-clone הקיים: `D:\AICode\my-health-tracker`. git כבר מאותחל ומחובר ל-`origin` — אל תריץ `git init` ואל תשנה את ה-remote.
- צור את שלד פרויקט ה-Android בתוך התיקייה הזו. שם האפליקציה (display name): **MyHealthTracker**, applicationId הגיוני (למשל `com.myhealthtracker.app`).
- הוסף `.gitignore` מתאים ל-Android אם עדיין אין, ועשה commits מקומיים הגיוניים תוך כדי העבודה.
- **אל תעשה `git push` בלי אישור ממני** — הצג לי את הפקודה ובקש אישור לפני ה-push הראשון.

**מה לבנות (פרטים מלאים ב-blueprint-phase-1):**
1. פרויקט Android חדש: Kotlin, Jetpack Compose, MVVM, מבנה מודולרי (`data/auth`, `data/profile`, `data/health`, `sync`, `ui/profile`, `ui/dashboard`).
2. Google Sign-In דרך Firebase Auth + שמירת מצב התחברות + logout.
3. מסך פרופיל ראשוני: שנת לידה, משקל (ק"ג), גובה (ס"מ) → `users/{uid}/profile`. ולידציית טווחים. גיל מחושב מ-`birthYear`.
4. `HealthConnectManager`: בדיקת זמינות (מותקן / לא מותקן → הפניה ל-Play Store / לא נתמך), בקשת הרשאות קריאה ל-Steps + SleepSession + ExerciseSession, טיפול בדחייה.
5. קריאת נתוני יום: צעדים, שינה (משך + סשנים), אימונים (סוג/משך/התחלה).
6. מיפוי ל-`healthDaily/{date}` וכתיבה idempotent ל-Firestore.
7. `HealthSyncWorker` (WorkManager): סנכרון תקופתי (~6 שעות) + בפתיחת אפליקציה. עמיד למצב ללא רשת/הרשאות.
8. מסך Dashboard: צעדים/שינה/אימונים של היום + מצב סנכרון אחרון.

**טסטים (חובה — ראה blueprint):** חישוב גיל, ולידציית פרופיל, מיפוי Health Connect, אגרגציית שינה, כתיבה idempotent, התנהגות ללא הרשאות / Health Connect לא זמין. השתמש ב-JUnit + MockK ו-Firebase Local Emulator Suite.

**אסור (constraints):**
- אל תממש יומן אכילה, מצלמה, Gemini, Cloud Functions, או סיכום יומי.
- אל תוסיף סוגי Health Connect מעבר לשלושה שצוינו.
- אל תאחסן תמונות. אל תכתוב נתוני בריאות מחוץ ל-`users/{uid}`.

**עצור ובקש ממני** כשתגיע לשלב שדורש פעולה ידנית שלי: הקמת פרויקט Firebase, `google-services.json`, הפעלת Auth/Firestore. אל תנסה לעקוף אותם.

**סיום:** הרץ `./gradlew assembleDebug` ואת הטסטים. דווח: [✓ הושלם] או [⚠ חסום: סיבה]. אל תסמן את הפייז כהושלם עד שה-build והטסטים עוברים.

אם משהו ב-blueprint לא ברור — שאל אותי לפני שתתחיל.
