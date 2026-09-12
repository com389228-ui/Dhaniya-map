package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.location.SearchResult

@Composable
fun DirectCoordinatesDialog(
    currentLocation: Location?,
    fixedOrigin: SearchResult?,
    destination: SearchResult?,
    onDismiss: () -> Unit,
    onSubmit: (
        originName: String,
        originLat: Double,
        originLng: Double,
        destName: String,
        destLat: Double,
        destLng: Double
    ) -> Unit
) {
    val context = LocalContext.current

    // Initial state setup
    var originName by remember {
        mutableStateOf(fixedOrigin?.name ?: "Point A (आरंभिक)")
    }
    var originLatText by remember {
        val lat = fixedOrigin?.latitude ?: currentLocation?.latitude ?: 28.6139
        mutableStateOf(String.format(java.util.Locale.US, "%.6f", lat))
    }
    var originLngText by remember {
        val lng = fixedOrigin?.longitude ?: currentLocation?.longitude ?: 77.2090
        mutableStateOf(String.format(java.util.Locale.US, "%.6f", lng))
    }

    var destName by remember {
        mutableStateOf(destination?.name ?: "Point B (मंज़िल)")
    }
    var destLatText by remember {
        val lat = destination?.latitude ?: 27.1751
        mutableStateOf(String.format(java.util.Locale.US, "%.6f", lat))
    }
    var destLngText by remember {
        val lng = destination?.longitude ?: 78.0421
        mutableStateOf(String.format(java.util.Locale.US, "%.6f", lng))
    }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Helper to parse clipboard text containing "lat, lng" or "lat lng"
    fun parseClipboardCoordinates(): Pair<Double, Double>? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).text?.toString()?.trim() ?: return null
            // Regex match for two decimal numbers separated by comma or space
            val regex = Regex("""([-+]?\d{1,2}(?:\.\d+)?)\s*[, ]\s*([-+]?\d{1,3}(?:\.\d+)?)""")
            val match = regex.find(text)
            if (match != null) {
                val lat = match.groupValues[1].toDoubleOrNull()
                val lng = match.groupValues[2].toDoubleOrNull()
                if (lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0) {
                    return Pair(lat, lng)
                }
            }
        }
        return null
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(20.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
            border = CardDefaults.outlinedCardBorder().copy(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF00E5FF), Color(0xFFFFD54F))
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(18.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "🌐 सीधे Lat / Lon इनपुट करें",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Point A और Point B के अक्षांश व देशांतर दर्ज करें",
                            color = Color(0xFF94A3B8),
                            fontSize = 11.sp
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // POINT A CONTAINER
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF162235)),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFF00E676).copy(alpha = 0.5f), Color(0xFF00E5FF).copy(alpha = 0.5f))
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFF00E676).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.GpsFixed,
                                        contentDescription = null,
                                        tint = Color(0xFF00E676),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Point A (आरंभिक बिंदु)",
                                    color = Color(0xFF00E676),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Paste button
                                OutlinedButton(
                                    onClick = {
                                        val coords = parseClipboardCoordinates()
                                        if (coords != null) {
                                            originLatText = String.format(java.util.Locale.US, "%.6f", coords.first)
                                            originLngText = String.format(java.util.Locale.US, "%.6f", coords.second)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                                ) {
                                    Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFF00E5FF), modifier = Modifier.size(12.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("पेस्ट", fontSize = 10.sp, color = Color(0xFF00E5FF))
                                }

                                // Use GPS Button
                                if (currentLocation != null) {
                                    Button(
                                        onClick = {
                                            originLatText = String.format(java.util.Locale.US, "%.6f", currentLocation.latitude)
                                            originLngText = String.format(java.util.Locale.US, "%.6f", currentLocation.longitude)
                                            originName = "वर्तमान GPS स्थान"
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676)),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.height(28.dp),
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                                    ) {
                                        Text("GPS लें", fontSize = 10.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = originName,
                            onValueChange = { originName = it },
                            label = { Text("स्थान का नाम (वैकल्पिक)", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF00E676),
                                unfocusedBorderColor = Color(0xFF334155)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = originLatText,
                                onValueChange = { originLatText = it },
                                label = { Text("Latitude (अक्षांश)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF00E676),
                                    unfocusedBorderColor = Color(0xFF334155)
                                )
                            )
                            OutlinedTextField(
                                value = originLngText,
                                onValueChange = { originLngText = it },
                                label = { Text("Longitude (देशांतर)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFF00E676),
                                    unfocusedBorderColor = Color(0xFF334155)
                                )
                            )
                        }
                    }
                }

                // SWAP BUTTON
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        onClick = {
                            val tempName = originName
                            val tempLat = originLatText
                            val tempLng = originLngText

                            originName = destName
                            originLatText = destLatText
                            originLngText = destLngText

                            destName = tempName
                            destLatText = tempLat
                            destLngText = tempLng
                        },
                        shape = CircleShape,
                        color = Color(0xFF1E293B),
                        border = CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(Color(0xFF00E676), Color(0xFFFF5252)))
                        ),
                        modifier = Modifier.size(34.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.SwapVert,
                                contentDescription = "Swap A and B",
                                tint = Color(0xFFFFD54F),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // POINT B CONTAINER
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF162235)),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder().copy(
                        brush = Brush.horizontalGradient(
                            listOf(Color(0xFFFF5252).copy(alpha = 0.5f), Color(0xFFFFB300).copy(alpha = 0.5f))
                        )
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .background(Color(0xFFFF5252).copy(alpha = 0.2f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Point B (मंज़िल - Destination)",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    val coords = parseClipboardCoordinates()
                                    if (coords != null) {
                                        destLatText = String.format(java.util.Locale.US, "%.6f", coords.first)
                                        destLngText = String.format(java.util.Locale.US, "%.6f", coords.second)
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(28.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, tint = Color(0xFFFF8A80), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("पेस्ट", fontSize = 10.sp, color = Color(0xFFFF8A80))
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = destName,
                            onValueChange = { destName = it },
                            label = { Text("मंज़िल का नाम (वैकल्पिक)", fontSize = 11.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFFFF5252),
                                unfocusedBorderColor = Color(0xFF334155)
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = destLatText,
                                onValueChange = { destLatText = it },
                                label = { Text("Latitude (अक्षांश)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFF5252),
                                    unfocusedBorderColor = Color(0xFF334155)
                                )
                            )
                            OutlinedTextField(
                                value = destLngText,
                                onValueChange = { destLngText = it },
                                label = { Text("Longitude (देशांतर)", fontSize = 10.sp) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = Color(0xFFFF5252),
                                    unfocusedBorderColor = Color(0xFF334155)
                                )
                            )
                        }
                    }
                }

                // Error Message Display
                errorMessage?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ $err",
                        color = Color(0xFFFF5252),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("रद्द करें", color = Color(0xFF94A3B8), fontSize = 13.sp)
                    }

                    Button(
                        onClick = {
                            val oLat = originLatText.toDoubleOrNull()
                            val oLng = originLngText.toDoubleOrNull()
                            val dLat = destLatText.toDoubleOrNull()
                            val dLng = destLngText.toDoubleOrNull()

                            if (oLat == null || oLat !in -90.0..90.0) {
                                errorMessage = "कृपया Point A का मान्य Latitude (-90 से 90) दर्ज करें"
                                return@Button
                            }
                            if (oLng == null || oLng !in -180.0..180.0) {
                                errorMessage = "कृपया Point A का मान्य Longitude (-180 से 180) दर्ज करें"
                                return@Button
                            }
                            if (dLat == null || dLat !in -90.0..90.0) {
                                errorMessage = "कृपया Point B का मान्य Latitude (-90 से 90) दर्ज करें"
                                return@Button
                            }
                            if (dLng == null || dLng !in -180.0..180.0) {
                                errorMessage = "कृपया Point B का मान्य Longitude (-180 से 180) दर्ज करें"
                                return@Button
                            }

                            errorMessage = null
                            onSubmit(originName, oLat, oLng, destName, dLat, dLng)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = "✅ सटीक दूरी नापें",
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }
}
