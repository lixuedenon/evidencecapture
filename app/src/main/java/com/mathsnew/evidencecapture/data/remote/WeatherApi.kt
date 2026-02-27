// app/src/main/java/com/mathsnew/evidencecapture/data/remote/WeatherApi.kt
// Kotlin - 数据层，OpenWeatherMap API 接口定义

package com.mathsnew.evidencecapture.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

data class WeatherResponse(
    val weather: List<WeatherDesc>,
    val main: WeatherMain,
    val wind: WeatherWind
)

data class WeatherDesc(
    val description: String
)

data class WeatherMain(
    val temp: Float,
    val humidity: Float
)

data class WeatherWind(
    val speed: Float
)

interface WeatherApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") appid: String,
        @Query("units") units: String = "metric",
        @Query("lang") lang: String = "zh_cn"
    ): WeatherResponse
}