package com.example.mytime.ui

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import java.util.concurrent.Executors

/** Small, local-only diagnostic trail for recovering context after an unexpected exit. */
object DiagnosticLogger {
    private const val FILE_NAME = "mytime-diagnostics.log"
    private const val MAX_BYTES = 512 * 1024L
    private const val RETAIN_BYTES = 256 * 1024

    private val executor = Executors.newSingleThreadExecutor()
    private val lock = Any()
    @Volatile private var applicationContext: Context? = null
    @Volatile private var crashHandlerInstalled = false

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
        log("APP", "diagnostic logging initialized")
    }

    fun log(area: String, message: String, error: Throwable? = null) {
        val context = applicationContext ?: return
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
