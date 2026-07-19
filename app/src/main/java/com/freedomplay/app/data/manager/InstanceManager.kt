package com.freedomplay.app.data.manager

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.freedomplay.app.util.CrashLogger
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val Context.instanceHealthDataStore: DataStore<Preferences> by preferencesDataStore(name = "instance_health")

enum class InstanceType {
    PIPED,
    INVIDIOUS
}

data class InstanceHealth(
    val failCount: Int = 0,
    val successCount: Int = 0,
    val lastFailureTime: Long = 0,
    val lastSuccessTime: Long = 0,
    val avgLatencyMs: Long = 0,
    val consecutiveFails: Int = 0
)

data class InstanceConfig(
    val url: String,
    val type: InstanceType,
    val priority: Int = 0
)

@Singleton
class InstanceManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val MAX_CONSECUTIVE_FAILS = 5
        private const val FAIL_COOLDOWN_MS = 60_000L
        private const val PERMANENT_FAIL_THRESHOLD = 20
        private const val LATENCY_WEIGHT = 0.3f
        private const val SUCCESS_WEIGHT = 0.5f
        private const val RECENCY_WEIGHT = 0.2f
        private val KEY_INSTANCE_HEALTH = stringPreferencesKey("instance_health_map")
    }

    private val healthMap = ConcurrentHashMap<String, InstanceHealth>()
    private val dataStore = context.instanceHealthDataStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        withContext(Dispatchers.IO) {
            try {
                val prefs = dataStore.data.first()
                val json = prefs[KEY_INSTANCE_HEALTH]
                if (!json.isNullOrBlank()) {
                    val type = object : TypeToken<Map<String, InstanceHealth>>() {}.type
                    val loaded: Map<String, InstanceHealth> = try {
                        gson.fromJson(json, type) ?: emptyMap()
                    } catch (e: Exception) {
                        CrashLogger.d("Failed to parse instance health JSON: ${e.message}")
                        emptyMap()
                    }
                    loaded.forEach { (url, health) ->
                        healthMap[url] = health
                    }
                    CrashLogger.d("InstanceManager initialized: ${healthMap.size} instances loaded")
                }
            } catch (e: Exception) {
                CrashLogger.d("InstanceManager init failed: ${e.message}")
            }
            initialized = true
        }
    }

    fun getHealthScore(instanceUrl: String): Float {
        val health = healthMap[instanceUrl] ?: return 0.5f
        if (isCircuitBreakerTripped(health)) return 0.0f
        val totalRequests = health.successCount + health.failCount
        if (totalRequests == 0) return 0.5f
        val successRate = health.successCount.toFloat() / totalRequests
        val latencyScore = calculateLatencyScore(health.avgLatencyMs)
        val recencyScore = calculateRecencyScore(health.lastSuccessTime)
        return (successRate * SUCCESS_WEIGHT) + (latencyScore * LATENCY_WEIGHT) + (recencyScore * RECENCY_WEIGHT)
    }

    fun isAvailable(instanceUrl: String): Boolean {
        val health = healthMap[instanceUrl] ?: return true
        if (isCircuitBreakerTripped(health)) return false
        return health.failCount < PERMANENT_FAIL_THRESHOLD
    }

    fun getOrderedInstances(instances: List<InstanceConfig>): List<InstanceConfig> {
        return instances
            .filter { isAvailable(it.url) }
            .sortedWith(compareByDescending<InstanceConfig> { getHealthScore(it.url) }
                .thenBy { it.priority })
    }

    fun recordSuccess(instanceUrl: String, latencyMs: Long) {
        val current = healthMap[instanceUrl] ?: InstanceHealth()
        val newAvgLatency = if (current.successCount == 0) {
            latencyMs
        } else {
            (current.avgLatencyMs * 0.7 + latencyMs * 0.3).toLong()
        }
        healthMap[instanceUrl] = current.copy(
            successCount = current.successCount + 1,
            consecutiveFails = 0,
            avgLatencyMs = newAvgLatency,
            lastSuccessTime = System.currentTimeMillis()
        )
        persistHealthMap()
    }

    fun recordFailure(instanceUrl: String) {
        val current = healthMap[instanceUrl] ?: InstanceHealth()
        val newConsecutiveFails = current.consecutiveFails + 1
        healthMap[instanceUrl] = current.copy(
            failCount = current.failCount + 1,
            consecutiveFails = newConsecutiveFails,
            lastFailureTime = System.currentTimeMillis()
        )
        persistHealthMap()
    }

    fun resetInstance(instanceUrl: String) {
        healthMap.remove(instanceUrl)
        persistHealthMap()
        CrashLogger.d("InstanceManager: reset health for $instanceUrl")
    }

    fun getDebugInfo(): Map<String, String> {
        return healthMap.map { (url, health) ->
            val circuitBreaker = if (isCircuitBreakerTripped(health)) "TRIPPED" else "OK"
            val total = health.successCount + health.failCount
            val successRate = if (total > 0) "%.1f%%".format(health.successCount * 100f / total) else "N/A"
            url to "score=%.2f | success=$successRate | fails=${health.failCount}/${health.consecutiveFails} | latency=${health.avgLatencyMs}ms | circuit=$circuitBreaker"
        }.toMap()
    }

    private fun calculateLatencyScore(avgLatencyMs: Long): Float {
        return when {
            avgLatencyMs <= 0 -> 0.5f
            avgLatencyMs >= 1000 -> 0.0f
            else -> 1.0f - (avgLatencyMs.toFloat() / 1000f)
        }
    }

    private fun calculateRecencyScore(lastSuccessTime: Long): Float {
        if (lastSuccessTime == 0L) return 0.1f
        val elapsed = System.currentTimeMillis() - lastSuccessTime
        return when {
            elapsed < 5 * 60 * 1000 -> 1.0f
            elapsed < 30 * 60 * 1000 -> 0.7f
            elapsed < 2 * 60 * 60 * 1000 -> 0.4f
            else -> 0.1f
        }
    }

    private fun isCircuitBreakerTripped(health: InstanceHealth): Boolean {
        if (health.consecutiveFails < MAX_CONSECUTIVE_FAILS) return false
        val elapsed = System.currentTimeMillis() - health.lastFailureTime
        return elapsed < FAIL_COOLDOWN_MS
    }

    private fun persistHealthMap() {
        try {
            val json = gson.toJson(healthMap.toMap())
            scope.launch {
                dataStore.edit { prefs ->
                    prefs[KEY_INSTANCE_HEALTH] = json
                }
            }
        } catch (e: Exception) {
            CrashLogger.d("InstanceManager: persist failed: ${e.message}")
        }
    }
}
