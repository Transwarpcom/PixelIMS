package io.github.vvb2060.ims.viewmodel

import android.app.Application
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.application
import androidx.lifecycle.viewModelScope
import io.github.vvb2060.ims.BuildConfig
import io.github.vvb2060.ims.LogcatRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class LogcatViewModel(application: Application) : AndroidViewModel(application) {
    val logs = LogcatRepository.logs

    init {
        LogcatRepository.startLogcat()
    }

    fun clearLogs() {
        LogcatRepository.clearLogs()
    }

    fun exportLogFile() {
        viewModelScope.launch(Dispatchers.IO) {
            File(application.externalCacheDir, "turbo_ims.log").apply {
                writeText("App Version: ${BuildConfig.VERSION_NAME}\n")
                appendText("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                appendText("Android Version: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
                appendText("System Build Version: ${Build.DISPLAY}\n")
                appendText("Security Patch Version: ${Build.VERSION.SECURITY_PATCH}\n")
                appendText("-----------------------------------------------------------------")
                appendText("PixelIMS Logcat:\n")
                logs.map { it.raw }.forEach {
                    appendText(it + "\n")
                }

                val authority = "${application.packageName}.logcat_fileprovider"
                val uri = FileProvider.getUriForFile(application, authority, this)
                application.startActivity(
                    Intent.createChooser(
                        Intent(Intent.ACTION_SEND)
                            .setType("text/plain")
                            .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .putExtra(Intent.EXTRA_STREAM, uri),
                        "Export Logcat"
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }
}