package dev.bpmcrafters.processengine.worker.quarkus.idempotency.jpa.deployment

import java.lang.reflect.Array as ReflectArray
import java.math.BigDecimal
import java.math.BigInteger
import java.time.*
import java.util.Date
import java.util.UUID

/** Explicit bounded support: applications register their own payload types separately. */
internal object SerializationTypes {
  fun names(): Array<String> = valueNames() + arrayNames()

  private fun valueNames(): Array<String> = arrayOf(
    "java.util.HashMap", "java.util.LinkedHashMap", "java.util.TreeMap",
    "java.util.ArrayList", "java.util.LinkedList", "java.util.Arrays\$ArrayList",
    "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet",
    "java.util.Collections\$EmptyMap", "java.util.Collections\$EmptyList",
    "java.util.Collections\$EmptySet", "java.util.Collections\$SingletonMap",
    "java.util.Collections\$SingletonList", "java.util.Collections\$SingletonSet",
    "java.util.Collections\$UnmodifiableCollection", "java.util.Collections\$UnmodifiableList",
    "java.util.Collections\$UnmodifiableRandomAccessList", "java.util.Collections\$UnmodifiableSet",
    "java.util.Collections\$UnmodifiableMap", "java.util.Collections\$UnmodifiableSortedMap",
    "java.util.Collections\$UnmodifiableNavigableMap", "java.util.Collections\$UnmodifiableSortedSet",
    "java.util.Collections\$UnmodifiableNavigableSet",
    "java.util.ImmutableCollections\$Map1", "java.util.ImmutableCollections\$MapN",
    "java.util.ImmutableCollections\$List12", "java.util.ImmutableCollections\$ListN",
    "java.util.ImmutableCollections\$Set12", "java.util.ImmutableCollections\$SetN",
    // Map.of/List.of/Set.of serialize through this writeReplace proxy.
    "java.util.CollSer",
    "kotlin.collections.EmptyMap", "kotlin.collections.EmptyList", "kotlin.collections.EmptySet",
    "kotlin.collections.builders.MapBuilder", "kotlin.collections.builders.ListBuilder",
    "kotlin.collections.builders.SetBuilder", "kotlin.collections.builders.SerializedMap",
    "kotlin.collections.builders.SerializedCollection",
    "java.lang.String", "java.lang.Boolean", "java.lang.Byte", "java.lang.Short",
    "java.lang.Integer", "java.lang.Long", "java.lang.Float", "java.lang.Double",
    "java.lang.Character", "java.lang.Number", "java.math.BigDecimal", "java.math.BigInteger",
    "java.util.UUID", "java.util.Date",
    "java.time.Instant", "java.time.Duration", "java.time.Period", "java.time.LocalDate",
    "java.time.LocalTime", "java.time.LocalDateTime", "java.time.OffsetTime",
    "java.time.OffsetDateTime", "java.time.ZonedDateTime", "java.time.ZoneOffset",
    "java.time.ZoneRegion", "java.time.Year", "java.time.YearMonth", "java.time.MonthDay",
    // java.time values use a serialization delegate rather than their public type.
    "java.time.Ser",
    // ObjectOutputStream writes this standard exception graph when a value is not serializable.
    "java.lang.Throwable", "java.lang.Exception", "java.io.IOException",
    "java.io.ObjectStreamException", "java.io.NotSerializableException", "java.lang.StackTraceElement"
  )

  private fun arrayNames(): Array<String> {
    // JVM descriptors (e.g. [[I and [Ljava.lang.Integer;) match both native configs.
    val components = listOf(
      Boolean::class.javaPrimitiveType!!, Byte::class.javaPrimitiveType!!,
      Short::class.javaPrimitiveType!!, Int::class.javaPrimitiveType!!,
      Long::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!,
      Double::class.javaPrimitiveType!!, Char::class.javaPrimitiveType!!,
      Any::class.java, String::class.java, Boolean::class.javaObjectType,
      Byte::class.javaObjectType, Short::class.javaObjectType, Int::class.javaObjectType,
      Long::class.javaObjectType, Float::class.javaObjectType, Double::class.javaObjectType,
      Char::class.javaObjectType, Number::class.java, BigDecimal::class.java, BigInteger::class.java,
      UUID::class.java, Date::class.java, Instant::class.java, Duration::class.java,
      Period::class.java, LocalDate::class.java, LocalTime::class.java, LocalDateTime::class.java,
      OffsetTime::class.java, OffsetDateTime::class.java, ZonedDateTime::class.java,
      ZoneOffset::class.java, Year::class.java, YearMonth::class.java, MonthDay::class.java,
      StackTraceElement::class.java
    )
    return components.flatMap { component ->
      listOf(ReflectArray.newInstance(component, 0).javaClass.name,
        ReflectArray.newInstance(component, 0, 0).javaClass.name)
    }.toTypedArray()
  }
}
