# מפרט עיצוב: אנימציית מעבר ושינוי צבע תאריכים היסטוריים

מסמך זה מתאר את מפרט העיצוב להוספת אנימציית מעבר (fade-in/out) ושינוי צבע של כרטיס התאריך הנבחר כאשר הוא אינו היום הנוכחי (היום), הן במסך האוכל והן במסך הפעילות/אימון.

## דרישות ועיצוב חזותי

1. **שינוי צבע כרטיס תאריך נבחר:**
   - כאשר נבחר תאריך שהוא **היום הנוכחי** (`LocalDate.now()`), כרטיס התאריך יישאר בצבע הירוק המיתוגי הראשי (`MaterialTheme.colorScheme.primary`).
   - כאשר נבחר תאריך שהוא **שונה מהיום הנוכחי** (כלומר יום מהעבר או מהעתיד), כרטיס התאריך ייצבע בגוון Slate (כחול-אפרפר) כדי לשקף שמדובר בצפייה בהיסטוריה ולא ביום הפעיל הנוכחי.
   - הצבעים יותאמו למצב לייט ודארק בנפרד.

2. **אנימציית מעבר לתוכן:**
   - בעת החלפת התאריך הנבחר, תוכן המסך שמתחת לרצועת התאריכים הראשי יעבור אנימציית עמעום חלק (`Crossfade` או `AnimatedContent` עם `fadeIn`/`fadeOut`) באורך של 300ms.
   - האנימציה תמנע "קפיצה" מיידית של הנתונים ותקנה חוויית משתמש איכותית ונעימה (Premium Feel).

## שינויים מוצעים בקוד

### 1. [com.myhealthtracker.app.theme](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/theme/Color.kt)
הגדרת גווני Slate ייעודיים ב-[Color.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/theme/Color.kt):
```kotlin
val SlateSelectedLight = Color(0xFF546E7A)
val SlateSelectedDark = Color(0xFF78909C)
```

### 2. [com.myhealthtracker.app.ui.food](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/ui/food/FoodScreen.kt)
עדכון מסך האוכל:
- עדכון צבע הרקע והטקסט של כרטיס התאריך הנבחר לפי התנאי `date == LocalDate.now()`.
- עטיפת גוף התוכן (`LazyColumn`) ב-`Crossfade` המגיב לשינויים ב-`state.selectedDate`.

### 3. [com.myhealthtracker.app.ui.activity](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/ui/activity/ActivityScreen.kt)
עדכון מסך הפעילות:
- עדכון צבע הרקע והטקסט של כרטיס התאריך הנבחר לפי התנאי `date == LocalDate.now()`.
- עטיפת גוף התוכן (`LazyColumn`) ב-`Crossfade` המגיב לשינויים ב-`state.selectedDate`.

## תוכנית אימות (Verification Plan)

### בדיקות ידניות
- הרצת האפליקציה באמולטור/מכשיר פיזי ומעבר בין ימים שונים במסך האוכל ומסך הפעילות.
- וידוא שרק היום הנוכחי (היום) מופיע בירוק כאשר הוא נבחר, וימים אחרים מופיעים בצבע כחול-אפרפר (Slate).
- וידוא מעבר אנימציה חלק (Fade) בעת החלפת הימים ולא קפיצה פתאומית.
- וידוא התנהגות נכונה במצב לייט ובמצב דארק.

### בדיקות אוטומטיות
- הרצת `./gradlew test` לוודא שלא שברנו אף בדיקת יחידה קיימת.
- וידוא קומפילציה מוצלחת של האפליקציה באמצעות `./gradlew assembleDebug`.
