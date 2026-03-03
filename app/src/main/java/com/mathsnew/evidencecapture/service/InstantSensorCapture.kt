// app/src/main/java/com/mathsnew/evidencecapture/service/InstantSensorCapture.kt
// Kotlin - 服务层，瞬时传感器采集核心类，含天气API调用

package com.mathsnew.evidencecapture.service

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.mathsnew.evidencecapture.BuildConfig
import com.mathsnew.evidencecapture.data.remote.WeatherApi
import com.mathsnew.evidencecapture.data.remote.WeatherResponse
import com.mathsnew.evidencecapture.domain.model.SensorSnapshot
import com.mathsnew.evidencecapture.domain.repository.SensorSnapshotRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.log10
import kotlin.math.sqrt

@Singleton
class InstantSensorCapture @Inject constructor(
    @ApplicationContext private val context: Context,
    private val snapshotRepository: SensorSnapshotRepository
) {
    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val weatherApi: WeatherApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .client(OkHttpClient.Builder().build())
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(WeatherApi::class.java)
    }

    /**
     * 采集一次完整的环境快照。
     * @param evidenceId     关联证据 ID
     * @param measureDecibel 是否采集分贝。
     *                       拍照取证传 true（默认，麦克风空闲可正常采集）；
     *                       录音/录像取证传 false（麦克风已被占用，跳过采集直接填 0）。
     */
    suspend fun capture(
        evidenceId: String,
        measureDecibel: Boolean = true
    ): SensorSnapshot = withContext(Dispatchers.Default) {
        val capturedAt = System.currentTimeMillis()

        val networkTimeDeferred = async(Dispatchers.IO) { getSntpTime() }
        val locationDeferred = async(Dispatchers.IO) { getLastLocation() }
        val azimuthDeferred = async {
            readSensor(Sensor.TYPE_ROTATION_VECTOR) { values ->
                val rotationMatrix = FloatArray(9)
                SensorManager.getRotationMatrixFromVector(rotationMatrix, values)
                val orientation = FloatArray(3)
                SensorManager.getOrientation(rotationMatrix, orientation)
                val deg = Math.toDegrees(orientation[0].toDouble()).toFloat()
                if (deg < 0f) deg + 360f else deg
            }
        }
        val lightDeferred = async { readSensor(Sensor.TYPE_LIGHT) { it[0] } }
        val pressureDeferred = async { readSensor(Sensor.TYPE_PRESSURE) { it[0] } }
        // measureDecibel = false 时跳过分贝采集，不启动 AudioRecord，直接填 0
        val decibelDeferred = if (measureDecibel) async(Dispatchers.IO) { measureDecibel() } else null
        val wifiDeferred = async(Dispatchers.IO) { getWifiSsid() }
        val operatorDeferred = async(Dispatchers.IO) { getMobileOperator() }

        val networkTime = withTimeoutOrNull(3000L) { networkTimeDeferred.await() } ?: 0L
        val location = withTimeoutOrNull(3000L) { locationDeferred.await() }
        val azimuth = withTimeoutOrNull(2000L) { azimuthDeferred.await() } ?: 0f
        val lightLux = withTimeoutOrNull(2000L) { lightDeferred.await() } ?: 0f
        val pressure = withTimeoutOrNull(2000L) { pressureDeferred.await() } ?: 0f
        val decibel = if (decibelDeferred != null) {
            withTimeoutOrNull(2000L) { decibelDeferred.await() } ?: 0f
        } else 0f
        val wifiSsid = withTimeoutOrNull(1000L) { wifiDeferred.await() } ?: ""
        val operator = withTimeoutOrNull(1000L) { operatorDeferred.await() } ?: ""

        SensorSnapshot(
            id = evidenceId,
            evidenceId = evidenceId,
            capturedAt = capturedAt,
            networkTime = networkTime,
            latitude = location?.latitude ?: 0.0,
            longitude = location?.longitude ?: 0.0,
            altitude = location?.altitude ?: 0.0,
            address = "",
            azimuth = azimuth,
            lightLux = lightLux,
            decibel = decibel,
            pressureHpa = pressure,
            wifiSsid = wifiSsid,
            operator = operator
        ).also {
            Log.i(TAG, "Snapshot: az=${it.azimuth} lux=${it.lightLux} " +
                    "db=${it.decibel} pressure=${it.pressureHpa} " +
                    "lat=${it.latitude} lng=${it.longitude} " +
                    "wifi=${it.wifiSsid} operator=${it.operator} " +
                    "networkTime=$networkTime measureDecibel=$measureDecibel")
        }
    }

    suspend fun fetchAndFillAsync(evidenceId: String, lat: Double, lng: Double) {
        if (lat == 0.0 && lng == 0.0) return

        withContext(Dispatchers.IO) {
            try {
                val address = reverseGeocode(lat, lng)
                if (address.isNotEmpty()) {
                    snapshotRepository.updateAddress(evidenceId, address)
                    Log.i(TAG, "Address updated: $address")
                } else {
                    Log.w(TAG, "Address empty, skip update")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Address update failed: ${e.message}")
            }
        }

        val apiKey = BuildConfig.WEATHER_API_KEY
        if (apiKey.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                try {
                    val response: WeatherResponse =
                        weatherApi.getCurrentWeather(lat, lng, apiKey)
                    snapshotRepository.updateWeather(
                        evidenceId = evidenceId,
                        desc = response.weather.firstOrNull()?.description ?: "",
                        temperature = response.main.temp,
                        humidity = response.main.humidity,
                        windSpeed = response.wind.speed
                    )
                    Log.i(TAG, "Weather updated: ${response.weather.firstOrNull()?.description} " +
                            "${response.main.temp}℃ 湿度${response.main.humidity}% " +
                            "风速${response.wind.speed}m/s")
                } catch (e: Exception) {
                    Log.w(TAG, "Weather update failed: ${e.message}")
                }
            }
        } else {
            Log.w(TAG, "Weather API key not configured")
        }
    }

    /**
     * 公开的分贝采集接口，供外部按需调用
     */
    suspend fun measureDecibelPublic(): Float = withContext(Dispatchers.IO) {
        measureDecibel()
    }

    private fun getSntpTime(): Long {
        val socket = DatagramSocket()
        return try {
            socket.soTimeout = 2500
            val address = InetAddress.getByName("pool.ntp.org")
            val buffer = ByteArray(48)
            buffer[0] = 0x1B
            socket.send(DatagramPacket(buffer, buffer.size, address, 123))
            val response = DatagramPacket(buffer, buffer.size)
            socket.receive(response)
            val seconds = ((buffer[40].toLong() and 0xFF) shl 24) or
                    ((buffer[41].toLong() and 0xFF) shl 16) or
                    ((buffer[42].toLong() and 0xFF) shl 8) or
                    (buffer[43].toLong() and 0xFF)
            (seconds - 2208988800L) * 1000L
        } catch (e: Exception) {
            Log.w(TAG, "SNTP failed: ${e.message}")
            0L
        } finally {
            socket.close()
        }
    }

    @Suppress("MissingPermission")
    private fun getLastLocation(): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        } catch (e: Exception) {
            Log.w(TAG, "GPS unavailable: ${e.message}")
            null
        }
    }

    private suspend fun readSensor(
        sensorType: Int,
        extract: (FloatArray) -> Float
    ): Float? = withContext(Dispatchers.Main) {
        val sensor = sensorManager.getDefaultSensor(sensorType)
            ?: return@withContext null
        withTimeoutOrNull(2000L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : SensorEventListener {
                    override fun onSensorChanged(event: SensorEvent) {
                        sensorManager.unregisterListener(this)
                        if (cont.isActive) cont.resume(extract(event.values))
                    }
                    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
                }
                sensorManager.registerListener(
                    listener, sensor, SensorManager.SENSOR_DELAY_FASTEST
                )
                cont.invokeOnCancellation { sensorManager.unregisterListener(listener) }
            }
        }
    }

    /**
     * 采集一次环境分贝。
     * 修正公式：以 16bit 满量程 32768 为基准计算 dBFS，加 96 偏移映射为 0~96 正值。
     * 原公式 rms > 1.0 的阈值过高，安静环境 RMS 低于 1.0 时永远返回 0。
     * 修正后：安静环境约 20~35dB，正常说话约 55~70dB。
     */
    @Suppress("MissingPermission")
    private fun measureDecibel(): Float {
        val bufferSize = AudioRecord.getMinBufferSize(
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (bufferSize <= 0) return 0f
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            44100,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return 0f
        }
        return try {
            recorder.startRecording()
            val buffer = ShortArray(bufferSize)
            recorder.read(buffer, 0, bufferSize)
            recorder.stop()
            val rms = sqrt(buffer.map { it.toLong() * it.toLong() }.average())
            if (rms > 0.0) {
                val db = (20.0 * log10(rms / 32768.0) + 96.0).toFloat()
                db.coerceIn(0f, 96f)
            } else {
                0f
            }
        } catch (e: Exception) {
            Log.w(TAG, "Decibel failed: ${e.message}")
            0f
        } finally {
            recorder.release()
        }
    }

    @Suppress("DEPRECATION")
    private fun getWifiSsid(): String {
        return try {
            val wm = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wm.connectionInfo ?: return ""
            val ssid = info.ssid ?: return ""
            return when {
                ssid == "<unknown ssid>" -> ""
                ssid == "0x" -> ""
                ssid.length >= 2 && ssid.startsWith("\"") && ssid.endsWith("\"") ->
                    ssid.substring(1, ssid.length - 1)
                else -> ssid
            }
        } catch (e: Exception) {
            Log.w(TAG, "WiFi SSID failed: ${e.message}")
            ""
        }
    }

    private fun getMobileOperator(): String {
        return try {
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            tm.networkOperatorName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    suspend fun reverseGeocode(lat: Double, lng: Double): String = withContext(Dispatchers.IO) {
        if (lat == 0.0 && lng == 0.0) return@withContext ""
        return@withContext try {
            val geocoder = Geocoder(context, Locale.getDefault())
            @Suppress("DEPRECATION")
            val addresses: List<Address>? = geocoder.getFromLocation(lat, lng, 1)
            addresses?.firstOrNull()?.getAddressLine(0) ?: ""
        } catch (e: Exception) {
            Log.w(TAG, "Geocode failed: ${e.message}")
            ""
        }
    }

    companion object {
        private const val TAG = "InstantSensorCapture"
    }
}