package com.ivi.audiobook.presentation.navigation

sealed class AppRoute {
    data object Library : AppRoute()
    data class Player(val bookId: Long) : AppRoute()
}
