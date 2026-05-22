package dev.pointandshoot

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.measureTimeMillis

/**
 * Memory profiler for extended capture sessions.
 * Monitors memory usage, tracks performance metrics, and logs memory-related events.
 */
class MemoryProfiler private constructor(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private var isProfiling = false
    private var profilingJob: Job? = null
    private val memorySnapshots = mutableListOf<MemorySnapshot>()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    
    data class MemorySnapshot(
        val timestamp: Long,
        val usedMemoryMB: Long,
        val maxMemoryMB: Long,
        val availableMemoryMB: Long,
        val memoryPressure: Float,
        val event: String? = null
    )
    
    companion object {
        const val TAG = "PNS.MemoryProfiler"

        @Volatile
        private var INSTANCE: MemoryProfiler? = null

        fun getInstance(context: Context, scope: CoroutineScope): MemoryProfiler {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MemoryProfiler(context.applicationContext, scope).also { INSTANCE = it }
            }
        }
    }
    
    /**
     * Start profiling memory usage during capture sessions.
     */
    fun startProfiling(intervalMs: Long = 5000L) {
        if (isProfiling) {
            Log.w(TAG, "Profiling already started")
            return
        }
        
        Log.i(TAG, "Starting memory profiling with ${intervalMs}ms interval")
        isProfiling = true
        memorySnapshots.clear()
        
        profilingJob = scope.launch {
            while (isProfiling) {
                try {
                    val snapshot = captureMemorySnapshot()
                    memorySnapshots.add(snapshot)
                    
                    // Log memory status
                    Log.d(TAG, snapshot.toString())
                    
                    // Check for memory pressure
                    if (snapshot.memoryPressure > 0.8f) {
                        Log.w(TAG, "High memory pressure detected: ${snapshot.memoryPressure}")
                        onMemoryPressureHigh(snapshot)
                    }
                    
                    if (snapshot.memoryPressure > 0.9f) {
                        Log.e(TAG, "Critical memory pressure: ${snapshot.memoryPressure}")
                        onMemoryPressureCritical(snapshot)
                    }
                    
                    delay(intervalMs)
                } catch (e: Exception) {
                    Log.e(TAG, "Error during memory profiling", e)
                    delay(intervalMs)
                }
            }
        }
    }
    
    /**
     * Stop profiling and generate report.
     */
    fun stopProfiling(): MemoryReport {
        if (!isProfiling) {
            Log.w(TAG, "Profiling not started")
            return MemoryReport(emptyList())
        }
        
        Log.i(TAG, "Stopping memory profiling")
        isProfiling = false
        profilingJob?.cancel()
        
        val report = generateReport()
        Log.i(TAG, "Memory profiling completed: ${report}")
        
        return report
    }
    
    /**
     * Capture a single memory snapshot with event annotation.
     */
    fun logEvent(event: String) {
        val snapshot = captureMemorySnapshot(event)
        memorySnapshots.add(snapshot)
        Log.d(TAG, "Event logged: $event - ${snapshot}")
    }
    
    private fun captureMemorySnapshot(event: String? = null): MemorySnapshot {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMemory = runtime.maxMemory() / (1024 * 1024)
        val availableMemory = memoryInfo.availMem / (1024 * 1024)
        val memoryPressure = usedMemory.toFloat() / maxMemory.toFloat()
        
        return MemorySnapshot(
            timestamp = System.currentTimeMillis(),
            usedMemoryMB = usedMemory,
            maxMemoryMB = maxMemory,
            availableMemoryMB = availableMemory,
            memoryPressure = memoryPressure,
            event = event
        )
    }
    
    private fun onMemoryPressureHigh(snapshot: MemorySnapshot) {
        // Trigger garbage collection
        System.gc()
        
        // Log detailed memory info
        val runtime = Runtime.getRuntime()
        Log.w(TAG, "Memory pressure response - " +
                "Used: ${snapshot.usedMemoryMB}MB, " +
                "Total: ${runtime.totalMemory() / (1024 * 1024)}MB, " +
                "Free: ${runtime.freeMemory() / (1024 * 1024)}MB")
    }
    
    private fun onMemoryPressureCritical(snapshot: MemorySnapshot) {
        // Emergency memory cleanup
        Log.e(TAG, "CRITICAL: Emergency memory cleanup triggered")
        
        // Force multiple garbage collections
        repeat(3) {
            System.gc()
            Thread.sleep(100)
        }
        
        // Log final state
        val finalSnapshot = captureMemorySnapshot("Emergency cleanup")
        memorySnapshots.add(finalSnapshot)
    }
    
    private fun generateReport(): MemoryReport {
        if (memorySnapshots.isEmpty()) {
            return MemoryReport(emptyList())
        }
        
        val usedMemoryValues = memorySnapshots.map { it.usedMemoryMB }
        val memoryPressureValues = memorySnapshots.map { it.memoryPressure }
        
        return MemoryReport(
            snapshots = memorySnapshots.toList(),
            durationMs = memorySnapshots.last().timestamp - memorySnapshots.first().timestamp,
            avgUsedMemoryMB = usedMemoryValues.average(),
            maxUsedMemoryMB = usedMemoryValues.maxOrNull() ?: 0L,
            minUsedMemoryMB = usedMemoryValues.minOrNull() ?: 0L,
            avgMemoryPressure = memoryPressureValues.average(),
            maxMemoryPressure = memoryPressureValues.maxOrNull() ?: 0f,
            highPressureEvents = memorySnapshots.count { it.memoryPressure > 0.8f },
            criticalPressureEvents = memorySnapshots.count { it.memoryPressure > 0.9f }
        )
    }
    
    /**
     * Save profiling report to file.
     */
    fun saveReportToFile(report: MemoryReport, filename: String? = null): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val finalFilename = filename ?: "memory_profile_$timestamp.csv"
        
        val file = File(context.getExternalFilesDir(null), "memory_profiles")
        if (!file.exists()) {
            file.mkdirs()
        }
        
        val reportFile = File(file, finalFilename)
        
        try {
            FileWriter(reportFile).use { writer ->
                // Write header
                writer.appendLine("Timestamp,UsedMemoryMB,MaxMemoryMB,AvailableMemoryMB,MemoryPressure,Event")
                
                // Write data
                report.snapshots.forEach { snapshot ->
                    val timestampStr = dateFormat.format(Date(snapshot.timestamp))
                    writer.appendLine("$timestampStr,${snapshot.usedMemoryMB},${snapshot.maxMemoryMB},${snapshot.availableMemoryMB},${snapshot.memoryPressure},${snapshot.event ?: ""}")
                }
                
                // Write summary
                writer.appendLine("")
                writer.appendLine("Summary")
                writer.appendLine("Duration (ms),${report.durationMs}")
                writer.appendLine("Avg Used Memory (MB),${report.avgUsedMemoryMB}")
                writer.appendLine("Max Used Memory (MB),${report.maxUsedMemoryMB}")
                writer.appendLine("Min Used Memory (MB),${report.minUsedMemoryMB}")
                writer.appendLine("Avg Memory Pressure,${report.avgMemoryPressure}")
                writer.appendLine("Max Memory Pressure,${report.maxMemoryPressure}")
                writer.appendLine("High Pressure Events,${report.highPressureEvents}")
                writer.appendLine("Critical Pressure Events,${report.criticalPressureEvents}")
            }
            
            Log.i(TAG, "Memory report saved to: ${reportFile.absolutePath}")
            return reportFile.absolutePath
            
        } catch (e: Exception) {
            Log.e(TAG, "Error saving memory report", e)
            throw e
        }
    }
    
    data class MemoryReport(
        val snapshots: List<MemorySnapshot>,
        val durationMs: Long = 0,
        val avgUsedMemoryMB: Double = 0.0,
        val maxUsedMemoryMB: Long = 0,
        val minUsedMemoryMB: Long = 0,
        val avgMemoryPressure: Double = 0.0,
        val maxMemoryPressure: Float = 0f,
        val highPressureEvents: Int = 0,
        val criticalPressureEvents: Int = 0
    ) {
        override fun toString(): String {
            return "MemoryReport(duration=${durationMs}ms, " +
                    "avgMemory=${avgUsedMemoryMB.toInt()}MB, " +
                    "maxMemory=${maxUsedMemoryMB}MB, " +
                    "maxPressure=${String.format("%.2f", maxMemoryPressure)}, " +
                    "highEvents=$highPressureEvents, " +
                    "criticalEvents=$criticalPressureEvents)"
        }
    }
}

/**
 * Extension function for easy memory profiling in capture sessions.
 */
fun Context.startMemoryProfiling(scope: CoroutineScope, intervalMs: Long = 5000L): MemoryProfiler {
    val profiler = MemoryProfiler.getInstance(this, scope)
    profiler.startProfiling(intervalMs)
    return profiler
}
