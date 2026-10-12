package br.com.aggilli.bastao.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Cores da marca Aggilli, tiradas do logo: o "A" azul/ciano e a folha verde. */
object Aggilli {
    val Azul = Color(0xFF0B6E96)
    val AzulEscuro = Color(0xFF08506E)
    val Ciano = Color(0xFF14B8DC)
    val Verde = Color(0xFF2E7D16)
    val VerdeFolha = Color(0xFF5DBB1E)

    /** Estados do bastão: conectado, em andamento, parado. */
    val Ok = Color(0xFF2E7D16)
    val Andamento = Color(0xFFC77700)
    val Parado = Color(0xFF6B7A80)
}

private val Claro = lightColorScheme(
    primary = Aggilli.Azul,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEFFA),
    onPrimaryContainer = Color(0xFF00344A),
    secondary = Aggilli.Verde,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF3CC),
    onSecondaryContainer = Color(0xFF103A05),
    tertiary = Color(0xFF00809C),
    background = Color(0xFFF4F8F9),
    onBackground = Color(0xFF14231C),
    surface = Color.White,
    onSurface = Color(0xFF14231C),
    surfaceVariant = Color(0xFFE2EDF0),
    onSurfaceVariant = Color(0xFF41535A),
    outline = Color(0xFF8BA0A8),
)

private val Escuro = darkColorScheme(
    primary = Color(0xFF6FD3F2),
    onPrimary = Color(0xFF003548),
    primaryContainer = Color(0xFF0B4F6C),
    onPrimaryContainer = Color(0xFFCDEFFA),
    secondary = Color(0xFF8FD96A),
    onSecondary = Color(0xFF103A05),
    secondaryContainer = Color(0xFF22561A),
    onSecondaryContainer = Color(0xFFDDF3CC),
    tertiary = Color(0xFF5FD0EA),
    background = Color(0xFF0D171B),
    onBackground = Color(0xFFE2ECEF),
    surface = Color(0xFF132026),
    onSurface = Color(0xFFE2ECEF),
    surfaceVariant = Color(0xFF22323A),
    onSurfaceVariant = Color(0xFFB7C8CE),
    outline = Color(0xFF6D838B),
)

@Composable
fun AggilliTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Escuro else Claro, content = content)
}
