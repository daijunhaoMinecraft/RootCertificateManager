package com.daijunhao.certificatemanager.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = TrustGreen80,
    onPrimary = TrustGreenContainerDark,
    primaryContainer = TrustGreenContainerDark,
    onPrimaryContainer = TrustGreenContainerLight,
    secondary = Slate80,
    onSecondary = SlateContainerDark,
    secondaryContainer = SlateContainerDark,
    onSecondaryContainer = SlateContainerLight,
    tertiary = BlueGray80,
    onTertiary = BlueGrayContainerDark,
    tertiaryContainer = BlueGrayContainerDark,
    onTertiaryContainer = BlueGrayContainerLight,
    error = ErrorRed80,
    onError = ErrorContainerDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = ErrorContainerLight,
    background = SurfaceDark,
    onBackground = Color(0xFFE1E3DF),
    surface = SurfaceDark,
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = Color(0xFFBFC9C2),
)

private val LightColorScheme = lightColorScheme(
    primary = TrustGreen40,
    onPrimary = Color.White,
    primaryContainer = TrustGreenContainerLight,
    onPrimaryContainer = InkGreen,
    secondary = Slate40,
    onSecondary = Color.White,
    secondaryContainer = SlateContainerLight,
    onSecondaryContainer = Color(0xFF092016),
    tertiary = BlueGray40,
    onTertiary = Color.White,
    tertiaryContainer = BlueGrayContainerLight,
    onTertiaryContainer = Color(0xFF001F2A),
    error = ErrorRed40,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF410002),
    background = PaleGreen,
    onBackground = Color(0xFF171D1A),
    surface = PaleGreen,
    onSurface = Color(0xFF171D1A),
    surfaceVariant = Color(0xFFDCE5DE),
    onSurfaceVariant = Color(0xFF404943),
)

@Composable
fun CertificateManagerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
