package com.laterbox.app.ui.navigation

sealed class LaterboxDestination(val route: String) {
    object Inbox : LaterboxDestination("inbox")
    object Collections : LaterboxDestination("collections")
    object Downloads : LaterboxDestination("downloads")
    object Archive : LaterboxDestination("archive")
    object Settings : LaterboxDestination("settings")
    object SettingsAppearanceProfile : LaterboxDestination("settings/appearance_profile")
    object SettingsRemindersAi : LaterboxDestination("settings/reminders_ai")
    object SettingsCaptureShortcuts : LaterboxDestination("settings/capture_shortcuts")
    object SettingsReadingBehavior : LaterboxDestination("settings/reading_behavior")
    object SettingsMediaDownloads : LaterboxDestination("settings/media_downloads")
    object SettingsDataStorage : LaterboxDestination("settings/data_storage")
    object SettingsAboutPrivacy : LaterboxDestination("settings/about_privacy")
    object NotificationHistory : LaterboxDestination("notification_history")

    object CollectionDetail : LaterboxDestination("collection/{collectionId}/{collectionName}") {
        fun createRoute(collectionId: Long, collectionName: String): String {
            val encodedName = java.net.URLEncoder.encode(collectionName, "UTF-8")
            return "collection/$collectionId/$encodedName"
        }
    }
}
