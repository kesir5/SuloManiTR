package de.sulo.manitr.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.sulo.manitr.R

@Composable
fun LockScreen(message: String, needsSetup: Boolean, onUnlock: () -> Unit, onOpenSecuritySettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFF0F2C5C), Color(0xFF1A4F9C)))).padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painterResource(R.drawable.app_icon), contentDescription = "Sülo ManiTR",
            modifier = Modifier.size(112.dp).clip(RoundedCornerShape(26.dp)),
        )
        Spacer(Modifier.height(20.dp))
        Text("Sülo ManiTR", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(message, color = Color.White.copy(alpha = .85f), fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        if (needsSetup) {
            Button(onClick = onOpenSecuritySettings, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = C.Pri)) {
                Text("Sicherheitseinstellungen öffnen")
            }
        } else {
            Button(onClick = onUnlock, colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = C.Pri)) {
                Text("Entsperren")
            }
        }
    }
}
