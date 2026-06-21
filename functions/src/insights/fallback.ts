import { DayData } from "./aggregate";
import { ParsedInsights, DISCLAIMER_HE } from "./insightsParse";
import { WEEKLY_AEROBIC_GOAL_MIN, WEEKLY_STRENGTH_GOAL, dailyStepsGoal } from "./goals";

/**
 * Deterministic, non-AI insights for a day with no logged meals.
 * Returns the same shape as parsed Gemini output so it flows through writeInsights
 * unchanged. All copy is Hebrew (product language); the activity line is linked to
 * the user's daily steps goal and the fixed weekly exercise goals.
 */
export function buildFallbackInsights(day: DayData): ParsedInsights {
  const stepsGoal = dailyStepsGoal(day.profile);
  const hasActivity = day.steps > 0 || day.workouts.length > 0;

  const activity = hasActivity
    ? `כל הכבוד על הפעילות היום! צברת ${day.steps} צעדים מתוך יעד של ${stepsGoal}, והשבוע ${day.weeklyAerobicMinutes} מתוך ${WEEKLY_AEROBIC_GOAL_MIN} דק' אירובי ו-${day.weeklyStrengthWorkouts} מתוך ${WEEKLY_STRENGTH_GOAL} אימוני כוח — שווה להמשיך כך.`
    : `עוד לא תועדה פעילות היום; כל תנועה נחשבת — כדאי לשאוף ל-${stepsGoal} צעדים ביום, ${WEEKLY_AEROBIC_GOAL_MIN} דק' אירובי ו-${WEEKLY_STRENGTH_GOAL} אימוני כוח בשבוע.`;

  return {
    today: {
      general:
        "עדיין לא רשמת ארוחות היום, אז אין מספיק נתונים לסיכום תזונתי — כדאי לעדכן את הארוחות והמים כדי לקבל תובנות מדויקות.",
      nutrition:
        "לא תועדו ארוחות היום; כדאי להוסיף את מה שאכלת ולעדכן את כמות המים כדי שנוכל לתת משוב תזונתי.",
      activity,
      sleep:
        "שינה סדירה ואיכותית תורמת לאנרגיה ולריכוז — כדאי לשמור על שעות שינה קבועות.",
    },
    tomorrow: {
      nutrition:
        "מחר כדאי לתעד את הארוחות והמים לאורך היום כדי לקבל תמונה תזונתית מלאה.",
      activity: `המשך לשאוף ל-${WEEKLY_AEROBIC_GOAL_MIN} דק' פעילות אירובית ו-${WEEKLY_STRENGTH_GOAL} אימוני כוח בשבוע.`,
      sleep: "כדאי לכוון לשעת שינה קבועה כדי להתעורר רענן יותר.",
    },
    disclaimer: DISCLAIMER_HE,
  };
}
