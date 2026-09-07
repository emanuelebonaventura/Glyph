package com.fankes.coloros.notify.framework

import com.fankes.coloros.notify.R
import com.fankes.coloros.notify.diagnostics.AppDiagnostics
import com.fankes.coloros.notify.diagnostics.DiagnosticEvent
import com.fankes.coloros.notify.diagnostics.DiagnosticLevel
import java.io.File
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

object SystemUiRestarter {

    class RestartFailure internal constructor(
        cause: Exception,
        message: String,
        val messageResId: Int? = null,
    ) : Exception("Unable to restart SystemUI", cause) {
        val userMessage: String = message
    }

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "systemui-restarter")
    }
    private val restartCommand = """
        pid=$(/system/bin/pidof com.android.systemui 2>/dev/null)
        if [ -n "${'$'}pid" ]; then
          /system/bin/kill -9 ${'$'}pid
          echo "killed:${'$'}pid"
          exit 0
        fi
        /system/bin/pkill -f com.android.systemui >/dev/null 2>&1 && { echo "pkill"; exit 0; }
        /system/bin/killall com.android.systemui >/dev/null 2>&1 && { echo "killall"; exit 0; }
        echo "not_found"
        exit 1
    """.trimIndent()

    fun restartSystemUi(onResult: (Result<Unit>) -> Unit) {
        try {
            executor.execute {
                deliver(restartResult(), onResult)
            }
        } catch (exception: Exception) {
            deliver(failureResult(exception), onResult)
        }
    }

    private fun restartResult(): Result<Unit> = try {
        restartBlocking()
        AppDiagnostics.logger.report(
            level = DiagnosticLevel.Info,
            event = DiagnosticEvent.SystemUiRestarted,
            message = "SystemUI restart requested",
        )
        Result.success(Unit)
    } catch (exception: Exception) {
        failureResult(exception)
    }

    private fun failureResult(cause: Exception): Result<Unit> {
        if (cause is InterruptedException) Thread.currentThread().interrupt()
        val failure = RestartFailure(
            cause = cause,
            message = userMessageFor(cause),
            messageResId = userMessageResIdFor(cause),
        )
        AppDiagnostics.logger.report(
            level = DiagnosticLevel.Error,
            event = DiagnosticEvent.SystemUiRestartFailed,
            message = failure.message.orEmpty(),
            cause = cause,
        )
        return Result.failure(failure)
    }

    private fun deliver(result: Result<Unit>, callback: (Result<Unit>) -> Unit) {
        MainThreadCallbacks.dispatch("systemui_restart") { callback(result) }
    }

    private fun restartBlocking() {
        val suCommand = resolveSuCommand()
        val process = ProcessBuilder(suCommand, "-c", restartCommand)
            .redirectErrorStream(true)
            .start()
        try {
            if (!process.waitFor(RESTART_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw TimeoutException("SystemUI restart command timed out")
            }
            // Drain the merged stream without exposing shell output to UI or diagnostics.
            val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
            check(process.exitValue() == 0) {
                "SystemUI restart command exited with ${process.exitValue()}; output=$output"
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun resolveSuCommand(): String {
        val candidates = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/system_ext/bin/su",
            "/product/bin/su",
            "/sbin/su",
            "/su/bin/su",
            "su",
        )
        return candidates.firstOrNull { candidate ->
            candidate == "su" || File(candidate).exists()
        } ?: "su"
    }

    private fun userMessageResIdFor(cause: Exception): Int {
        val detail = cause.message.orEmpty()
        return when {
            cause is TimeoutException -> R.string.restart_error_timeout
            detail.contains("Cannot run program", ignoreCase = true) ||
                detail.contains("error=2", ignoreCase = true) ->
                R.string.restart_error_no_su
            detail.contains("Permission denied", ignoreCase = true) ||
                detail.contains("denied", ignoreCase = true) ->
                R.string.restart_error_denied
            else -> R.string.message_restart_failed_generic
        }
    }

    private fun userMessageFor(cause: Exception): String {
        val detail = cause.message.orEmpty()
        return when {
            cause is TimeoutException -> "SystemUI restart timed out, please check Root authorization prompt"
            detail.contains("Cannot run program", ignoreCase = true) ||
                detail.contains("error=2", ignoreCase = true) ->
                "su command not found, please confirm Root environment is properly loaded"
            detail.contains("Permission denied", ignoreCase = true) ||
                detail.contains("denied", ignoreCase = true) ->
                "Root permission denied, please grant access to Glyph in your Root manager"
            else -> "Unable to execute SystemUI restart command"
        }
    }

    private const val RESTART_TIMEOUT_SECONDS = 10L
}
