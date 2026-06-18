# עיצוב — עריכת מרכיבי ארוחה מנותחת (soft-delete + שחזור)

**תאריך:** 2026-06-18
**פייז:** 2 (יומן אכילה) — שיפור זרימת `AddMeal` הקיימת
**קבצים מושפעים:**
`app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealViewModel.kt`,
`app/src/main/java/com/myhealthtracker/app/ui/meal/AddMealScreen.kt`,
`app/src/test/java/com/myhealthtracker/app/ui/meal/AddMealViewModelTest.kt`

## הבעיה

לאחר ניתוח AI של ארוחה (טקסט או תמונה), המשתמש רואה רשימת מרכיבים הניתנת לעריכה במסך
`ResultState`. כיום אפשר רק לערוך פריט קיים — **אין דרך להסיר מרכיב**, וכפתור "הוספת פריט
נוסף" שבור (קורא ל-`updateItem` עם `index == size`, שנדחה). המשתמש ביקש יכולת להסיר מרכיב
ולחשב מחדש את הקלוריות/מאקרו.

## תובנות מהקוד הקיים

1. **חישוב מחדש הוא "חינמי".** ה-`totals` הם ערך נגזר (`sumOf` על רשימת הפריטים) ולא מצב
   נפרד שנשמר — ב-`saveMeal` וב-`ResultStateContent`. לכן אין צורך בחיסור ידני; ברגע
   שפריט יוצא מהחישוב, הסכום מתעדכן אוטומטית. יש לשמר את הדפוס הזה ולא להחזיק `totals`
   כ-state עצמאי.
2. **הפער היחיד:** אין פעולת הסרה ב-ViewModel ואין affordance ב-UI.
3. **ריח ארכיטקטוני:** `ResultStateContent` מחזיק עותק מקומי
   `var itemsList by remember(recognizedItems)` בעוד העריכות הולכות ל-ViewModel — שני
   מקורות אמת שנשארים מסונכרנים רק במקרה. יש להסירו ולקרוא ישירות מה-ViewModel.
4. **באג קיים:** `updateItem` דוחה `index == size`, ולכן "הוספת פריט נוסף" אינה מוסיפה
   כלום. מתוקן באותה הזדמנות כי הוא נובע מאותו שורש.

## ההחלטה: soft-delete עם שחזור (לא מחיקה קשה, לא דיאלוג)

לחיצה על איקון ההסרה **אינה** מוחקת את הפריט מהרשימה — היא מסמנת אותו כ"מוסר":

- הפריט מוצג עם קו חוצה (`LineThrough`), כרטיס מעומעם, והאיקון מתחלף ל"שחזור".
- הפריט המסומן **יוצא מחישוב ה-totals**.
- לחיצה נוספת על השחזור מחזירה אותו לחישוב.
- שום פריט לא נמחק בפועל עד השמירה; בשמירה נשמרים רק הפריטים הפעילים.

הסיבה להעדפת soft-delete על דיאלוג אישור / Snackbar-undo: הוא הפיך, נשאר גלוי וברור עד
השמירה, ואין חלון שנעלם או הפרעה לזרימה. ההחלטה התקבלה מול חלופות אלו במפורש.

## ארכיטקטורה

### ViewModel — מקור אמת יחיד (`AddMealViewModel`)

- `_recognizedItems: MutableStateFlow<List<MealItem>>` — מחזיק את **כל** הפריטים (קיים).
- `_excludedIndices: MutableStateFlow<Set<Int>>` — **חדש**: אינדקסים שסומנו כמוסרים.
  נחשף כ-`excludedIndices: StateFlow<Set<Int>>`.
- `toggleItemRemoved(index: Int)` — **חדש**: מוסיף/מסיר את האינדקס מהקבוצה (no-op אם
  האינדקס מחוץ לטווח הרשימה).
- `updateItem(index, item)` — מתוקן: מעדכן רק אם `index in indices`.
- `addItem(item: MealItem): Int` — **חדש**: מוסיף לסוף, מחזיר את האינדקס החדש. מחליף את
  הקריאה השבורה `updateItem(size, …)`.
- `saveMeal()` — שומר רק פריטים שאינם ב-`excludedIndices`; `totals` מחושבים מהפעילים
  בלבד. אם **כל** הפריטים מוסרים — no-op (לא נשמר; ראו "כל הפריטים מוסרים" למטה).

האינדקסים יציבים: אין מחיקה קשה, והוספה רק מוסיפה לסוף — לכן אינדקס של פריט קיים אינו
משתנה. (איפוס `excludedIndices` קורה ב-`resetToInput` / `switchToManualFallback` יחד עם
שאר ה-state.)

### UI (`AddMealScreen` — `ResultStateContent`)

- **הסרת** העותק המקומי `var itemsList by remember(recognizedItems)`. הקומפוזבל קורא
  ישירות מ-`recognizedItems` (פרמטר קיים) ומ-`excludedIndices` (פרמטר חדש).
- `editingIndex` נשאר UI state מקומי (טהור).
- חישוב `totals` עובר לפעול על הפריטים הפעילים בלבד:
  `recognizedItems.filterIndexed { i, _ -> i !in excludedIndices }`.
- כל כרטיס פריט:
  - **פעיל:** תצוגה רגילה; האיקון בכותרת = פח (`Icons.Default.Delete`, גוון `error`)
    לצד איקון העריכה הקיים.
  - **מוסר:** שם עם `textDecoration = LineThrough`, אלפא מופחת על הכרטיס; האיקון =
    שחזור (`Icons.Default.Undo` / `Refresh`).
  - שתי המצבים קוראים ל-`onToggleRemoved(index)`.
- "הוספת פריט נוסף": `val newIndex = onItemAdd(newItem); editingIndex = newIndex`.
- callbacks חדשים שהקומפוזבל מקבל: `onToggleRemoved: (Int) -> Unit`,
  `onItemAdd: (MealItem) -> Int`. הקיים `onItemUpdate` נשמר.

### כל הפריטים מוסרים

כאשר `excludedIndices` מכסה את כל הפריטים, הארוחה ריקה אפקטיבית:

- כפתור **השמירה מושבת** (`enabled = false`) עם רמז `"כל הפריטים הוסרו"`.
- **אין סגירה אוטומטית.** המשתמש יוצא בלי לשמור דרך כפתור ה-X הקיים ב-TopAppBar.
- הערת השגיאה הישנה "אין פריטים לשמירה" יורדת — היא כבר לא רלוונטית.

## טיפול בשגיאות

- `toggleItemRemoved`/`updateItem` עם אינדקס מחוץ לטווח → no-op (לא קורס).
- `saveMeal` כשכל הפריטים מוסרים → no-op (כפתור ממילא מושבת; ההגנה ב-ViewModel היא
  שכבת ביטחון).

## טסטים (`AddMealViewModelTest`)

1. `toggleItemRemoved` מוסיף אינדקס לקבוצה, וקריאה חוזרת מסירה אותו (toggle).
2. `toggleItemRemoved` עם אינדקס לא חוקי → `excludedIndices` ללא שינוי, לא קורס.
3. `addItem` מוסיף פריט לסוף ומחזיר את האינדקס הנכון (`size - 1`).
4. `updateItem` עם `index == size` (מצב ההוספה הישן) → no-op, לא קורס.
5. `saveMeal` מתעלם מפריטים מוסרים — שומר רק את הפעילים, ו-`totals` משקפים את הפעילים
   בלבד (אימות חישוב-מחדש מקצה-לקצה).
6. `saveMeal` כשכל הפריטים מוסרים → לא נשמרת ארוחה.

## מחוץ להיקף (YAGNI)

- עריכת/מחיקת ארוחה **שכבר נשמרה** ב-Firestore (קיים `deleteMeal` נפרד, לא מטופל כאן).
- swipe-to-delete, אנימציות, Snackbar-undo.
- שינוי סדר פריטים.
