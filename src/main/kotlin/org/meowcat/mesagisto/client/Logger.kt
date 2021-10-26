package org.meowcat.mesagisto.client
enum class LogLevel {
  TRACE, DEBUG, INFO, WARN, ERROR
}
interface ILogger {
  fun log(level: LogLevel, msg: String)
}

@Suppress("NOTHING_TO_INLINE", "unused")
object Logger {
  var level: LogLevel = LogLevel.TRACE
  var provider: ILogger? = null

  inline fun trace(msg: () -> String) {
    if (level >= LogLevel.TRACE) {
      provider?.log(LogLevel.TRACE, msg()) ?: println(msg())
    }
  }

  inline fun debug(msg: () -> String) {
    if (level >= LogLevel.DEBUG) {
      provider?.log(LogLevel.DEBUG, msg()) ?: println(msg())
    }
  }

  inline fun info(msg: () -> String) {
    if (level >= LogLevel.INFO) {
      provider?.log(LogLevel.INFO, msg()) ?: println(msg())
    }
  }

  inline fun warn(msg: () -> String) {
    if (level >= LogLevel.WARN) {
      provider?.log(LogLevel.WARN, msg()) ?: println(msg())
    }
  }

  inline fun error(msg: () -> String) {
    if (level >= LogLevel.ERROR) {
      provider?.log(LogLevel.ERROR, msg()) ?: println(msg())
    }
  }
  inline fun error(e: Throwable) {
    if (level >= LogLevel.ERROR) {
      provider?.log(LogLevel.ERROR, "Error ${e.message} \n ${e.stackTrace}")
        ?: println("${e.message} \n ${e.stackTrace}")
    }
  }
}
