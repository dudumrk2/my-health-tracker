<div dir="rtl">

# Agent Blueprint — פייז 1: תשתית + בריאות

> מסמך הנחיה לסוכן מימוש. מוגבל לפייז 1 בלבד. אין לממש פייז 2 (יומן אכילה) או פייז 3 (סיכום יומי).

</div>

```xml
<agent_blueprint phase="1" name="Foundation + Health Connect">

  <system_context>
    System Goal: אפליקציית אנדרואיד אישית שאוספת נתוני פעילות ושינה מ-Health Connect,
    מאפשרת רישום ארוחות עם ניתוח AI, ומפיקה סיכום יומי. פייז 1 בונה את התשתית ואת
    שכבת הבריאות בלבד.

    Tech Stack (פייז 1):
    - Android Native, Kotlin (latest stable), minSdk 28+, targetSdk latest
    - Jetpack Compose ל-UI, ארכיטקטורת MVVM, Coroutines + Flow
    - Firebase Auth (Google Sign-In), Cloud Firestore (KTX SDK)
    - Health Connect SDK (androidx.health.connect:connect-client)
    - WorkManager לסנכרון רקע תקופתי

    Out of Scope (אסור לממש בפייז זה):
    - יומן אכילה / קלט מצלמה / קריאות Gemini / Cloud Functions
    - סיכום יומי / Cloud Scheduler
    - סוגי Health Connect מעבר ל-Steps, SleepSession, ExerciseSession

    Success Criteria:
    - המשתמש מתחבר עם Google ומסך הפרופיל נשמר ל-Firestore
    - הרשאות Health Connect מתבקשות ומטופלות (כולל מקרה דחייה / Health Connect לא מותקן)
    - נתוני צעדים, שינה ואימונים נקראים מקומית ומסונכרנים ל-Firestore
    - מסך תצוגה מציג את נתוני היום
  </system_context>

  <input_context>
    Firestore data model (פייז 1 בלבד — תחת users/{uid}):

    profile (document):
      birthYear: number
      gender: string              ("male" | "female" | "other")
      weightKg: number
      heightCm: number
      createdAt: timestamp
      updatedAt: timestamp

    healthDaily/{yyyy-MM-dd} (document):
      date: string (yyyy-MM-dd)
      steps: number
      sleepMinutes: number
      sleepSessions: [{ start: timestamp, end: timestamp }]
      workouts: [{ type: string, durationMin: number, startTime: timestamp, source: "health_connect"|"manual" }]
      syncedAt: timestamp
      source: "health_connect" | "mixed"

    Firestore Security Rules: משתמש קורא/כותב רק תחת users/{uid} שלו.
  </input_context>

  <task_directive>
    ממש את שכבת התשתית והבריאות של אפליקציית האנדרואיד.

    מבנה מודולרי מוצע (התאם לפי קונבנציות הפרויקט):
    - app/ — Compose UI, navigation, ViewModels
    - data/auth/ — עטיפת Firebase Auth + Google Sign-In
    - data/profile/ — ProfileRepository (קריאה/כתיבה ל-Firestore)
    - data/health/ — HealthConnectManager (הרשאות + קריאת נתונים)
    - data/health/HealthRepository — מיפוי נתוני Health Connect → מודל Firestore + כתיבה
    - sync/ — HealthSyncWorker (WorkManager)
    - ui/profile/, ui/dashboard/ — מסכים + ViewModels

    דרישות מימוש:
    1. Google Sign-In דרך Firebase Auth; שמירת מצב התחברות; מסך התחברות + logout.
    2. מסך פרופיל ראשוני: שנת לידה, מין (Male/Female/Other), משקל (ק"ג), גובה (ס"מ). ולידציה בסיסית
       (טווחים סבירים). כתיבה ל-users/{uid}/profile. הגיל מחושב משנת הלידה בצד הלקוח.
       מין ופרופיל פיזי מועברים ל-Gemini כהקשר — לשמור ב-Firestore.
    3. HealthConnectManager: בדיקת זמינות Health Connect (מותקן/לא מותקן/לא נתמך),
       בקשת הרשאות קריאה ל-Steps, SleepSession, ExerciseSession, וטיפול בדחייה.
    4. קריאת נתוני היום (ובאופן כללי לפי טווח תאריכים): צעדים מצטברים, משך שינה
       וסשנים, אימונים (סוג/משך/זמן התחלה). כל workout מ-Health Connect מקבל `source: "health_connect"`.
    5. הוספת אימון ידנית: מסך/דיאלוג לבחירת סוג אימון, משך, ותאריך/שעה. נשמר ל-healthDaily/{date}
       עם `source: "manual"`. אם ביום יש גם Health Connect וגם ידני — `healthDaily.source = "mixed"`.
    6. מיפוי ל-healthDaily/{date} וכתיבה ל-Firestore (idempotent — עדכון לפי מפתח תאריך).
    7. HealthSyncWorker (WorkManager): סנכרון תקופתי (~6 שעות) + טריגר בפתיחת אפליקציה.
       טיפול במצב ללא הרשאות / ללא רשת (לדחות, לא לקרוס).
    8. מסך Dashboard: הצגת צעדים/שינה/אימונים (כולל ידניים) של היום + מצב סנכרון אחרון.
  </task_directive>

  <test_requirements>
    Unit tests:
    - חישוב גיל משנת לידה
    - ולידציית פרופיל (טווחים, ערכים חסרים, ערכי gender תקינים)
    - מיפוי רשומות Health Connect → מודל healthDaily (כולל יום ריק / ללא נתונים)
    - אגרגציית משך שינה ממספר סשנים
    - מיזוג workouts ידניים עם workouts מ-Health Connect באותו יום (source נכון, ללא כפילויות)

    Instrumented / repository tests:
    - ProfileRepository: כתיבה וקריאה (Firestore emulator)
    - HealthRepository: כתיבה idempotent לאותו תאריך (כתיבה כפולה לא מכפילה)
    - HealthConnectManager: התנהגות כש-Health Connect לא זמין / הרשאות נדחו (mock)

    Framework: JUnit + MockK; Firebase Local Emulator Suite ל-Firestore/Auth.
  </test_requirements>

  <acceptance_criteria>
    ✓ Google Sign-In עובד; מצב התחברות נשמר בין הפעלות
    ✓ פרופיל (כולל gender) נשמר ונקרא נכון מ-users/{uid}/profile
    ✓ זרימת הרשאות Health Connect מטופלת: ניתנו / נדחו / Health Connect לא מותקן
    ✓ צעדים, שינה ואימונים של היום נקראים ומוצגים ב-Dashboard
    ✓ אימון ידני נשמר עם source="manual" ומופיע ב-Dashboard לצד אימוני Health Connect
    ✓ כתיבה ל-healthDaily/{date} idempotent (אין כפילויות בכתיבה חוזרת)
    ✓ HealthSyncWorker רץ תקופתית ולא קורס ללא רשת/הרשאות
    ✓ כל הטסטים עוברים
    ✓ אין מפתחות/סודות בקוד הלקוח
    ✓ Firestore גישה מוגבלת ל-uid המחובר (נבדק מול Security Rules)
  </acceptance_criteria>

  <constraints>
    - אל תממש קלט ארוחה, מצלמה, Gemini, Cloud Functions, או סיכום יומי
    - אל תוסיף סוגי Health Connect מעבר ל-Steps, SleepSession, ExerciseSession
    - אל תאחסן תמונות
    - בקש רק הרשאות Health Connect שבשימוש בפועל (עיקרון המינימום)
    - אל תכתוב נתוני בריאות גולמיים מחוץ ל-users/{uid}
    - השתמש ב-KTX / Coroutines APIs; ללא קריאות חוסמות ב-main thread
    - טפל במפורש במקרה ש-Health Connect לא מותקן (הפנה להתקנה מ-Play Store)
  </constraints>

  <dependencies>
    - פרויקט Android Studio מאותחל עם Firebase (google-services.json)
    - פרויקט Firebase: Auth (Google provider) + Firestore מופעלים
    - Health Connect מותקן/זמין במכשיר הבדיקה (או מכשיר עם Android 14+)
    - Firebase Local Emulator Suite להרצת טסטים
  </dependencies>

</agent_blueprint>
```
