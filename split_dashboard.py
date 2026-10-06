import os

source_file = "app/src/main/java/com/fitnessapp/tracker/ui/dashboard/DashboardScreen.kt"
target_dir = "app/src/main/java/com/fitnessapp/tracker/ui/dashboard/components"

with open(source_file, "r") as f:
    lines = f.readlines()

# Extract imports and package declaration
header = []
for line in lines[:45]:
    if line.startswith("package"):
        header.append("package com.fitnessapp.tracker.ui.dashboard.components\n")
    else:
        header.append(line)

# Add imports for components back to the main file
main_imports = "\nimport com.fitnessapp.tracker.ui.dashboard.components.*\n"
header.append(main_imports) # Actually, the original file needs this, not the components.

component_imports = header.copy()
component_imports[0] = "package com.fitnessapp.tracker.ui.dashboard.components\n"
component_imports.insert(1, "import com.fitnessapp.tracker.ui.dashboard.*\n")

# Find line 245 where the components start
split_index = 244

main_file_content = lines[:split_index]
# Insert the component imports into main_file_content
for i, line in enumerate(main_file_content):
    if line.startswith("import "):
        main_file_content.insert(i, "import com.fitnessapp.tracker.ui.dashboard.components.*\n")
        break

components_content = lines[split_index:]

def create_component(filename, start_marker, end_marker=None):
    pass # Too complex to do by markers if there are multiple. We'll just split by @Composable manually in the loop.

current_component = []
current_file = None

components = {
    "DashboardHeader": "DashboardHeader.kt",
    "StatsRow": "StatsGrid.kt",
    "StartWorkoutButton": "StartWorkoutButton.kt",
    "SessionCard": "SessionCard.kt",
    "RoutineConfigBottomSheet": "RoutineConfigBottomSheet.kt",
    "RoutineProgressCard": "RoutineProgressCard.kt",
    "ChallengeCard": "ChallengeCard.kt",
    "WeeklyTrainingPlanCard": "WeeklyTrainingPlanCard.kt",
    "TrophiesAndRecordsCard": "TrophiesAndRecordsCard.kt"
}

file_contents = {v: component_imports.copy() for v in components.values()}
current_file = None

for line in components_content:
    if line.startswith("@Composable"):
        # We need to peek ahead to find the function name to determine which file it belongs to
        pass

# Actually, it's easier to just use regex to find the function names.
import re

current_file = None
buffer = []

for i in range(len(components_content)):
    line = components_content[i]
    if line.startswith("@Composable"):
        # Look at the next line for the function name
        next_line = components_content[i+1]
        match = re.search(r'fun\s+([A-Za-z0-9_]+)\s*\(', next_line)
        if match:
            func_name = match.group(1)
            # Map function to file
            if func_name == "DashboardHeader": current_file = "DashboardHeader.kt"
            elif func_name in ["StatsRow", "StatCard"]: current_file = "StatsGrid.kt"
            elif func_name == "StartWorkoutButton": current_file = "StartWorkoutButton.kt"
            elif func_name in ["SessionCard", "MiniStat"]: current_file = "SessionCard.kt"
            elif func_name == "RoutineConfigBottomSheet": current_file = "RoutineConfigBottomSheet.kt"
            elif func_name == "RoutineProgressCard": current_file = "RoutineProgressCard.kt"
            elif func_name == "ChallengeCard": current_file = "ChallengeCard.kt"
            elif func_name in ["WeeklyTrainingPlanCard", "TrainingPlanGoalDialog"]: current_file = "WeeklyTrainingPlanCard.kt"
            elif func_name == "TrophiesAndRecordsCard": current_file = "TrophiesAndRecordsCard.kt"
    
    if current_file:
        file_contents[current_file].append(line)

# Write all files
for filename, content in file_contents.items():
    with open(os.path.join(target_dir, filename), "w") as f:
        f.writelines(content)

# Update main file
with open(source_file, "w") as f:
    f.writelines(main_file_content)

print("Split completed successfully!")
