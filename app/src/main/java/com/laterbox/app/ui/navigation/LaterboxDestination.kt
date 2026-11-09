package com.laterbox.app.ui.navigation

sealed class LaterboxDestination(val route: String) {
    object Inbox : LaterboxDestination("inbox")
    object Collections : LaterboxDestination("collections")
    object Archive : LaterboxDestination("archive")
    object Settings : LaterboxDestination("settings")
    object NotificationHistory : LaterboxDestination("notification_history")

    object CollectionDetail : LaterboxDestination("collection/{collectionId}/{collectionName}") {
        fun createRoute(collectionId: Long, collectionName: String): String {
            val encodedName = java.net.URLEncoder.encode(collectionName, "UTF-8")
            return "collection/$collectionId/$encodedName"
        }
    }
}
