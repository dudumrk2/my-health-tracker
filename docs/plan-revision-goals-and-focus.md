<div dir="rtl">

# תוספת/תיקון לתוכנית — מטרות שימוש + הצהרה-עצמית (במקום זיהוי דמוגרפי אוטומטי)

> מסמך זה **מחליף** את סעיף "Demographics & Menopause" בתוכנית המקורית, ומוסיף מודל מטרות שימוש.
> שאר התוכנית (תגיות איכות מזון, יעדים שבועיים, המלצות שדרוג) — **נשאר ללא שינוי ותקף**.
> עיקרון מנחה: האפליקציה לעולם לא מסיקה מצב רפואי מגיל/מין. תוכן רגיש מוצג רק לפי הצהרת המשתמש, עם דיסקליימר והפניה לרופא.

</div>

```xml
<plan_revision name="Goals + Self-Declared Focus (replaces auto-demographics)">

  <rationale>
    זיהוי אוטומטי של "אישה 40+/45+ בגיל המעבר" = הסקת מצב רפואי מגיל+מין, והתאמת ייעוץ
    תזונתי-הורמונלי. זהו ייעוץ רפואי, וגם סותר את הכלל הקיים ב-buildMealSystemInstruction
    ("Do NOT diagnose, give medical advice, or recommend diets"). מוחלף בגישת הצהרה-עצמית:
    המשתמש בוחר מטרה ומסמן תחומים רלוונטיים בעצמו → אז זו בחירתו, לא אבחון של המערכת.
  </rationale>

  <!-- ============ 1. מודל נתונים: מטרות + הצהרה-עצמית ============ -->
  <change area="profile-data-model">
    הוסף ל-profile (Firestore + Android model + DTO):
    - primaryGoal: string  ("lose" | "maintain" | "gain")  — נבחר בהרשמה
    - focusAreas: string[]? — שדה אופציונלי, ערכים שהמשתמש מסמן בעצמו (checkboxes).
                              דוגמאות ערכים: "menopause", "muscle_gain", "heart_health".
                              לעולם לא מחושב/מוסק — רק קלט ישיר מהמשתמש.
    - goalOverrides: object? — דריסה ידנית של יעדים מחושבים. שדות אופציונליים:
                              { caloriesKcal?, steps?, proteinG?, sleepHours?, waterMl? }
  </change>

  <!-- ============ 2. מסך הרשמה / פרופיל ============ -->
  <change area="profile-ui">
    בהרשמה (ובעריכת פרופיל), הוסף:
    1. בחירת מטרת שימוש (single-select): "ירידה במשקל" / "שמירה על המשקל" / "עלייה במשקל".
    1a. בחירת רמת פעילות (single-select): יושבני / פעיל קל / בינוני / פעיל מאוד / אקסטרה.
        משמש כמקדם TDEE — שדה חובה לחישוב קלורי מדויק.
    2. שדה הצהרה-עצמית (multi-select, אופציונלי לחלוטין) — "תחומים שחשובים לי", עם
       אפשרויות שהמשתמש מסמן. כל אפשרות שנוגעת לבריאות מלווה בטקסט עדין:
       "מידע כללי בלבד — אינו תחליף לייעוץ רפואי".
    3. (בהמשך/אופציונלי) מסך יעדים שמציג את היעדים המחושבים ומאפשר דריסה ידנית (goalOverrides).
  </change>

  <!-- ============ 3. חישוב יעדים (ברירת מחדל מהפרופיל) ============ -->
  <change area="goal-computation">
    יישם חישוב יעדים לפי goal-setting-research.md:
    - קלוריות: Mifflin-St Jeor → BMR → ×מקדם פעילות (לפי activityLevel) → TDEE → התאמה ל-primaryGoal
      (lose = גירעון ~500 kcal; maintain = TDEE; gain = עודף מתון).
      אזהרה אם הגירעון/עודף > 35% מ-TDEE.
    - צעדים: ברירת מחדל 7,000-8,000 (לא 10,000).
    - חלבון: 1.2-1.6 ג/ק"ג. שומן: 0.8-1.0 ג/ק"ג. פחמימות: יתרת הקלוריות.
    - שינה: 7-9 שעות (7-8 לגיל 65+).
    - מים: ~3 ל' (גברים) / ~2.1 ל' (נשים) מהשתייה.
    כל יעד: אם קיים goalOverrides → הערך הידני גובר על המחושב.
    - **activityLevel** נבחר ע"י המשתמש בהרשמה (sedentary/light/moderate/very/extra) ומכתיב את מקדם ה-TDEE.
    - **Edge cases:** אם חסר נתון פרופיל (גובה/משקל/גיל/מין/activityLevel) — נסיגה בטוחה ליעדי
      ברירת מחדל (כ-2000 קק"ל, ~2000-2500 מ"ל מים, 8000 צעדים), והצגה למשתמש שהיעד כללי עד השלמת הפרופיל.
      אסור לקרוס או לחשב על נתון חסר.
  </change>

  <!-- ============ 4. AI Insights — הצהרה-עצמית במקום זיהוי אוטומטי ============ -->
  <change area="insights-prompt">
    ב-buildInsightsUserPrompt: הוסף את primaryGoal ואת focusAreas (אם קיימים) כהקשר.
    ב-buildInsightsSystemInstruction — **החלף** את סעיף ה-Demographics האוטומטי בזה:

    "User-Declared Focus Guidelines:",
    "- Tailor tone toward the user's declared primaryGoal (lose/maintain/gain) supportively.",
    "- ONLY if the user has SELF-DECLARED a focus area in focusAreas, you may reference it.",
    "  Never infer any health/medical state from age or gender.",
    "- If focusAreas includes 'menopause': you may share GENERAL, non-prescriptive information",
    "  (e.g. that strength training helps preserve muscle, that whole foods support metabolic",
    "  health), always phrased as general wellness info, in an empathetic tone, and ALWAYS",
    "  append a brief note recommending consulting a doctor for personalized guidance.",
    "  Do NOT prescribe a diet, dosage, or treatment. Do NOT diagnose.",
    "- For any health-related focus area, prefer 'you might consider...' + 'consult a clinician'",
    "  over directive statements.",

    חשוב: הסר כל לוגיקה שבודקת gender=="female" && age>=40/45. הזיהוי היחיד הוא focusAreas.
  </change>

  <!-- ============ 5. עקביות עם Contract A ============ -->
  <note>
    תגיות איכות המזון (processedScore, insulinImpact וכו') נשארות כפי שתוכננו — הן תיאוריות
    (מתארות את המזון), לא מרשמות. זה תקין. רק ודא ש-buildMealSystemInstruction עדיין כולל
    את השורה "Do NOT diagnose, give medical advice, or recommend diets" — אין סתירה יותר,
    כי חלק ה-insights כבר לא מאבחן.
  </note>

  <!-- ============ 6. אזהרה/דיסקליימר ============ -->
  <change area="disclaimer">
    כל תובנה שנוגעת ל-focusArea בריאותי תכלול disclaimer קצר וקבוע (HE):
    "מידע כללי ואינו תחליף לייעוץ רפואי. מומלץ להתייעץ עם רופא/ה."
    הצג אותו גם ב-UI ליד תוכן כזה, לא רק בטקסט ה-AI.
  </change>

  <tests>
    - חישוב יעדים: BMR/TDEE לדוגמאות ידועות; התאמה ל-lose/maintain/gain; אזהרת >35%.
    - goalOverrides גובר על מחושב.
    - insights prompt כולל focusAreas רק כשהוצהר; לא מופעל אוטומטית מ-gender/age.
    - תוכן menopause מופיע רק כש-focusAreas מכיל "menopause", תמיד עם disclaimer.
  </tests>

  <constraints>
    - אסור לבנות לוגיקה שמסיקה גיל המעבר / מצב רפואי מגיל+מין.
    - אסור מרשם דיאטה/מינון/טיפול. רק מידע כללי + הפניה לרופא.
    - אל תשנה את חלקי התוכנית האחרים (איכות מזון, יעדים שבועיים) מעבר לנדרש לעקביות.
  </constraints>

</plan_revision>
```
