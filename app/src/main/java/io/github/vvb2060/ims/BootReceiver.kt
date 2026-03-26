package io.github.vvb2060.ims

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_LOCKED_BOOT_COMPLETED) {
            Log.d("BootReceiver", "Boot completed, marking needs_apply")
            val prefs = context.getSharedPreferences("boot_prefs", Context.MODE_PRIVATE)
            prefs.edit().putBoolean("needs_apply", true).apply()

            // Just in case Shizuku is already connected at this exact point
            Application.tryApplyConfigs(context)
        }
    }
}
