package com.ejden.app

import android.content.Context
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Garde le détail de la dernière erreur brutale, pour pouvoir la consulter ensuite. */
object CrashLog {
    private const val FILE_NAME = "last-crash.txt"

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        val file = File(context.filesDir, FILE_NAME)

        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val writer = StringWriter()
                error.printStackTrace(PrintWriter(writer))
                val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                file.writeText(("$stamp\nFil : ${thread.name}\n\n$writer").take(20000))
            } catch (e: Exception) {
                // rien de plus à faire
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun read(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        return try {
            if (file.isFile) file.readText() else null
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE_NAME).delete()
        } catch (e: Exception) {
            // rien de plus à faire
        }
    }
}
