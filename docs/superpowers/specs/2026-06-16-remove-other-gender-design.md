# Design: Remove OTHER Gender Option

This document outlines the design for removing the "Other" (`other`/`אחר`) gender option from all parts of the MyHealthTracker application.

## 1. Goal

The objective is to restrict gender selection within the application to only "Male" (`male`/`זכר`) and "Female" (`female`/`נקבה`). This will apply to:
- The UI (removing the "Other" option from the profile editing screen).
- The validation logic (in both production and fake repository data layers).
- The unit and integration test suites.

No database migration is required for existing users who might have their gender set to `other`. However, they will be forced to select either male or female if they attempt to update their profile in the future.

## 2. Proposed Changes

### UI Layer

#### [MODIFY] [ProfileScreen.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileScreen.kt)
- Modify the `genders` list in `ProfileScreenContent` to remove `"אחר"`.
- Old: `val genders = listOf("זכר", "נקבה", "אחר")`
- New: `val genders = listOf("זכר", "נקבה")`

#### [MODIFY] [ProfileViewModel.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/ui/profile/ProfileViewModel.kt)
- Remove the `"אחר" -> "other"` mapping case from the `saveProfile` method since `"אחר"` is no longer a selectable option in the UI.

### Data Layer

#### [MODIFY] [ProfileRepository.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/main/java/com/myhealthtracker/app/data/profile/ProfileRepository.kt)
- Remove the `"other" -> "אחר"` mapping case from `genderToHebrew`.
- Modify `validateProfile` to check `profile.gender != "male" && profile.gender != "female"`. If so, return a failure with the message `"Gender must be 'male' or 'female'"`.

### Test Layer

#### [MODIFY] [FakeRepository.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/test/java/com/myhealthtracker/app/data/FakeRepository.kt)
- Align the validation logic in `validateProfile` to check only for `"male"` and `"female"`, matching `ProfileRepository.kt`.

#### [MODIFY] [ProfileAndHealthUnitTest.kt](file:///d:/AICode/my-health-tracker/my-health-tracker/app/src/test/java/com/myhealthtracker/app/ProfileAndHealthUnitTest.kt)
- Update `testProfileValidation_gender` to assert that `gender = "other"` results in validation failure:
  - Old: `assertTrue(profileRepository.validateProfile(UserProfile(1995, 70.0, 175.0, gender = "other")).isSuccess)`
  - New: `assertTrue(profileRepository.validateProfile(UserProfile(1995, 70.0, 175.0, gender = "other")).isFailure)`

#### [MODIFY] [aggregate.test.ts](file:///d:/AICode/my-health-tracker/my-health-tracker/functions/test/aggregate.test.ts)
- Update the mock user profile in the `tolerates malformed/missing fields without throwing` test case to use `gender: "female"` (or `"male"`) instead of `"other"`.

## 3. Verification Plan

### Automated Tests
- Run Android unit tests to verify the updated validation logic:
  - `ProfileAndHealthUnitTest.kt`
- Run Cloud Functions unit tests to verify aggregation and general tests:
  - `aggregate.test.ts`

### Manual Verification
- Launch the application and open the profile editing screen.
- Verify that only two options are available under "Gender": "זכר" (Male) and "נקבה" (Female).
- Try saving a profile with both options and confirm success.
