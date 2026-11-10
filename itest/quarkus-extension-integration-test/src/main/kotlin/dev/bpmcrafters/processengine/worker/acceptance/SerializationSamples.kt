package dev.bpmcrafters.processengine.worker.acceptance

import java.math.BigDecimal
import java.math.BigInteger
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.Arrays
import java.util.Collections
import java.util.Date
import java.util.LinkedList
import java.util.Objects
import java.util.TreeMap
import java.util.TreeSet
import java.util.UUID

/** Construct inside each process; never obtain these collection implementations from JSON. */
object SerializationSamples {
  @JvmStatic
  fun create(): MutableMap<String, Any?> {
    val result = linkedMapOf<String, Any?>()
    // Deliberately exercise Java immutable collections and their serialization proxies, not Kotlin equivalents.
    result["immutable"] = java.util.List.of(java.util.Map.of("a", 1), java.util.Map.of("a", 1, "b", 2),
      java.util.List.of(1), java.util.List.of(1, 2, 3), java.util.Set.of(1), java.util.Set.of(1, 2, 3))
    result["mutable"] = java.util.List.of(HashMap(java.util.Map.of("a", 1)),
      LinkedHashMap(java.util.Map.of("b", 2)), TreeMap(java.util.Map.of("c", 3)),
      ArrayList(java.util.List.of(1)), LinkedList(java.util.List.of(2)), Arrays.asList(1, 2),
      HashSet(java.util.Set.of(1)), LinkedHashSet(java.util.Set.of(2)), TreeSet(java.util.Set.of(3)))
    result["singletons"] = java.util.List.of(Collections.singletonMap("a", 1),
      Collections.singletonList(2), Collections.singleton(3))
    result["unmodifiable"] = java.util.List.of(Collections.unmodifiableMap(HashMap(java.util.Map.of("a", 1))),
      Collections.unmodifiableList(ArrayList(java.util.List.of(2))),
      Collections.unmodifiableList(LinkedList(java.util.List.of(3))),
      Collections.unmodifiableSet(HashSet(java.util.Set.of(4))))
    result["empty"] = java.util.List.of(java.util.Map.of<String, Any?>(), java.util.List.of<Any>(),
      java.util.Set.of<Any>(), Collections.emptyMap<String, Any?>(), Collections.emptyList<Any>(),
      Collections.emptySet<Any>(), emptyMap<String, Any?>(), emptyList<Any>(), emptySet<Any>())
    result["kotlinBuilt"] = kotlinBuilt()
    result["dto"] = WorkerPayload("serialized", WorkerPayload.Details(7, WorkerPayload.State.READY))
    result["scalars"] = java.util.List.of("string", true, 1.toByte(), 2.toShort(), 3, 4L, 5F, 6.0, 'x',
      BigDecimal("123.45"), BigInteger("987654321"), UUID.fromString("00000000-0000-0000-0000-000000000001"),
      Instant.parse("2026-01-01T00:00:00Z"), LocalDate.of(2026, 1, 1), Duration.ofSeconds(30),
      Date.from(Instant.parse("2026-01-01T00:00:00Z")))
    result["null"] = null
    addArraysAndSharedReferences(result)
    return result
  }

  private fun addArraysAndSharedReferences(result: MutableMap<String, Any?>) {
    result["bytes"] = byteArrayOf(1, -2)
    result["shorts"] = shortArrayOf(3, -4)
    val ints = intArrayOf(5, -6)
    result["ints"] = ints
    result["longs"] = longArrayOf(7, -8)
    result["floats"] = floatArrayOf(1.5F, -2.5F)
    result["doubles"] = doubleArrayOf(3.5, -4.5)
    result["chars"] = charArrayOf('a', '\u03bb')
    result["booleans"] = booleanArrayOf(true, false)
    val dtos = arrayOf(result["dto"] as WorkerPayload)
    result["dtoArray"] = dtos
    result["dtoMatrix"] = arrayOf(dtos, dtos)
    result["primitiveMatrix"] = arrayOf(ints, ints)
    val shared = ArrayList(java.util.List.of("shared-reference"))
    result["shared"] = shared
    result["references"] = arrayOf(arrayOf<Any>(shared), arrayOf<Any>(shared))
  }

  @JvmStatic
  fun matches(actual: Map<*, *>?): Boolean = deepEquals(create(), actual) && preservesIdentity(actual!!)

  private fun deepEquals(expected: Any?, actual: Any?): Boolean {
    if (expected is Map<*, *> && actual is Map<*, *>) {
      return expected.keys == actual.keys && expected.all { (key, value) -> deepEquals(value, actual[key]) }
    }
    if (expected is List<*> && actual is List<*>) {
      if (expected.size != actual.size) return false
      return expected.indices.all { deepEquals(expected[it], actual[it]) }
    }
    return Objects.deepEquals(expected, actual)
  }

  @Suppress("UNCHECKED_CAST")
  private fun preservesIdentity(actual: Map<*, *>): Boolean {
    val dtos = actual["dtoArray"] as Array<WorkerPayload>
    val dtoMatrix = actual["dtoMatrix"] as Array<Array<WorkerPayload>>
    val primitiveMatrix = actual["primitiveMatrix"] as Array<IntArray>
    val references = actual["references"] as Array<Array<Any>>
    return dtos[0] === actual["dto"] && dtoMatrix[0] === dtos && dtoMatrix[1] === dtos &&
      primitiveMatrix[0] === actual["ints"] && primitiveMatrix[1] === actual["ints"] &&
      references[0][0] === actual["shared"] && references[1][0] === actual["shared"]
  }

  private fun kotlinBuilt(): List<Any> = java.util.List.of(
    buildMap { put("a", 1) }, buildList { add(2) }, buildSet { add(3) })
}
