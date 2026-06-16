# Quick Actions Notification Design

This document details the design for adding a persistent (ongoing) notification in the notification drawer with quick action buttons to log a meal, log a workout, or quickly increment daily water intake directly from the notification.

## User Review Required

> [!IMPORTANT]
> - **Notification Permissions**: Starting with Android 13 (API 33), applications must explicitly request the `POST_NOTIFICATIONS` permission. We will add this permission to the manifest and request it at runtime (either on application startup or when toggling the setting on).
> - **Launch Mode**: `MainActivity` will be updated to use `launchMode="singleTask"` in `AndroidManifest.xml` to prevent creating multiple instances of the app when the user clicks action buttons in the notification.

## Proposed Changes

### Component 1: Data Models & Persistence

#### [MODIFY] [ProfileRepository.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/data/profile/ProfileRepository.kt)
- Add `quickActionsEnabled: Boolean = true` to `UserProfile`.
- Update `FirestoreProfileRepository` parsing and saving logic to read and write the new field.

---

### Component 2: Notification Management & Receiver

#### [NEW] [QuickActionsNotificationManager.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/notification/QuickActionsNotificationManager.kt)
- Responsible for:
  - Creating the `"Quick Actions"` notification channel (importance `IMPORTANCE_LOW` to prevent sound/popup interruptions).
  - Building the ongoing notification (`setOngoing(true)`).
  - Subscribing to the water log flow (from `AppContainer.waterRepository.waterLog`) to display the current day's water intake dynamically.
  - Setting up `PendingIntent` actions:
    - **Add Meal**: launches `MainActivity` with `EXTRA_NAVIGATE_TO = "add_meal"`.
    - **Add Workout**: launches `MainActivity` with `EXTRA_NAVIGATE_TO = "add_workout"`.
    - **+250ml Water**: fires a Broadcast to `WaterLoggingReceiver`.

#### [NEW] [WaterLoggingReceiver.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/notification/WaterLoggingReceiver.kt)
- A `BroadcastReceiver` registered in the manifest.
- When it receives `ACTION_ADD_WATER`:
  - Increments water by 250 ml for the current date using `AppContainer.waterRepository.addWater(today, 250)`.
  - Shows a brief Toast confirming the action.
  - Dynamically updates the notification text with the new value.

---

### Component 3: User Interface & Routing

#### [MODIFY] [AndroidManifest.xml](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/AndroidManifest.xml)
- Add `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`.
- Declare `<receiver android:name=".notification.WaterLoggingReceiver" android:exported="false" />`.
- Update `<activity android:name=".MainActivity" android:launchMode="singleTask" />`.

#### [MODIFY] [MainActivity.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/MainActivity.kt)
- Manage a mutable `intentState` containing the active/incoming `Intent`.
- Override `onNewIntent` to capture new intents when the app is in the foreground.
- Request `POST_NOTIFICATIONS` permission on startup if `quickActionsEnabled` is true (and the permission has not been granted yet).
- Initialize/Start the notification if the user is authenticated and the preference is enabled.

#### [MODIFY] [Navigation.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/Navigation.kt)
- Accept `intent` and `onIntentHandled` in `MainNavigation`.
- Use a `LaunchedEffect(intent)` to check for `EXTRA_NAVIGATE_TO` and navigate to `AddMeal` or `AddWorkout` screens.

#### [MODIFY] [ProfileScreen.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt)
- Display a `Switch` row in the settings/profile screen to enable/disable the quick actions notification.
- Connect it to the state and saving flow.

#### [MODIFY] [ProfileViewModel.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileViewModel.kt)
- Expose and manage the `quickActionsEnabled` preference.

#### [MODIFY] [FoodScreen.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/ui/food/FoodScreen.kt)
- Update water increment to 250ml (from 300ml) in water drop calculations and quick add button callbacks.

---

## Verification Plan

### Automated Tests
- Run Gradle unit test suite: `./gradlew test`
- Add unit tests verifying `UserProfile` serialization and parsing of the new `quickActionsEnabled` field.

### Manual Verification
1. **Notification Creation**: Sign in, verify that the notification appears in the drawer.
2. **Settings Toggle**: Turn off the switch in the profile screen, verify the notification disappears. Turn it on, verify it reappears.
3. **Add Meal / Add Workout**: Tap the "הוספת ארוחה" or "הוספת אימון" button, verify the app opens directly to the correct sheet.
4. **+250ml Water Action**: Tap "+250 מ״ל", verify the app stays in the background, a Toast is shown, and the notification's text updates dynamically (e.g. from 0 to 250 ml).
