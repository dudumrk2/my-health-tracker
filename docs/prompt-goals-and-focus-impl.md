<div dir="rtl">

# Prompt — יעדים אישיים, חישוב יעדים, והצהרה-עצמית

> הדבק ב-Claude Code ב-session נקי, מתוך `D:\AICode\my-health-tracker`.
> האפליקציה כבר עובדת ומלאה (כל הפייזים + אינטגרציה הושלמו).
> זה שילוב של **אימות** (חלקים שכבר בוצעו) ו**בנייה** (חלקים חדשים). קרא את הקבצים תחילה.

</div>

קרא קודם את הקבצים האלה — הם מקור האמת ומשקפים את כל ההחלטות:
- `CLAUDE.md` — כללי הברזל (במיוחד 5, 5a, 5b: אין ייעוץ רפואי; אסור להסיק מצב רפואי מגיל/מין; יעדים מחושבים + דריסה ידנית).
- `docs/HLD-health-tracker.md` — מודל `profile` המעודכן (primaryGoal, activityLevel, focusAreas?, goalOverrides?).
- `docs/goal-setting-research.md` — הנוסחאות וערכי ברירת המחדל לכל יעד.
- `docs/plan-revision-goals-and-focus.md` — המפרט המלא של חלק זה (מטרות + הצהרה-עצמית + תוכן רגיש).

---

## חלק א — אימות (כבר בוצע; ודא שקיים ותקין כפי שהוגדר)

הנושאים הבאים כבר מומשו במשימה קודמת. **אל תבנה מחדש** — רק ודא שהם קיימים, תקינים, ותואמים להגדרות. אם משהו חסר או סוטה — דווח לי לפני תיקון.

1. **תגיות איכות מזון** (Contract A): שדה `quality` בתוצאת analyzeMeal — processedScore (1-5), hasComplexCarbs, hasSimpleCarbs, hasHealthyFats, insulinImpact — ושדה `recommendation` (המלצת שדרוג בעברית, משפט אחד). מוצגים ב-AddMealScreen וב-MealDetailSheet, נשמרים ב-Firestore.
2. **יעדים שבועיים**: אגרגציית 7 ימים של `weeklyAerobicMinutes` (יעד 150) ו-`weeklyStrengthWorkouts` (יעד 2), עם progress bars בדשבורד, ובהקשר של ה-insights prompt.

ודא ש-`buildMealSystemInstruction` עדiין כולל את השורה "Do NOT diagnose, give medical advice, or recommend diets" — חשוב לעקביות עם חלק ב.

---

## חלק ב — בנייה (חדש)

ממש את המפרט המלא ב-`docs/plan-revision-goals-and-focus.md`. תקציר:

### 1. הרחבת הפרופיל
- `primaryGoal`: "lose" | "maintain" | "gain" — בחירה בהרשמה.
- `activityLevel`: "sedentary"|"light"|"moderate"|"very"|"extra" — בחירה בהרשמה; מקדם TDEE; **שדה חובה**.
- `focusAreas`: string[] אופציונלי — הצהרה-עצמית (checkboxes), למשל "menopause". **לעולם לא מוסק אוטומטית מגיל/מין.**
- `goalOverrides`: object אופציונלי — דריסה ידנית של יעדים מחושבים.
עדכן: Firestore schema, Android model + DTO, מסך הרשמה/פרופיל.

### 2. חישוב יעדים (לפי goal-setting-research.md)
- קלוריות: Mifflin-St Jeor → BMR → ×מקדם לפי activityLevel → TDEE → התאמה ל-primaryGoal (lose=גירעון ~500; maintain=TDEE; gain=עודף מתון). **אזהרה אם גירעון/עודף > 35% מ-TDEE.**
- צעדים: ברירת מחדל 7,000-8,000.
- חלבון: 1.2-1.6 ג/ק"ג. שומן: 0.8-1.0 ג/ק"ג. פחמימות: יתרת הקלוריות.
- שינה: 7-9 שעות (7-8 לגיל 65+). מים: ~3 ל' גברים / ~2.1 ל' נשים.
- **goalOverrides גובר** על כל יעד מחושב.
- **Edge cases:** נתון פרופיל חסר → נסיגה בטוחה ליעדי ברירת מחדל (כ-2000 קק"ל, ~2000-2500 מ"ל, 8000 צעדים) + חיווי שהיעד כללי. אל תקרוס.

### 3. מסך/אזור יעדים (UI)
הצג את היעדים המחושבים, ואפשר דריסה ידנית (goalOverrides). שלב את היעדים בתצוגות הקיימות (קלוריות בטאב אוכל, צעדים בטאב פעילות, וכו').

### 4. תוכן רגיש — מבוסס הצהרה-עצמית בלבד (insights prompt)
- ב-`buildInsightsUserPrompt`: הוסף primaryGoal ו-focusAreas (אם קיימים) כהקשר.
- ב-`buildInsightsSystemInstruction`: **אסור** לוגיקה שבודקת gender/age להסקת מצב רפואי. תוכן רגיש (כגון "menopause") מוצג **רק** אם המשתמש סימן אותו ב-focusAreas, תמיד כמידע כללי אמפתי + דיסקליימר + הפניה לרופא, ללא מרשם דיאטה/מינון/אבחון. (הניסוח המלא ב-plan-revision, סעיף insights-prompt.)
- דיסקליימר קבוע (HE) ליד כל תוכן בריאותי, גם ב-UI: "מידע כללי ואינו תחליף לייעוץ רפואי. מומלץ להתייעץ עם רופא/ה."

---

## טסטים (חובה)
- חישוב BMR/TDEE לדוגמאות ידועות; התאמה ל-lose/maintain/gain; אזהרת >35%.
- goalOverrides גובר על מחושב; edge case של נתון חסר → ברירת מחדל, לא קריסה.
- insights כולל focusAreas רק כשהוצהר; לא מופעל מ-gender/age.
- תוכן menopause מופיע רק כש-focusAreas מכיל "menopause", תמיד עם disclaimer.
- אימות (חלק א): טסטים קיימים של quality + weekly עדiין עוברים.

## אסור
- אסור לבנות לוגיקה שמסיקה מצב רפואי מגיל+מין.
- אסור מרשם דיאטה/מינון/טיפול — רק מידע כללי + הפניה לרופא.
- אל תיגע בהסרת אופציית "other" (משימה נפרדת).
- אל תעשה `git push` בלי אישור.

## סיום
הרץ `./gradlew assembleDebug` + `cd functions && npm run test` + `./gradlew test`. דווח [✓ הושלם] / [⚠ חסום: סיבה], וספק smoke test קצר (הגדרת מטרה, צפייה ביעד מחושב, דריסה ידנית, סימון focusArea וצפייה בתוכן + דיסקליימר).

אם משהו בחלק א חסר/סוטה מההגדרות, או אם אתה מגלה תלות לא צפויה — עצור ודווח לפני שתמשיך.
