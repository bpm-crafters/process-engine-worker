package dev.bpmcrafters.processengine.worker.quarkus.deploymentresource.deployment

import io.quarkus.paths.PathTree
import java.io.IOException
import java.io.UncheckedIOException
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.util.TreeMap

class DeploymentResourceScanner {
  data class Source(val tree: PathTree, val removed: Set<String>, val excludedRoots: Set<URI> = emptySet()) {
    fun tree() = tree
    fun removed() = removed
    fun excludedRoots() = excludedRoots

    companion object {
      fun excludingRoots(tree: PathTree, removed: Set<String>, excluded: Collection<Path>): Source {
        val locations = HashSet<URI>()
        for (root in excluded) {
          locations.add(root.toUri())
          // Archive visits use the opened zip filesystem root, rather than the advertised JAR path.
          if (Files.isRegularFile(root)) locations.add(URI.create("jar:${root.toUri()}!/"))
        }
        return Source(tree, removed, java.util.Set.copyOf(locations))
      }
    }
  }

  data class Resource(val originalPath: String, val generatedPath: String, val content: ByteArray) {
    fun originalPath() = originalPath
    fun generatedPath() = generatedPath
    fun content() = content
  }

  fun scan(sources: List<Source>, patterns: List<ClasspathResourcePattern>): List<Resource> {
    val resources = ArrayList<Resource>()
    val resolved = HashSet<ClasspathResourcePattern>()
    for (source in sources) {
      for (root in read(source, patterns)) {
        // A classpath: wildcard resolves its fixed directory once, even when it has no matches.
        val active = patterns.filter { it.all() || (it !in resolved && root.containsLookup(it)) }
        resolved.addAll(active)
        root.files.forEach { (name, bytes) ->
          if (active.none { it.matches(name) }) return@forEach
          val filename = name.substringAfterLast('/')
          resources.add(Resource(name, "$PREFIX${resources.size}/$filename", bytes))
        }
      }
    }
    return java.util.List.copyOf(resources)
  }

  private fun read(source: Source, patterns: List<ClasspathResourcePattern>): List<Root> {
    val roots = LinkedHashMap<Path, Root>()
    source.tree.walk { visit ->
      if (visit.root.toUri() in source.excludedRoots) return@walk
      val root = roots.computeIfAbsent(visit.root) { Root() }
      val name = visit.getRelativePath("/")
      if (Files.isDirectory(visit.path)) root.directories.add(if (name.isEmpty()) "" else "$name/")
      if (!Files.isRegularFile(visit.path) || name in source.removed || patterns.none { it.matches(name) }) return@walk
      try {
        root.files[name] = Files.readAllBytes(visit.path)
      } catch (failure: IOException) {
        throw UncheckedIOException("Cannot read deployment resource $name", failure)
      }
    }
    return java.util.List.copyOf(roots.values)
  }

  private class Root {
    val directories = HashSet<String>()
    val files = TreeMap<String, ByteArray>()
    fun containsLookup(pattern: ClasspathResourcePattern) =
      if (pattern.wildcard()) pattern.lookupPath() in directories else pattern.lookupPath() in files
  }

  companion object {
    const val PREFIX = "META-INF/process-engine-worker/deployment/"
  }
}
