package io.github.recrivenvi.connectorx.platform

object StorageKeys {
    const val DARK_MODE = "dark_mode"
    const val THEME_PURE_BLACK = "theme_pure_black"
    const val THEME_MONET = "theme_monet"
    const val THEME_PALETTE_STYLE = "theme_palette_style"
    const val THEME_ACCENT_COLOR = "theme_accent_color"
    const val THEME_BLUR = "theme_blur"
    const val THEME_BLUR_STYLE = "theme_blur_style"
    const val THEME_FLOATING_BOTTOM_BAR = "theme_floating_bottom_bar"
    const val THEME_FLOATING_BOTTOM_BAR_STYLE = "theme_floating_bottom_bar_style"
    const val THEME_BOTTOM_BAR_MODE = "theme_bottom_bar_mode"
    const val THEME_DENSITY_SCALE = "theme_density_scale"
    const val NAV_RAIL_EXPANDED = "nav_rail_expanded"
    const val PREDICTIVE_BACK = "predictive_back"
    const val SWIPE_DISMISS = "swipe_dismiss"
}

interface PlatformStorage {
    fun getString(key:String,default:String):String
    fun putString(key:String,value:String)
}
