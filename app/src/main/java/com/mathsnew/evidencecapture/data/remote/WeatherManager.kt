// app/src/main/java/com/mathsnew/evidencecapture/data/remote/WeatherManager.kt
// Kotlin - 数据层，天气数据管理，负责请求 API 并回填快照数据库

package com.mathsnew.evidencecapture.data.remote

import android.util.Log
import com.mathsnew.evidencecapture.domain.repository.SensorSnapshotRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherManager @Inject constructor(
    private val snapshotRepository: SensorSnapshotRepository
) {
    private val api: WeatherApi by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }

    /**
     * 异步获取天气并回填到指定证据的快照记录
     * 应在证据保存后立即异步调用，失败时静默降级（不影响取证流程）
     *
     * @param evidenceId 关联证据 ID
     * @param lat        纬度
     * @param lon        经度
     * @param apiKey     OpenWeatherMap API Key，在 local.properties 中配置
     */
    suspend fun fetchAndUpdate(
        evidenceId: String,
        lat: Double,
        lon: Double,
        apiKey: String
    ) = withContext(Dispatchers.IO) {
        if (lat == 0.0 && lon == 0.0) return@withContext
        if (apiKey.isEmpty() || apiKey == "YOUR_API_KEY") {
            Log.w(TAG, "Weather API key not configured, skipping")
            return@withContext
        }
        try {
            val response = api.getCurrentWeather(lat, lon, apiKey)
            snapshotRepository.updateWeather(
                evidenceId = evidenceId,
                desc = response.weather.firstOrNull()?.description ?: "",
                temperature = response.main.temp,
                humidity = response.main.humidity,
                windSpeed = response.wind.speed
            )
            Log.i(TAG, "Weather updated for $evidenceId: ${response.weather.firstOrNull()?.description}")
        } catch (e: Exception) {
            Log.w(TAG, "Weather fetch failed for $evidenceId: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "WeatherManager"
        // 在 local.properties 中添加：WEATHER_API_KEY=你的key
        // 并在 build.gradle.kts 的 defaultConfig 中添加：
        // buildConfigField("String", "WEATHER_API_KEY", "\"${properties["WEATHER_API_KEY"]}\"")
        const val PLACEHOLDER_KEY = "YOUR_API_KEY"
    }
}