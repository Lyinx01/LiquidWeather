package com.liuli.weather.ui.main

import com.liuli.weather.data.model.Weather

sealed class MainUiState {
    /** 尚未选择任何地点。 */
    object Idle : MainUiState()

    object Loading : MainUiState()

    data class Success(val weather: Weather, val fromCache: Boolean) : MainUiState()

    data class Error(
        val message: String,
        val cached: Weather?,
        val noToken: Boolean
    ) : MainUiState()
}
