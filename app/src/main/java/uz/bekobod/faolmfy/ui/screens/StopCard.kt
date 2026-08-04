package uz.bekobod.faolmfy.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.bekobod.faolmfy.data.remote.StopDto
import uz.bekobod.faolmfy.util.ImageUtils
import java.io.File

/** Lokal navbatdagi rasm — UI uchun soddalashtirilgan ko'rinish. */
data class LocalPhoto(
    val id: Long,
    val path: String,
    val uploaded: Boolean,
    val failed: Boolean,
)

@Composable
private fun PhotoThumb(photo: LocalPhoto) {
    val bitmap = remember(photo.path) {
        runCatching {
            val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeFile(photo.path, opts)
        }.getOrNull()
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFFEEEEEE),
        modifier = Modifier.size(72.dp),
    ) {
        Column {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)),
                )
            } else {
                Spacer(Modifier.fillMaxWidth().height(54.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (photo.uploaded) Icons.Default.CloudDone
                                  else Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = when {
                        photo.uploaded -> Color(0xFF2E7D32)
                        photo.failed -> Color(0xFFC62828)
                        else -> Color(0xFFF9A825)
                    },
                    modifier = Modifier.size(12.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    when {
                        photo.uploaded -> "Yuborildi"
                        photo.failed -> "Xato"
                        else -> "Navbatda"
                    },
                    fontSize = 8.sp,
                    color = Color.DarkGray,
                )
            }
        }
    }
}

@Composable
fun StopCard(
    stop: StopDto,
    localPhotos: List<LocalPhoto>,
    localNote: String?,
    onPhotoTaken: (file: File, anchorTs: String) -> Unit,
    onNoteSaved: (anchorTs: String, note: String) -> Unit,
) {
    val context = LocalContext.current
    var showNoteDialog by remember { mutableStateOf(false) }
    var pendingFile by remember { mutableStateOf<File?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val file = pendingFile
        pendingFile = null
        if (success && file != null && file.exists() && file.length() > 0) {
            onPhotoTaken(file, stop.startedAt)
        } else {
            runCatching { file?.delete() }
        }
    }

    val note = localNote ?: stop.note
    val documented = note != null || localPhotos.isNotEmpty() || stop.photoCount > 0

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (documented) Color(0xFFF1F8E9) else Color(0xFFFAFAFA)
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    "${stop.startedAt.substringAfter('T').take(5)} – " +
                    stop.endedAt.substringAfter('T').take(5),
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    "${stop.durationS / 60} daqiqa",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                stop.address ?: if (stop.insideMfy) "Mahalla ichida"
                                else "Mahalladan tashqarida",
                style = MaterialTheme.typography.bodySmall,
                color = if (stop.insideMfy) Color(0xFF2E7D32) else Color(0xFFEF6C00),
            )

            if (note != null) {
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        note,
                        modifier = Modifier.padding(10.dp),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (localPhotos.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(localPhotos.size) { i -> PhotoThumb(localPhotos[i]) }
                }
            } else if (stop.photoCount > 0) {
                Text(
                    "${stop.photoCount} ta rasm serverda saqlangan",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = {
                    val file = ImageUtils.newTempFile(context)
                    pendingFile = file
                    runCatching {
                        cameraLauncher.launch(ImageUtils.uriFor(context, file))
                    }.onFailure { pendingFile = null }
                }) {
                    Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Rasm")
                }
                TextButton(onClick = { showNoteDialog = true }) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (note == null) "Izoh" else "Izohni o'zgartirish")
                }
            }
        }
    }

    if (showNoteDialog) {
        NoteDialog(
            initial = note.orEmpty(),
            onDismiss = { showNoteDialog = false },
            onSave = {
                onNoteSaved(stop.startedAt, it)
                showNoteDialog = false
            },
        )
    }
}

@Composable
private fun NoteDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("To'xtash izohi") },
        text = {
            Column {
                Text(
                    "Bu joyda nima qildingiz? Masalan: «Karimovlar oilasi bilan " +
                    "suhbat, gaz muammosi bo'yicha».",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 1000) text = it },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Saqlash") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Bekor qilish") } },
    )
}
