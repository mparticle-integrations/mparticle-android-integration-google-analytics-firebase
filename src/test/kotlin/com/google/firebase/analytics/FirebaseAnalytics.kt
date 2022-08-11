package com.google.firebase.analytics

import java.util.LinkedList
import java.util.AbstractMap.SimpleEntry
import android.app.Activity
import android.content.Context
import android.os.Bundle

class FirebaseAnalytics {
    var loggedEvents: LinkedList<Map.Entry<String, Bundle>> = LinkedList()
    var currentScreenName: String? = null


    fun logEvent(key: String, bundle: Bundle) {
        loggedEvents.add(SimpleEntry(key, bundle))
    }

    fun setCurrentScreen(currentActivity: Activity?, screenName: String?, classOverride: String?) {
        currentScreenName = screenName
    }

    fun setUserProperty(key: String?, value: String?) {}
    fun getLoggedEvents(): List<Map.Entry<String, Bundle>> = loggedEvents

    fun clearLoggedEvents() {
        loggedEvents = LinkedList()
    }

    companion object {
        var firebaseInstanceId: String? = null
        var instance: FirebaseAnalytics? = null

        @JvmStatic
        fun getInstance(context: Context?): FirebaseAnalytics? {
            if (instance == null) {
                instance = FirebaseAnalytics()
            }
            return instance
        }

        /**
         * Access Methods
         */
        fun clearInstance() {
            instance = null
        }

        fun setFirebaseId(firebaseId: String?) {
            firebaseInstanceId = firebaseId
        }
    }
}