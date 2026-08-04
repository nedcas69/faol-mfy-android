package uz.bekobod.faolmfy.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.bekobod.faolmfy.data.remote.JobTitleDto
import uz.bekobod.faolmfy.data.remote.OrgUnitDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> Selector(
    label: String,
    items: List<T>,
    selected: T?,
    itemLabel: (T) -> String,
    enabled: Boolean,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
    ) {
        OutlinedTextField(
            value = selected?.let(itemLabel) ?: "",
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            items.forEach { item ->
                DropdownMenuItem(
                    text = { Text(itemLabel(item)) },
                    onClick = { onSelect(item); expanded = false },
                )
            }
        }
    }
}

@Composable
fun ActivationScreen(
    regions: List<OrgUnitDto>,
    districts: List<OrgUnitDto>,
    mfys: List<OrgUnitDto>,
    jobs: List<JobTitleDto>,
    loading: Boolean,
    error: String?,
    onRegionSelected: (Int) -> Unit,
    onDistrictSelected: (Int) -> Unit,
    onSubmit: (code: String, fio: String, phone: String, orgUnitId: Int, jobId: Int) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var fio by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var region by remember { mutableStateOf<OrgUnitDto?>(null) }
    var district by remember { mutableStateOf<OrgUnitDto?>(null) }
    var mfy by remember { mutableStateOf<OrgUnitDto?>(null) }
    var job by remember { mutableStateOf<JobTitleDto?>(null) }

    val canSubmit = code.length >= 6 && fio.trim().length >= 5 &&
                    mfy != null && job != null && !loading

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(16.dp))
        Text("Faol MFY", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Mahalla yettiligi hududiy faollik tizimi",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = code,
            onValueChange = { code = it.uppercase().take(8) },
            label = { Text("Faollashtirish kodi") },
            supportingText = { Text("Hokimlik tomonidan beriladi") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = fio,
            onValueChange = { fio = it },
            label = { Text("F.I.Sh.") },
            supportingText = { Text("Pasportdagidek to'liq yozing") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        OutlinedTextField(
            value = phone,
            onValueChange = { phone = it.take(20) },
            label = { Text("Telefon raqami") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth(),
        )

        Selector(
            label = "Viloyat",
            items = regions,
            selected = region,
            itemLabel = { it.nameUz },
            enabled = regions.isNotEmpty(),
        ) {
            region = it; district = null; mfy = null
            onRegionSelected(it.id)
        }

        Selector(
            label = "Tuman / shahar",
            items = districts,
            selected = district,
            itemLabel = { it.nameUz },
            enabled = region != null,
        ) {
            district = it; mfy = null
            onDistrictSelected(it.id)
        }

        Selector(
            label = "MFY (mahalla)",
            items = mfys,
            selected = mfy,
            itemLabel = { it.nameUz },
            enabled = district != null,
        ) { mfy = it }

        Selector(
            label = "Lavozim",
            items = jobs,
            selected = job,
            itemLabel = { it.nameUz },
            enabled = jobs.isNotEmpty(),
        ) { job = it }

        if (error != null) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    error,
                    modifier = Modifier.padding(14.dp),
                    color = Color(0xFFB3261E),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Button(
            onClick = { onSubmit(code, fio, phone, mfy!!.id, job!!.id) },
            enabled = canSubmit,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(vertical = 14.dp),
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.height(20.dp),
                    strokeWidth = 2.dp,
                    color = Color.White,
                )
            } else {
                Text("Ro'yxatdan o'tish", fontSize = 16.sp)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8E9)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Ilova nima qiladi", fontWeight = FontWeight.SemiBold)
                Text(
                    "• Faqat 09:00–18:00 orasida, dushanba–juma joylashuvni yozadi\n" +
                    "• 18:00 da butunlay o'chadi, dam olish kunlari ishlamaydi\n" +
                    "• Mikrofon, qo'ng'iroqlar, SMS va kontaktlarni O'QIMAYDI\n" +
                    "• Siz faqat o'z natijangizni ko'rasiz",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun PendingApprovalScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Ro'yxatdan o'tdingiz", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(
            "Hokimlik administratori ma'lumotlaringizni tasdiqlagandan keyin " +
            "kuzatuv boshlanadi. Ilovani yopib turishingiz mumkin.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Gray,
        )
    }
}
