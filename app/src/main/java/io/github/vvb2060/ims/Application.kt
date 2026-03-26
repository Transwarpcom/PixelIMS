package io.github.vvb2060.ims

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.FeatureValueType
import io.github.vvb2060.ims.privileged.ImsModifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import java.io.File

class Application : Application() {

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        tryApplyConfigs(this)
    }

    override fun onCreate() {
        super.onCreate()
        Shizuku.addBinderReceivedListener(binderReceivedListener)
    }

    override fun onTerminate() {
        super.onTerminate()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        LogcatRepository.stopAndClear()
    }

    companion object {
        fun tryApplyConfigs(context: Context) {
            val bootPrefs = context.getSharedPreferences("boot_prefs", Context.MODE_PRIVATE)
            if (!bootPrefs.getBoolean("needs_apply", false)) {
                return
            }

            if (!Shizuku.pingBinder()) {
                Log.d("Application", "Shizuku not running, skip applying configs")
                return
            }

            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                Log.d("Application", "Shizuku permission not granted, skip applying configs")
                return
            }

            Log.d("Application", "Applying configs from boot")

            CoroutineScope(Dispatchers.IO).launch {
                val dataDir = context.applicationInfo.dataDir
                val sharedPrefsDir = File(dataDir, "shared_prefs")
                if (!sharedPrefsDir.exists() || !sharedPrefsDir.isDirectory) {
                    bootPrefs.edit().putBoolean("needs_apply", false).apply()
                    return@launch
                }

                val simConfigPrefsFiles = sharedPrefsDir.listFiles { _, name ->
                    name.startsWith("sim_config_") && name.endsWith(".xml")
                }

                if (simConfigPrefsFiles != null) {
                    for (file in simConfigPrefsFiles) {
                        val prefName = file.nameWithoutExtension
                        val subIdStr = prefName.substringAfter("sim_config_")
                        val subId = subIdStr.toIntOrNull() ?: continue

                        val prefs = context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
                        if (prefs.all.isEmpty()) continue

                        val carrierName = if (subId == -1) null else prefs.getString(Feature.CARRIER_NAME.name, Feature.CARRIER_NAME.defaultValue as String)
                        val imsUserAgent = if (subId == -1) null else prefs.getString(Feature.IMS_USER_AGENT.name, Feature.IMS_USER_AGENT.defaultValue as String)
                        val enableVoLTE = prefs.getBoolean(Feature.VOLTE.name, Feature.VOLTE.defaultValue as Boolean)
                        val enableVoWiFi = prefs.getBoolean(Feature.VOWIFI.name, Feature.VOWIFI.defaultValue as Boolean)
                        val enableVT = prefs.getBoolean(Feature.VT.name, Feature.VT.defaultValue as Boolean)
                        val enableVoNR = prefs.getBoolean(Feature.VONR.name, Feature.VONR.defaultValue as Boolean)
                        val enableCrossSIM = prefs.getBoolean(Feature.CROSS_SIM.name, Feature.CROSS_SIM.defaultValue as Boolean)
                        val enableUT = prefs.getBoolean(Feature.UT.name, Feature.UT.defaultValue as Boolean)
                        val enable5GNR = prefs.getBoolean(Feature.FIVE_G_NR.name, Feature.FIVE_G_NR.defaultValue as Boolean)
                        val enable5GThreshold = prefs.getBoolean(Feature.FIVE_G_THRESHOLDS.name, Feature.FIVE_G_THRESHOLDS.defaultValue as Boolean)
                        val enableShow4GForLTE = prefs.getBoolean(Feature.SHOW_4G_FOR_LTE.name, Feature.SHOW_4G_FOR_LTE.defaultValue as Boolean)

                        val bundle = ImsModifier.buildBundle(
                            carrierName,
                            imsUserAgent,
                            enableVoLTE,
                            enableVoWiFi,
                            enableVT,
                            enableVoNR,
                            enableCrossSIM,
                            enableUT,
                            enable5GNR,
                            enable5GThreshold,
                            enableShow4GForLTE
                        )
                        bundle.putInt(ImsModifier.BUNDLE_SELECT_SIM_ID, subId)

                        ShizukuProvider.overrideImsConfig(context, bundle)
                    }
                }

                bootPrefs.edit().putBoolean("needs_apply", false).apply()
                Log.d("Application", "Configs applied and boot_prefs cleared")
            }
        }
    }
}