package uz.bekobod.faolmfy.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import uz.bekobod.faolmfy.util.DeviceInfo
import uz.bekobod.faolmfy.util.OemHelper

private fun granted(context: Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

@Composable
private fun WizardStep(
    number: Int,
    title: String,
    description: String,
    done: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (done) Color(0xFFE8F5E9) else Color(0xFFFAFAFA)
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (done) Icons.Default.CheckCircle
                                  else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (done) Color(0xFF2E7D32) else Color.Gray,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.size(10.dp))
                Text("$number. $title", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            }
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.DarkGray)
            if (!done) {
                Button(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun SetupWizardScreen(
    onReportPerms: (Boolean, Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val guide = remember { OemHelper.guideFor() }

    var fine by remember { mutableStateOf(granted(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var background by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                granted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        )
    }
    var notifications by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                granted(context, Manifest.permission.POST_NOTIFICATIONS)
        )
    }
    var activity by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                granted(context, Manifest.permission.ACTIVITY_RECOGNITION)
        )
    }
    var battery by remember { mutableStateOf(DeviceInfo.isBatteryUnrestricted(context)) }
    var autostart by remember { mutableStateOf(false) }

    val fineLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { fine = granted(context, Manifest.permission.ACCESS_FINE_LOCATION) }

    val bgLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { background = it }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notifications = it }

    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { activity = it }

    // Ekranga qaytilganda holatni qayta tekshiramiz (sozlamalardan qaytish)
    LaunchedEffect(Unit) {
        battery = DeviceInfo.isBatteryUnrestricted(context)
    }

    val allDone = fine && background && notifications && battery && autostart

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        Text("Sozlash", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Text(
            "Ilova fonda ishlashi uchun quyidagi sozlamalar shart. " +
            "Ularsiz kuzatuv to'xtab qoladi va ballaringiz yo'qoladi.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )

        WizardStep(
            number = 1,
            title = "Joylashuvga ruxsat",
            description = "Ilova mahalladagi harakatingizni yozib borishi uchun kerak.",
            done = fine,
            actionLabel = "Ruxsat berish",
        ) {
            fineLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                )
            )
        }

        WizardStep(
            number = 2,
            title = "«Har doim ruxsat berish»",
            description = "Ekran o'chganda ham ishlashi uchun. Ochilgan oynada " +
                          "«Har doim ruxsat berish» (Allow all the time) ni tanlang.",
            done = background,
            actionLabel = "Fon ruxsatini berish",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else background = true
        }

        WizardStep(
            number = 3,
            title = "Bildirishnomalar",
            description = "Kuzatuv holati va ogohlantirishlarni ko'rsatish uchun.",
            done = notifications,
            actionLabel = "Ruxsat berish",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else notifications = true
        }

        WizardStep(
            number = 4,
            title = "Qadam sanagich",
            description = "Yurgan masofangizni tekshirish uchun. Ixtiyoriy, lekin " +
                          "tavsiya etiladi.",
            done = activity,
            actionLabel = "Ruxsat berish",
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            } else activity = true
        }

        WizardStep(
            number = 5,
            title = "Batareya cheklovini olib tashlash",
            description = "Ochilgan oynada «Ruxsat berish» (Allow) ni bosing. " +
                          "Bu ilovaning fonda o'chib qolishini kamaytiradi.",
            done = battery,
            actionLabel = "Sozlash",
        ) {
            OemHelper.requestIgnoreBatteryOptimization(context)
        }

        // 6-qadam — eng muhimi, aynan shu brendlar uchun
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (autostart) Color(0xFFE8F5E9) else Color(0xFFFFF8E1)
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (autostart) Icons.Default.CheckCircle
                                      else Icons.Default.RadioButtonUnchecked,
                        contentDescription = null,
                        tint = if (autostart) Color(0xFF2E7D32) else Color(0xFFF57C00),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.size(10.dp))
                    Text("6. Avtoishga tushirish", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                }
                Text(
                    guide.brandName,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE65100),
                )
                Text(
                    "Telefoningiz ilovalarni fonda o'ldiradi. Buni ilova ichidan " +
                    "hal qilib bo'lmaydi — quyidagi qadamlarni QO'LDA bajaring:",
                    style = MaterialTheme.typography.bodySmall,
                )
                guide.steps.forEachIndexed { i, step ->
                    Text("${i + 1}) $step", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = { OemHelper.openOemSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sozlamalarni ochish") }

                if (!autostart) {
                    Button(
                        onClick = { autostart = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Bajardim") }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                onReportPerms(fine, background, notifications, activity, battery, autostart)
                onFinish()
            },
            enabled = allDone,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            Text(
                if (allDone) "Kuzatuvni boshlash" else "Barcha qadamlarni bajaring",
                fontSize = 16.sp,
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}
