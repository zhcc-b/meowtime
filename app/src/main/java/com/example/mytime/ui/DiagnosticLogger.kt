package com.example.mytime.ui

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant

/** Small, local-only diagnostic trail for recovering context after an unexpected exit. */
object DiagnosticLogger {
    private const val FILE_NAME = "mytime-diagnostics.log"
    private const val MAX_BYTES = 512 * 1024L
    private const val RETAIN_BYTES = 256 * 1024

    // Process-owned, bounded diagnostic writer; crashes still flush synchronously below.
    private val executor = java.util.concurrent.ThreadPoolExecutor(
        0, 1, 30L, java.util.concurrent.TimeUnit.SECONDS,
        java.util.concurrent.ArrayBlockingQueue<Runnable>(256),
        java.util.concurrent.ThreadFactory { task -> Thread(task, "MeowDiagnostics").apply { isDaemon = true } },
        java.util.concurrent.ThreadPoolExecutor.DiscardPolicy()
    )
    private val lock = Any()
    @Volatile private var applicationContext: Context? = null
    @Volatile private var crashHandlerInstalled = false

    private val session = java.util.UUID.randomUUID().toString().take(8)
    private var lastSnapshot = "none"

    @Synchronized fun initialize(context: Context) {
        if (applicationContext != null) return
        applicationContext = context.applicationContext
        val prefs = context.getSharedPreferences("diagnostic_session", Context.MODE_PRIVATE)
        val previous = prefs.getString("session", null)
        if (previous != null) {
            log("RECOVERY", "previous session=$previous; managedCrash=${prefs.getBoolean("crashed", false)}; last=${prefs.getString("snapshot", "unknown")}; lifecycle=${prefs.getString("lifecycle", "unknown")}; an unrecorded exit may be a system kill, native crash, or force-stop")
        }
        prefs.edit().putString("session", session).putBoolean("crashed", false).apply()
        log("APP", "session=$session; sdk=${android.os.Build.VERSION.SDK_INT}; model=${android.os.Build.MODEL}")
    }

    fun snapshot(state: String) {
        val context = applicationContext ?: return
        val memory = android.os.Debug.MemoryInfo()
        android.os.Debug.getMemoryInfo(memory)
        val runtime = Runtime.getRuntime()
        val javaKb = (runtime.totalMemory() - runtime.freeMemory()) / 1024
        log("HEARTBEAT", "session=$session; $state; pssKb=${memory.totalPss}; javaKb=$javaKb; nativeKb=${android.os.Debug.getNativeHeapAllocatedSize() / 1024}")
        if (state != lastSnapshot) {
            lastSnapshot = state
            context.getSharedPreferences("diagnostic_session", Context.MODE_PRIVATE)
                .edit().putString("snapshot", state).apply()
            log("SNAPSHOT", state)
        }
    }

    fun log(area: String, message: String, error: Throwable? = null) {
        val context = applicationContext ?: return
        if (area == "LIFECYCLE") {
            context.getSharedPreferences("diagnostic_session", Context.MODE_PRIVATE)
                .edit().putString("lifecycle", message).apply()
        }
        val line = formatLine(area, message, error)
        executor.execute { append(context, line) }
    }

    fun installCrashHandler(context: Context) {
        initialize(context)
        synchronized(lock) {
            if (crashHandlerInstalled) return
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                // The executor may not get CPU time during process teardown, so persist this line now.
                append(context.applicationContext, formatLine("CRASH", "uncaught exception on ${thread.name}", error))
                context.getSharedPreferences("diagnostic_session", Context.MODE_PRIVATE)
                    .edit().putBoolean("crashed", true).commit()
                previousHandler?.uncaughtException(thread, error)
            }
            crashHandlerInstalled = true
        }
    }

    private fun formatLine(area: String, message: String, error: Throwable?): String {
        val detail = error?.let {
            StringWriter().use { writer ->
                PrintWriter(writer).use { printer -> it.printStackTrace(printer) }
                "\n${writer}"
            }
        }.orEmpty()
        return "${Instant.now()} [$area] $message$detail\n"
    }

    private fun append(context: Context, line: String) {
        runCatching {
            synchronized(lock) {
                val file = File(context.filesDir, FILE_NAME)
                if (file.length() > MAX_BYTES) {
                    val retained = file.readBytes().takeLast(RETAIN_BYTES).toByteArray()
                    file.writeBytes(retained)
                }
                file.appendText(line)
            }
        }
    }
}
