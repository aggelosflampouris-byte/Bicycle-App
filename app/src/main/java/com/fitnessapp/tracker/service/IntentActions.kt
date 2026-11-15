package com.fitnessapp.tracker.service

/**
 * Centralised intent action string constants used across notification intents,
 * broadcast receivers, and activity intent handling.
 *
 * Using the application package prefix prevents collisions with other apps
 * and makes the intent origin unambiguous.
 */
object IntentActions {
    const val RECOMMENDED_WORKOUT = "com.fitnessapp.tracker.ACTION_RECOMMENDED_WORKOUT"
}
