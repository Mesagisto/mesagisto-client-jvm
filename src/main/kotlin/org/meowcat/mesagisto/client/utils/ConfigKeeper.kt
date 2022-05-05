package org.meowcat.mesagisto.client.utils

import com.charleskorn.kaml.Yaml
import com.charleskorn.kaml.YamlConfiguration
import kotlinx.serialization.* // ktlint-disable no-wildcard-imports
import org.meowcat.mesagisto.client.Logger
import java.nio.file.Path
import kotlin.io.path.* // ktlint-disable no-wildcard-imports

val YAML = Yaml(configuration = YamlConfiguration(strictMode = false))

class ConfigKeeper<C : Any> (
  val value: @Serializable C,
  private val serializer: KSerializer<C>,
  private val path: Path
) {
  fun save() {
    val str = YAML.encodeToString(serializer, value)
    path.writeText(str)
  }
  companion object {
    @OptIn(InternalSerializationApi::class)
    inline fun <reified T : Any> create(
      path: Path,
      defaultValue: () -> T
    ): ConfigKeeper<T> {
      val value = if (path.exists()) {
        Logger.info { "正在读取配置文件$path" }
        try {
          YAML.decodeFromString(T::class.serializer(), path.readText())
        } catch (_: Throwable) {
          Logger.warn { "读取失败，可能是版本更新导致的." }
          path.moveTo(path.parent.resolve("${path.fileName}.old"), true)
          Logger.warn { "使用默认配置覆盖原配置，原配置已修改成$path.old" }
          val default = defaultValue()
          val defaultStr = YAML.encodeToString(T::class.serializer(), default)
          path.writeText(defaultStr)
          default
        }
      } else {
        Logger.info { "配置文件不存在，新建默认配置 $path" }
        val default = defaultValue()
        val str = YAML.encodeToString(T::class.serializer(), default)
        path.parent.createDirectories()
        path.createFile()
        path.writeText(str)
        default
      }
      return ConfigKeeper(value, T::class.serializer(), path)
    }
  }
}
