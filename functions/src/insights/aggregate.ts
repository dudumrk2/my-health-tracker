import { getFirestore } from "firebase-admin/firestore";

export interface DayProfile {
  gender?: string;
  weightKg?: number;
  heightCm?: number;
  age?: number;
}

export interface DayWorkout {
  type: string;
  durationMin: number;
}

export interface DayMealTotals {
  calories: number;
  proteinG: number;
  carbsG: number;
  fatG: number;
}

export interface DayData {
  date: string;
  profile: DayProfile | null;
  steps: number;
  sleepMinutes: number;
  workouts: DayWorkout[];
  meals: { count: number; totals: DayMealTotals };
  waterMl: number;
  hasHealthData: boolean;
  hasMeals: boolean;
  isEmpty: boolean;
  weeklyAerobicMinutes: number;
  weeklyStrengthWorkouts: number;
}

/** Raw documents gathered from Firestore, shaped as `DocumentSnapshot.data()` plain objects. */
export interface RawDayInputs {
  date: string;
  currentYear: number;
  userDoc: Record<string, unknown> | null;
  healthDaily: Record<string, unknown> | null;
  meals: Array<Record<string, unknown>>;
  water: Record<string, unknown> | null;
}

function num(v: unknown): number {
  return typeof v === "number" && isFinite(v) ? v : 0;
}

function optNum(v: unknown): number | undefined {
  return typeof v === "number" && isFinite(v) ? v : undefined;
}

function buildProfile(userDoc: Record<string, unknown> | null, currentYear: number): DayProfile | null {
  const profile = (userDoc?.profile ?? null) as Record<string, unknown> | null;
  if (!profile) return null;
  const birthYear = optNum(profile.birthYear);
  return {
    gender: typeof profile.gender === "string" ? profile.gender : undefined,
    weightKg: optNum(profile.weightKg),
    heightCm: optNum(profile.heightCm),
    age: birthYear !== undefined ? currentYear - birthYear : undefined,
  };
}

/** Pure aggregation of already-fetched documents into a typed DayData. */
export function buildDayData(input: RawDayInputs): DayData {
  const health = input.healthDaily ?? {};
  const steps = num(health.steps);
  const sleepMinutes = num(health.sleepMinutes);
  const workoutsRaw = Array.isArray(health.workouts) ? (health.workouts as unknown[]) : [];
  const workouts: DayWorkout[] = workoutsRaw.map((w) => {
    const o = (w ?? {}) as Record<string, unknown>;
    return { type: typeof o.type === "string" ? o.type : "Exercise", durationMin: num(o.durationMin) };
  });

  const totals: DayMealTotals = { calories: 0, proteinG: 0, carbsG: 0, fatG: 0 };
  for (const m of input.meals) {
    const t = ((m ?? {}).totals ?? {}) as Record<string, unknown>;
    totals.calories += num(t.calories);
    totals.proteinG += num(t.proteinG);
    totals.carbsG += num(t.carbsG);
    totals.fatG += num(t.fatG);
  }

  const waterMl = num(input.water?.amountMl);
  const hasHealthData = steps > 0 || sleepMinutes > 0 || workouts.length > 0;
  const hasMeals = input.meals.length > 0;

  return {
    date: input.date,
    profile: buildProfile(input.userDoc, input.currentYear),
    steps,
    sleepMinutes,
    workouts,
    meals: { count: input.meals.length, totals },
    waterMl,
    hasHealthData,
    hasMeals,
    isEmpty: !hasHealthData && !hasMeals && waterMl <= 0,
    weeklyAerobicMinutes: 0,
    weeklyStrengthWorkouts: 0,
  };
}

/** Gathers a user's documents for `date` from Firestore and aggregates them. */
export async function fetchDayData(uid: string, date: string): Promise<DayData> {
  const db = getFirestore();
  const userRef = db.doc(`users/${uid}`);

  const baseDate = new Date(date);
  const minDateObj = new Date(baseDate);
  minDateObj.setDate(baseDate.getDate() - 6);
  const minDate = minDateObj.toISOString().split("T")[0];

  const [userSnap, healthSnap, mealsSnap, waterSnap, weeklyHealthSnap] = await Promise.all([
    userRef.get(),
    userRef.collection("healthDaily").doc(date).get(),
    userRef.collection("meals").where("date", "==", date).get(),
    userRef.collection("water").doc(date).get(),
    userRef.collection("healthDaily")
      .where("__name__", ">=", minDate)
      .where("__name__", "<=", date)
      .get()
  ]);

  let weeklyAerobicMinutes = 0;
  let weeklyStrengthWorkouts = 0;

  for (const doc of weeklyHealthSnap.docs) {
    const data = doc.data();
    const workouts = Array.isArray(data.workouts) ? data.workouts : [];
    for (const w of workouts) {
      const type = (w.type || "").toLowerCase();
      const duration = typeof w.durationMin === "number" ? w.durationMin : 0;
      if (["ריצה", "הליכה", "אופניים", "ספינינג", "זומבה", "running", "walking", "cycling", "spinning", "zumba"].includes(type)) {
        weeklyAerobicMinutes += duration;
      } else if (["כוח", "פונקציונלי", "strength", "functional"].includes(type)) {
        weeklyStrengthWorkouts += 1;
      }
    }
  }

  const dayData = buildDayData({
    date,
    currentYear: new Date().getUTCFullYear(),
    userDoc: userSnap.exists ? (userSnap.data() as Record<string, unknown>) : null,
    healthDaily: healthSnap.exists ? (healthSnap.data() as Record<string, unknown>) : null,
    meals: mealsSnap.docs.map((d) => d.data() as Record<string, unknown>),
    water: waterSnap.exists ? (waterSnap.data() as Record<string, unknown>) : null,
  });

  dayData.weeklyAerobicMinutes = weeklyAerobicMinutes;
  dayData.weeklyStrengthWorkouts = weeklyStrengthWorkouts;
  return dayData;
}
