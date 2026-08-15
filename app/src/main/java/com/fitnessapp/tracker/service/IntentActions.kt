package com.fitnessapp.tracker.service

/**
 * Centralised intent action string constants used across notification intents,
 * broadcast receivers, and activity intent handling.
 *
 * Using the application package prefix prevents collisions with other apps
 * and makes the intent origin unambiguous.
 */
object IntentActions {
    const val ACCEPT_CHALLENGE   = "com.fitnessapp.tracker.ACTION_ACCEPT_CHALLENGE"
    const val DENY_CHALLENGE     = "com.fitnessapp.tracker.ACTION_DENY_CHALLENGE"
    const val CANCEL_CHALLENGE   = "com.fitnessapp.tracker.ACTION_CANCEL_CHALLENGE"
    const val VIEW_CHALLENGES    = "com.fitnessapp.tracker.ACTION_VIEW_CHALLENGES"
    const val RECOMMENDED_WORKOUT = "com.fitnessapp.tracker.ACTION_RECOMMENDED_WORKOUT"
}
