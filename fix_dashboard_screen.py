import os

filepath = "app/src/main/java/com/fitnessapp/tracker/ui/dashboard/DashboardScreen.kt"
with open(filepath, "r") as f:
    content = f.read()

content = content.replace("viewModel.deleteSession(sessionToDelete!!)", "viewModel.onEvent(DashboardUiEvent.DeleteSession(sessionToDelete!!))")
content = content.replace("viewModel.setActivityType(activityType)", "viewModel.onEvent(DashboardUiEvent.SetActivityType(activityType))")
content = content.replace("viewModel.setActivityType(newActivity)", "viewModel.onEvent(DashboardUiEvent.SetActivityType(newActivity))")
content = content.replace("viewModel.respondToChallenge(challenge, true)", "viewModel.onEvent(DashboardUiEvent.RespondToChallenge(challenge, true))")
content = content.replace("viewModel.respondToChallenge(challenge, false)", "viewModel.onEvent(DashboardUiEvent.RespondToChallenge(challenge, false))")
content = content.replace("viewModel.cancelChallenge(challenge)", "viewModel.onEvent(DashboardUiEvent.CancelChallenge(challenge))")
content = content.replace("viewModel.generateTrainingPlan(goal)", "viewModel.onEvent(DashboardUiEvent.GenerateTrainingPlan(goal))")
content = content.replace("viewModel.toggleDailyPlanCompleted(day)", "viewModel.onEvent(DashboardUiEvent.ToggleDailyPlanCompleted(day))")
content = content.replace("viewModel.saveRoutine(interval, metric, target, autoImprove)", "viewModel.onEvent(DashboardUiEvent.SaveRoutine(interval, metric, target, autoImprove))")
content = content.replace("viewModel.deleteRoutine()", "viewModel.onEvent(DashboardUiEvent.DeleteRoutine)")

with open(filepath, "w") as f:
    f.write(content)
