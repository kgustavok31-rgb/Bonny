package com.larkplayer

import android.app.Activity
import android.os.Build
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Dark = darkColorScheme(
    primary = Color(0xFFA99BFF), onPrimary = Color(0xFF1B1240),
    primaryContainer = Color(0xFF2B2650), onPrimaryContainer = Color(0xFFE3DFFF),
    background = Color(0xFF0E0F14), surface = Color(0xFF0E0F14),
    surfaceVariant = Color(0xFF1A1C26), onSurface = Color(0xFFECEDF5), onSurfaceVariant = Color(0xFF9EA2B5)
)
private val Light = lightColorScheme(
    primary = Color(0xFF5B4BE0), onPrimary = Color.White,
    primaryContainer = Color(0xFFE3DFFF), onPrimaryContainer = Color(0xFF1B1240),
    background = Color(0xFFF7F7FB), surface = Color(0xFFF7F7FB),
    surfaceVariant = Color(0xFFECECF5), onSurface = Color(0xFF16171F), onSurfaceVariant = Color(0xFF6A6E80)
)

@Composable
fun LarkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> Dark
        else -> Light
    }
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        w.statusBarColor = scheme.background.toArgb()
        w.navigationBarColor = scheme.background.toArgb()
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
