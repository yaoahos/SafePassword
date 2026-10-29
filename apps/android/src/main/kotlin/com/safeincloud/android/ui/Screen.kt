package com.safeincloud.android.ui

sealed interface Screen {
    data object Unlock : Screen
    data object Vault : Screen
    data object Tools : Screen
    data object Security : Screen
    data object Settings : Screen
    data class ViewItem(val itemId: String) : Screen
    data class EditItem(val itemId: String?, val fromView: Boolean = false) : Screen
    data class WebDav(val restore: Boolean = false) : Screen
    data object Backups : Screen
    data object VaultManager : Screen
    data class LabelManager(val create: Boolean = false) : Screen
    data object Trash : Screen
    data object Theme : Screen
    data object About : Screen
}

enum class SortMode {
    TITLE,
    UPDATED,
    CREATED,
    SIZE
}
