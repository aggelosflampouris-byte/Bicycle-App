import os

filepath = "app/src/main/java/com/fitnessapp/tracker/ui/dashboard/DashboardViewModel.kt"
with open(filepath, "r") as f:
    content = f.read()

# Insert onEvent before setActivityType
on_event_code = """
    fun onEvent(event: DashboardUiEvent) {
        when (event) {
            is DashboardUiEvent.SetActivityType -> setActivityType(event.type)
            is DashboardUiEvent.DeleteSession -> deleteSession(event.sessionId)
            is DashboardUiEvent.RespondToChallenge -> respondToChallenge(event.challenge, event.accept)
            is DashboardUiEvent.CancelChallenge -> cancelChallenge(event.challenge)
            is DashboardUiEvent.GenerateTrainingPlan -> generateTrainingPlan(event.goalPrompt)
            is DashboardUiEvent.ToggleDailyPlanCompleted -> toggleDailyPlanCompleted(event.day)
            is DashboardUiEvent.SaveRoutine -> saveRoutine(event.interval, event.metric, event.targetValue, event.autoImprove)
            is DashboardUiEvent.DeleteRoutine -> deleteRoutine()
        }
    }
"""

content = content.replace("    fun setActivityType", on_event_code + "\n    private fun setActivityType")
content = content.replace("    fun cancelChallenge", "    private fun cancelChallenge")
content = content.replace("    fun saveRoutine", "    private fun saveRoutine")
content = content.replace("    fun deleteRoutine", "    private fun deleteRoutine")
content = content.replace("    fun deleteSession", "    private fun deleteSession")
content = content.replace("    fun respondToChallenge", "    private fun respondToChallenge")
content = content.replace("    fun generateTrainingPlan", "    private fun generateTrainingPlan")
content = content.replace("    fun toggleDailyPlanCompleted", "    private fun toggleDailyPlanCompleted")


with open(filepath, "w") as f:
    f.write(content)
