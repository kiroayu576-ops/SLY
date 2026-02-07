package com.sly.xcloud.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = mdThemeLightPrimary,
    secondary = mdThemeLightSecondary,
    tertiary = mdThemeLightTertiary,
    background = mdThemeLightBackground,
    surface = mdThemeLightSurface,
    onPrimary = mdThemeLightOnPrimary,
    onSurface = mdThemeLightOnSurface
)

@Composable
fun SlyXcloudTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
