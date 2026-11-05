package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.deployment

import java.util.regex.Pattern

/** A portable, slash-separated Ant pattern, independent of the host filesystem. */
class ClasspathResourcePattern(value: String) {
  private val all = value.startsWith("classpath*:")
  private val regex: Pattern
  private val lookupPath: String
  private val wildcard: Boolean

  init {
    val prefix = if (all) "classpath*:" else "classpath:"
    require(value.startsWith(prefix)) { "Deployment patterns require classpath: or classpath*: $value" }
    val path = value.substring(prefix.length).replaceFirst(Regex("^/+"), "")
    require(path.isNotEmpty() && !path.contains('\\') && !path.contains(':') && ".." !in path.split('/')) {
      "Invalid deployment classpath pattern: $value"
    }
    regex = Pattern.compile(toRegex(path))
    wildcard = path.contains('*') || path.contains('?')
    val firstWildcard = minOf(path.indexOf('*').takeIf { it >= 0 } ?: path.length,
      path.indexOf('?').takeIf { it >= 0 } ?: path.length)
    lookupPath = if (wildcard) path.substring(0, path.lastIndexOf('/', firstWildcard) + 1) else path
  }

  fun all() = all
  fun matches(name: String) = regex.matcher(name).matches()
  fun lookupPath() = lookupPath
  fun wildcard() = wildcard

  private fun toRegex(glob: String): String {
    val regex = StringBuilder("^")
    var index = 0
    while (index < glob.length) {
      val character = glob[index]
      if (character == '*' && index + 1 < glob.length && glob[index + 1] == '*') {
        index++
        if (index + 1 < glob.length && glob[index + 1] == '/') {
          regex.append("(?:.*/)?")
          index++
        } else regex.append(".*")
      } else if (character == '*') regex.append("[^/]*")
      else if (character == '?') regex.append("[^/]")
      else regex.append(Pattern.quote(character.toString()))
      index++
    }
    return regex.append('$').toString()
  }
}
