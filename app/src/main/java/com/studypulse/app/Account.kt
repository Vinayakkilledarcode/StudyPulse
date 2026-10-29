package com.studypulse.app

import android.content.Context

/** The signed in person. Every account has its own plan, progress and settings on this phone. */
object Account {
    private fun p(c: Context) = c.applicationContext.getSharedPreferences("studypulse_account", Context.MODE_PRIVATE)

    fun uid(c: Context): String = p(c).getString("uid", "") ?: ""
    fun signedIn(c: Context): Boolean = uid(c).isNotEmpty()
    fun name(c: Context): String = p(c).getString("name", "") ?: ""
    fun email(c: Context): String = p(c).getString("email", "") ?: ""
    fun phone(c: Context): String = p(c).getString("phone", "") ?: ""
    fun photo(c: Context): String = p(c).getString("photo", "") ?: ""
    fun method(c: Context): String = p(c).getString("method", "") ?: ""
    fun firstName(c: Context): String = name(c).trim().substringBefore(' ')

    /** A file-safe key for the current account. */
    fun key(c: Context): String {
        val u = uid(c)
        if (u.isEmpty()) return "anon"
        return u.filter { it.isLetterOrDigit() || it == '_' }.take(40).ifEmpty { "user" }
    }

    fun prefsName(c: Context): String = "studypulse_u_" + key(c)

    fun set(c: Context, newUid: String, newName: String, newEmail: String, newPhone: String, newPhoto: String, newMethod: String) {
        // a name the person typed in the profile wins over the one from the sign-in provider
        val keepName = if (newUid == uid(c) && name(c).isNotEmpty()) name(c) else newName
        p(c).edit().putString("uid", newUid).putString("name", keepName).putString("email", newEmail)
            .putString("phone", newPhone).putString("photo", newPhoto).putString("method", newMethod).apply()
    }

    fun setName(c: Context, n: String) { p(c).edit().putString("name", n.trim()).apply() }

    fun clear(c: Context) {
        p(c).edit().remove("uid").remove("name").remove("email").remove("phone").remove("photo").remove("method").apply()
    }

    /**
     * Data saved by the first version (one shared space) is handed to the first account that signs in,
     * together with the sample plan it was using. Runs only once.
     */
    fun adoptLegacy(c: Context) {
        val ap = p(c)
        if (ap.getBoolean("legacyDone", false)) return
        ap.edit().putBoolean("legacyDone", true).apply()
        val old = c.getSharedPreferences("studypulse", Context.MODE_PRIVATE)
        if (old.all.isEmpty()) return
        val dst = c.getSharedPreferences(prefsName(c), Context.MODE_PRIVATE)
        if (dst.all.isNotEmpty()) return
        val e = dst.edit()
        for ((k, v) in old.all) when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is String -> e.putString(k, v)
        }
        if (!old.contains("off")) e.putInt("off", 1)
        e.apply()
        Plan.installSample(c)
    }

    /** Progress made in the old no-sign-in mode moves to the first real account, once. */
    fun adoptGuest(c: Context) {
        val old = c.getSharedPreferences("studypulse_u_guest", Context.MODE_PRIVATE)
        if (old.all.isEmpty() || key(c) == "guest") return
        val dst = c.getSharedPreferences(prefsName(c), Context.MODE_PRIVATE)
        if (dst.all.isNotEmpty()) return
        val e = dst.edit()
        for ((k, v) in old.all) when (v) {
            is Boolean -> e.putBoolean(k, v)
            is Int -> e.putInt(k, v)
            is Long -> e.putLong(k, v)
            is Float -> e.putFloat(k, v)
            is String -> e.putString(k, v)
        }
        e.apply()
        val dir = c.applicationContext.filesDir
        val src = java.io.File(dir, "plan_guest.json")
        if (src.exists()) src.renameTo(java.io.File(dir, "plan_" + key(c) + ".json"))
        old.edit().clear().apply()
    }
}
