package com.minfinrobot.data.log

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class LogLevel { INFO, WARN, ERROR }

data class LogEntry(
    val timestamp: LocalDateTime,
    val level: LogLevel,
    val message: String
) {
    fun format(): String {
        val ts = timestamp.format(FORMATTER)
        val lvl = when (level) {
            LogLevel.INFO -> "I"
            LogLevel.WARN -> "W"
            LogLevel.ERROR -> "E"
        }
        return "[$ts] $lvl  $message"
    }

    companion object {
        private val FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
    }
}

/**
 * In-memory ring buffer на 500 записей. По требованию пользователя пишем только:
 *  - старт/стоп робота
 *  - результат каждого опроса Минфина (кратко, чтобы видеть что процесс идёт)
 *  - ошибки сети, парсинга, API
 *  - найденную публикацию, отправленные ордера
 */
object LogStore {
    private const val MAX_ENTRIES = 500

    private val _entries = MutableStateFlow<List<LogEntry>>(emptyList())
    val entries: StateFlow<List<LogEntry>> = _entries.asStateFlow()

    fun info(message: String) = add(LogLevel.INFO, message)
    fun warn(message: String) = add(LogLevel.WARN, message)
    fun error(message: String) = add(LogLevel.ERROR, message)

    @Synchronized
    private fun add(level: LogLevel, message: String) {
        val entry = LogEntry(LocalDateTime.now(), level, message)
        val current = _entries.value
        _entries.value = if (current.size >= MAX_ENTRIES) {
            current.drop(current.size - MAX_ENTRIES + 1) + entry
        } else {
            current + entry
        }
    }

    fun clear() {
        _entries.value = emptyList()
    }
}
