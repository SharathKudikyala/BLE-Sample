package com.app.blesample

import android.content.Context

object UniqueIdProvider {
    private const val PREF_NAME = "ble_device_prefs"
    private const val KEY_UNIQUE_ID = "unique_ble_device_id"

    fun getOrCreateId(context: Context): String {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        var id = prefs.getString(KEY_UNIQUE_ID, null)

        if (id == null) {
            id = getRandomString()//UUID.randomUUID().toString()
            prefs.edit().putString(KEY_UNIQUE_ID, id).apply()
        }

        return id
    }

    private fun getRandomString(): String {
        val letters = ('A'..'Z').shuffled().take(3).joinToString("")
        val numbers = (0..9).shuffled().take(3).joinToString("")
        return letters.plus(numbers).toList().shuffled().joinToString("")
    }

}