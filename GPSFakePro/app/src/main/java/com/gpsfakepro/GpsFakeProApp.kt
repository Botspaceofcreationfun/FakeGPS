package com.gpsfakepro

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.gpsfakepro.data.AppContainer
import com.gpsfakepro.data.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.osmdroid.config.Configuration

class GpsFakeProApp : Application() {

    override fun onCreate() {
        super.onCreate()

        AppContainer.init(this)

        // Identify this app to the OpenStreetMap tile servers, as their usage
        // policy requires. Tiles are cached locally; we do not bulk-download.
        Configuration.getInstance().userAgentValue =
            "GPSFakePro/${BuildConfig.VERSION_NAME} (Android; osmdroid; personal testing)"

        // Apply the persisted language choice before any activity is created.
        runBlocking {
            val defaults = SettingsRepository.observeDefaults(this@GpsFakeProApp).first()
            val lang = defaults.language
            if (lang != "system") {
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(lang)
                )
            }
        }
    }
}
