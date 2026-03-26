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

                        val featureValues = Feature.entries.associate { feature ->
                            feature to when (feature.valueType) {
                                FeatureValueType.STRING -> prefs.getString(feature.name, feature.defaultValue as? String)
                                FeatureValueType.BOOLEAN -> prefs.getBoolean(feature.name, feature.defaultValue as? Boolean ?: false)
                            }
                        }

                        val bundle = ImsModifier.buildBundle(
                            carrierName = if (subId == -1) null else featureValues[Feature.CARRIER_NAME] as? String,
                            imsUserAgent = if (subId == -1) null else featureValues[Feature.IMS_USER_AGENT] as? String,
                            enableVoLTE = featureValues[Feature.VOLTE] as? Boolean ?: false,
                            enableVoWiFi = featureValues[Feature.VOWIFI] as? Boolean ?: false,
                            enableVT = featureValues[Feature.VT] as? Boolean ?: false,
                            enableVoNR = featureValues[Feature.VONR] as? Boolean ?: false,
                            enableCrossSIM = featureValues[Feature.CROSS_SIM] as? Boolean ?: false,
                            enableUT = featureValues[Feature.UT] as? Boolean ?: false,
                            enable5GNR = featureValues[Feature.FIVE_G_NR] as? Boolean ?: false,
                            enable5GThreshold = featureValues[Feature.FIVE_G_THRESHOLDS] as? Boolean ?: false,
                            enableShow4GForLTE = featureValues[Feature.SHOW_4G_FOR_LTE] as? Boolean ?: false
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