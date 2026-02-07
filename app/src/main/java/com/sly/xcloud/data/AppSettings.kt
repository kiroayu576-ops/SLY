package com.sly.xcloud.data

data class AppSettings(
    val startUrl: String = DEFAULT_START_URL,
    val desktopMode: Boolean = false,
    val keepScreenOn: Boolean = true,
    val scriptsEnabled: Boolean = true,
    val immersiveMode: Boolean = false,
    val customCssEnabled: Boolean = false,
    val customCss: String = ""
) {
    companion object {
        const val DEFAULT_START_URL = "https://www.xbox.com/play"
    }
}
