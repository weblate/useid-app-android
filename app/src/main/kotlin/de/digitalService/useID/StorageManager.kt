package de.digitalService.useID

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface StorageManagerType {
    fun getIsFirstTimeUser(): Boolean
    fun setIsFirstTimeUser()
}

@Singleton
class StorageManager @Inject constructor(@ApplicationContext context: Context) : StorageManagerType {
    private enum class StorageKeys {
        PREFS_KEY,
        FIRST_TIME_USER_KEY
    }

    private val sharedPrefs = context.getSharedPreferences(StorageKeys.PREFS_KEY.name, Context.MODE_PRIVATE)

    override fun getIsFirstTimeUser(): Boolean {
        return sharedPrefs.getBoolean(StorageKeys.FIRST_TIME_USER_KEY.name, true)
    }

    override fun setIsFirstTimeUser() {
        sharedPrefs.edit {
            putBoolean(StorageKeys.FIRST_TIME_USER_KEY.name, false)
        }
    }
}
