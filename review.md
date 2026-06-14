Great work on Phase 3! The architecture is solid, and the separation of concerns (parsing, writing, prompting) on the backend is very clean. The `firestore.rules` changes are exactly the right way to enforce read-only access.

Here are a few observations and recommendations:

### 1. Scalability of `runForAllUsers` (Backend)
In `functions/src/generateInsights.ts`, the scheduled functions fetch all users via `collection("users").get()` and iterate through them sequentially.
While this works perfectly for an MVP, each Gemini call takes 1-2 seconds. With a 540-second timeout, this function will start timing out once you reach around 300-400 active users.
**Recommendation:** Consider adding a TODO to move to a fan-out architecture in the future (e.g., the scheduled function publishes a Pub/Sub message or enqueues a Cloud Task per user/batch to run `runInsightsForUser` in parallel).

### 2. Silent Error Handling on Manual Refresh (Client)
In `DashboardViewModel.kt` (and `FoodViewModel`, `ActivityViewModel`), the manual refresh catches the `InsightsRefreshException`:
```kotlin
} catch (_: InsightsRefreshException) {
    // Friendly failure: keep the last known insight; user can retry.
}
```
The `FunctionsInsightsRefresher` does a great job mapping Firebase errors to user-friendly Hebrew messages (e.g., "שירות ה-AI עמוס כרגע..."), but since the exception is silently ignored, the user never sees these messages.
**Recommendation:** Expose an `error: String?` in your state classes and show a Snackbar or Toast in the UI when a refresh fails, so the user knows *why* it failed.

### 3. Excellent Rule Structure
Restructuring `firestore.rules` to enumerate writable subcollections instead of using the `match /{document=**}` wildcard was exactly the right approach to genuinely protect the `insights/` collection from client writes.

### 4. Robust AI Parsing
The fail-closed mechanism in `runInsightsForUser` paired with `insightsParse.ts` is well executed. Not writing anything on a parse error or Vertex timeout ensures you never overwrite a valid existing insight with a partial/corrupted one.

Overall, a very well-tested and robust PR! I'm approving this.
