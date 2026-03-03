// app/src/main/java/com/mathsnew/evidencecapture/data/local/database/dao/SensorSnapshotDao.kt
// Kotlin - 数据层，Room 传感器快照表 DAO

package com.mathsnew.evidencecapture.data.local.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.mathsnew.evidencecapture.data.local.database.entity.SensorSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorSnapshotDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SensorSnapshotEntity)

    @Query("SELECT * FROM sensor_snapshot WHERE evidenceId = :evidenceId LIMIT 1")
    suspend fun getByEvidenceId(evidenceId: String): SensorSnapshotEntity?

    /**
     * 以 Flow 方式持续观察快照，天气/地址异步回填后会自动触发新值
     * 供 EvidenceDetailViewModel 使用，确保天气数据写入后 UI 自动刷新
     */
    @Query("SELECT * FROM sensor_snapshot WHERE evidenceId = :evidenceId LIMIT 1")
    fun observeByEvidenceId(evidenceId: String): Flow<SensorSnapshotEntity?>

    /** 回填天气数据（网络请求完成后异步调用） */
    @Query("""
        UPDATE sensor_snapshot
        SET weatherDesc = :desc,
            temperature = :temperature,
            humidity    = :humidity,
            windSpeed   = :windSpeed
        WHERE evidenceId = :evidenceId
    """)
    suspend fun updateWeather(
        evidenceId: String,
        desc: String,
        temperature: Float,
        humidity: Float,
        windSpeed: Float
    )

    /** 回填逆地理编码地址（Geocoder 或地图 API 返回后调用） */
    @Query("UPDATE sensor_snapshot SET address = :address WHERE evidenceId = :evidenceId")
    suspend fun updateAddress(evidenceId: String, address: String)

    /** 回填网络校准时间戳（请求可信时间服务后调用） */
    @Query("UPDATE sensor_snapshot SET networkTime = :networkTime WHERE evidenceId = :evidenceId")
    suspend fun updateNetworkTime(evidenceId: String, networkTime: Long)
}