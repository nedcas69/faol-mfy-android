package uz.bekobod.faolmfy.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Brendga moslashgan avtoishga tushirish sozlamalari — TZ F2.5.
 *
 * Bu Android'ning eng og'riqli joyi: ruxsat berilgan bo'lsa ham OEM
 * "battery optimizer" ForegroundService ni o'ldiradi. Kod bilan hal qilib
 * bo'lmaydi — foydalanuvchi qo'lda yoqishi SHART. Ilova faqat uni
 * to'g'ri ekranga olib borishi mumkin.
 */
object OemHelper {

    data class OemGuide(
        val brandName: String,
        val steps: List<String>,
        val intents: List<Intent>,
    )

    private fun component(pkg: String, cls: String) =
        Intent().setComponent(ComponentName(pkg, cls))

    fun guideFor(manufacturer: String = DeviceInfo.manufacturer): OemGuide {
        return when (manufacturer.lowercase()) {

            "xiaomi", "redmi", "poco" -> OemGuide(
                brandName = "Xiaomi / Redmi (MIUI / HyperOS)",
                steps = listOf(
                    "Sozlamalar → Ilovalar → Ilovalarni boshqarish → Faol MFY",
                    "«Avtoishga tushirish» (Autostart) — YOQING",
                    "«Batareyani tejash» → «Cheklovsiz» (No restrictions) tanlang",
                    "So'nggi ilovalar oynasida Faol MFY ni pastga bosib turing → qulf belgisini bosing",
                ),
                intents = listOf(
                    component("com.miui.securitycenter",
                        "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    component("com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                ),
            )

            "honor" -> OemGuide(
                brandName = "Honor (MagicOS)",
                steps = listOf(
                    "Sozlamalar → Ilovalar → Faol MFY → Batareya",
                    "«Ishga tushirish» (Launch) → qo'lda boshqarish",
                    "Uchala kalitni YOQING: avtoishga tushirish, bilvosita ishga tushirish, fonda ishlash",
                    "Sozlamalar → Batareya → Ilovani ishga tushirish → Faol MFY ni qulflang",
                ),
                intents = listOf(
                    component("com.hihonor.systemmanager",
                        "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    component("com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                ),
            )

            "huawei" -> OemGuide(
                brandName = "Huawei (EMUI)",
                steps = listOf(
                    "Sozlamalar → Ilovalar → Faol MFY → Batareya",
                    "«Ilovani ishga tushirish» → qo'lda boshqarishga o'tkazing",
                    "Avtoishga tushirish, bilvosita ishga tushirish va fonda ishlashni yoqing",
                ),
                intents = listOf(
                    component("com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    component("com.huawei.systemmanager",
                        "com.huawei.systemmanager.optimize.process.ProtectActivity"),
                ),
            )

            "oppo", "realme" -> OemGuide(
                brandName = "Oppo / Realme (ColorOS)",
                steps = listOf(
                    "Sozlamalar → Batareya → Batareya optimizatsiyasi → Faol MFY → Optimizatsiya qilinmasin",
                    "Sozlamalar → Ilovalar → Faol MFY → Fonda ishlashga ruxsat bering",
                    "Telefon menejeri → Avtoishga tushirish → Faol MFY ni yoqing",
                ),
                intents = listOf(
                    component("com.coloros.safecenter",
                        "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                    component("com.oppo.safe",
                        "com.oppo.safe.permission.startup.StartupAppListActivity"),
                ),
            )

            "vivo", "iqoo" -> OemGuide(
                brandName = "Vivo (Funtouch / OriginOS)",
                steps = listOf(
                    "iManager → Ilovalar menejeri → Avtoishga tushirish → Faol MFY ni yoqing",
                    "Sozlamalar → Batareya → Yuqori fon quvvat sarfi → Faol MFY ga ruxsat bering",
                ),
                intents = listOf(
                    component("com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    component("com.iqoo.secure",
                        "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                ),
            )

            "samsung" -> OemGuide(
                brandName = "Samsung (One UI)",
                steps = listOf(
                    "Sozlamalar → Batareya → Fon foydalanish chegaralari",
                    "Faol MFY «Uxlatiladigan ilovalar» ro'yxatida BO'LMASLIGI kerak",
                    "Sozlamalar → Ilovalar → Faol MFY → Batareya → Cheklanmagan",
                ),
                intents = listOf(
                    component("com.samsung.android.lool",
                        "com.samsung.android.sm.ui.battery.BatteryActivity"),
                ),
            )

            "tecno", "infinix", "itel" -> OemGuide(
                brandName = "Tecno / Infinix (HiOS / XOS)",
                steps = listOf(
                    "Sozlamalar → Ilovalar → Faol MFY → Avtoishga tushirishga ruxsat",
                    "Telefon menejeri → Quvvat tejash → Faol MFY ni oq ro'yxatga qo'shing",
                ),
                intents = emptyList(),
            )

            else -> OemGuide(
                brandName = manufacturer.replaceFirstChar { it.uppercase() },
                steps = listOf(
                    "Sozlamalar → Ilovalar → Faol MFY → Batareya → Cheklanmagan",
                    "Agar «Avtoishga tushirish» sozlamasi bo'lsa — uni yoqing",
                ),
                intents = emptyList(),
            )
        }
    }

    /** Brend sozlamalar ekranini ochishga urinadi. Topilmasa — ilova sozlamalari. */
    fun openOemSettings(context: Context): Boolean {
        for (intent in guideFor().intents) {
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return true
            } catch (_: Exception) { /* keyingisini sinaymiz */ }
        }
        return openAppSettings(context)
    }

    fun openAppSettings(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) { false }

    @Suppress("BatteryLife")
    fun requestIgnoreBatteryOptimization(context: Context): Boolean = try {
        context.startActivity(
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        true
    } catch (e: Exception) {
        try {
            context.startActivity(
                Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            true
        } catch (e2: Exception) { false }
    }

    /** Bu brend agressiv "battery killer" ishlatadimi? */
    fun isAggressiveOem(manufacturer: String = DeviceInfo.manufacturer): Boolean =
        manufacturer.lowercase() in setOf(
            "xiaomi", "redmi", "poco", "honor", "huawei",
            "oppo", "realme", "vivo", "iqoo", "oneplus",
            "tecno", "infinix", "itel", "meizu", "asus"
        )
}
