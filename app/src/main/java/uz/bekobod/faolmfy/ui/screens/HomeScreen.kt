package uz.bekobod.faolmfy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.bekobod.faolmfy.data.Profile
import uz.bekobod.faolmfy.data.remote.DayResponse

private fun statusColor(status: String): Color = when (status) {
    "green" -> Color(0xFF2E7D32)
    "yellow" -> Color(0xFFF9A825)
    "orange" -> Color(0xFFEF6C00)
    "red" -> Color(0xFFC62828)
    else -> Color.Gray
}

private fun statusLabel(status: String): String = when (status) {
    "green" -> "Ma'lumot to'liq"
    "yellow" -> "Qisman uzilish"
    "orange" -> "Kam ma'lumot"
    "red" -> "Ma'lumot yo'q"
    "nonworking" -> "Dam olish kuni"
    else -> "Noma'lum"
}

private fun eventLabel(type: String): String = when (type) {
    "tracking_gap" -> "Kuzatuv uzilgan"
    "mock_location" -> "Soxta joylashuv aniqlangan"
    "impossible_jump" -> "Imkonsiz sakrash"
    "step_mismatch" -> "Qadam soni masofaga mos emas"
    "device_stationary_all_day" -> "Qurilma kun bo'yi harakatsiz"
    "battery_critical" -> "Batareya kritik darajaga tushgan"
    "service_killed" -> "Ilova tizim tomonidan to'xtatilgan"
    "photo_location_mismatch" -> "Rasm joylashuvi mos kelmadi"
    else -> type
}

private fun hm(seconds: Int): String = "${seconds / 3600}s ${seconds % 3600 / 60}d"

@Composable
private fun ScoreRow(name: String, value: Double, max: Double) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            Text("${"%.1f".format(value)} / ${max.toInt()}",
                 style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        LinearProgressIndicator(
            progress = { if (max > 0) (value / max).toFloat().coerceIn(0f, 1f) else 0f },
            modifier = Modifier.fillMaxWidth().height(6.dp),
        )
    }
}

@Composable
fun HomeScreen(
    profile: Profile,
    day: DayResponse?,
    pendingPoints: Int,
    pendingAttachments: Int,
    photosByStop: Map<String, List<LocalPhoto>>,
    notesByAnchor: Map<String, String>,
    trackingActive: Boolean,
    isWorkday: Boolean,
    loading: Boolean,
    onRefresh: () -> Unit,
    onSync: () -> Unit,
    onPhotoTaken: (java.io.File, String) -> Unit,
    onNoteSaved: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(6.dp))

        // --- profil ---
        Column {
            Text(profile.fio.ifBlank { "Xodim" }, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(
                listOfNotNull(
                    profile.jobName.takeIf { it.isNotBlank() },
                    profile.orgName.takeIf { it.isNotBlank() },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray,
            )
        }

        // --- kuzatuv holati ---
        Card(
            colors = CardDefaults.cardColors(
                containerColor = if (trackingActive) Color(0xFFE8F5E9) else Color(0xFFF5F5F5)
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (trackingActive) Color(0xFF2E7D32) else Color.Gray,
                    modifier = Modifier.size(12.dp),
                ) {}
                Spacer(Modifier.size(10.dp))
                Column {
                    Text(
                        if (trackingActive) "KUZATUV YOQILGAN" else "KUZATUV O'CHIQ",
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            !isWorkday -> "Bugun dam olish / bayram kuni"
                            trackingActive -> "09:00–18:00 · 18:00 da avtomatik o'chadi"
                            else -> "Ish vaqtidan tashqarida"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }
        }

        // --- sinxron holati ---
        if (pendingAttachments > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Yuborilmagan rasm/izoh: $pendingAttachments",
                         fontWeight = FontWeight.Medium)
                    Text(
                        "Telefonda saqlangan. Internet paydo bo'lganda o'zi yuboriladi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }
        }

        if (pendingPoints > 0) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("Yuborilmagan: $pendingPoints ta yozuv", fontWeight = FontWeight.Medium)
                    Text(
                        "Internet paydo bo'lganda avtomatik yuboriladi. " +
                        "Bu ballingizga ta'sir qilmaydi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                    )
                }
            }
        }

        // --- bugungi ball ---
        if (day != null && day.kind == "workday") {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Bugungi ball", style = MaterialTheme.typography.bodySmall,
                                 color = Color.Gray)
                            Text("${"%.1f".format(day.total)}", fontSize = 38.sp,
                                 fontWeight = FontWeight.Bold)
                        }
                        Surface(
                            color = statusColor(day.dayStatus).copy(alpha = 0.15f),
                            shape = MaterialTheme.shapes.small,
                        ) {
                            Text(
                                statusLabel(day.dayStatus),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = statusColor(day.dayStatus),
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp,
                            )
                        }
                    }

                    ScoreRow("Mahalla ichidagi vaqt", day.scoreInside, 35.0)
                    ScoreRow("Hududiy qamrov", day.scoreCoverage, 30.0)
                    ScoreRow("Mazmunli to'xtashlar", day.scoreStops, 25.0)
                    ScoreRow("Intizom", day.scoreDiscipline, 10.0)
                }
            }

            // --- raqamlar ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatRow("Mahalla ichida", hm(day.insideS))
                    StatRow("Yurilgan masofa", "${"%.2f".format(day.distanceM / 1000)} km")
                    StatRow("Hududiy qamrov", "${day.cellsVisited} / ${day.cellsTotal} katakcha")
                    StatRow("To'xtashlar", "${day.stopsCount} ta (${day.documentedStops} tasi izohli)")
                    if (day.gapS > 0) StatRow("Uzilish", hm(day.gapS))
                }
            }

            // --- to'xtashlar (rasm va izoh biriktirish shu yerda) ---
            if (day.stops.isNotEmpty()) {
                Text("Bugungi to'xtashlar", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text(
                    "Har bir tashrifga rasm va izoh qo'shsangiz, «Mazmunli " +
                    "to'xtashlar» ballingiz oshadi.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                day.stops.forEach { stop ->
                    StopCard(
                        stop = stop,
                        localPhotos = photosByStop[stop.startedAt].orEmpty(),
                        localNote = notesByAnchor[stop.startedAt],
                        onPhotoTaken = onPhotoTaken,
                        onNoteSaved = onNoteSaved,
                    )
                }
            } else {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("Hali to'xtash aniqlanmadi", fontWeight = FontWeight.Medium)
                        Text(
                            "To'xtash bir joyda kamida 5 daqiqa turganda paydo bo'ladi. " +
                            "Shundan keyin unga rasm va izoh qo'sha olasiz.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                    }
                }
            }

            // --- aniqlangan holatlar ---
            if (day.events.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Aniqlangan holatlar", fontWeight = FontWeight.SemiBold)
                        day.events.forEach { e ->
                            Text("• ${eventLabel(e.type)}",
                                 style = MaterialTheme.typography.bodySmall)
                        }
                        Text(
                            "Bu holatlar ballingizni kamaytirmaydi — ular qayd etiladi " +
                            "va rahbariyat tomonidan ko'rib chiqiladi.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.Gray,
                        )
                    }
                }
            }
        } else if (day != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.padding(24.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Bugun ish kuni emas — kuzatuv olib borilmaydi", color = Color.Gray)
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onRefresh, enabled = !loading, modifier = Modifier.weight(1f)) {
                Text(if (loading) "Yuklanmoqda…" else "Yangilash")
            }
            OutlinedButton(onClick = onSync, enabled = !loading, modifier = Modifier.weight(1f)) {
                Text("Hozir yuborish")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.Gray)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
