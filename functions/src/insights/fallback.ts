import { DayData } from "./aggregate";
import { ParsedInsights, DISCLAIMER_HE, DISCLAIMER_EN } from "./insightsParse";
import { WEEKLY_AEROBIC_GOAL_MIN, WEEKLY_STRENGTH_GOAL, dailyStepsGoal } from "./goals";

/**
 * Deterministic, non-AI insights for a day with no logged meals.
 * Returns the same shape as parsed Gemini output so it flows through writeInsights
 * unchanged. Supports both Hebrew and English based on the language parameter.
 */
export function buildFallbackInsights(day: DayData, language: string = "he"): ParsedInsights {
  const stepsGoal = dailyStepsGoal(day.profile);
  const hasActivity = day.steps > 0 || day.workouts.length > 0;
  const isEn = language === "en";

  if (isEn) {
    const activity = hasActivity
      ? `Great job on staying active today! You recorded ${day.steps} steps towards your goal of ${stepsGoal}, and this week ${day.weeklyAerobicMinutes} of ${WEEKLY_AEROBIC_GOAL_MIN} aerobic minutes and ${day.weeklyStrengthWorkouts} of ${WEEKLY_STRENGTH_GOAL} strength workouts — keep it up!`
      : `No activity recorded yet today; every bit of movement counts — aim for ${stepsGoal} steps daily, ${WEEKLY_AEROBIC_GOAL_MIN} aerobic min and ${WEEKLY_STRENGTH_GOAL} strength workouts weekly.`;

    return {
      today: {
        general:
          "You haven't logged any meals today yet, so there is not enough data for a nutrition summary — remember to log your meals and water for personalized insights.",
        nutrition:
          "No meals recorded today; consider logging what you ate and updating your water intake so we can provide nutritional feedback.",
        activity,
        sleep:
          "Consistent, quality sleep supports energy and focus — try to maintain regular sleep hours.",
      },
      tomorrow: {
        nutrition:
          "Tomorrow, try to log your meals and water throughout the day to get a complete nutrition picture.",
        activity: `Continue aiming for ${WEEKLY_AEROBIC_GOAL_MIN} minutes of aerobic exercise and ${WEEKLY_STRENGTH_GOAL} strength workouts per week.`,
        sleep: "Aim for a consistent bedtime to wake up feeling refreshed.",
      },
      disclaimer: DISCLAIMER_EN,
    };
  }

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
