package dev.bpmcrafters.example.quarkus.domain;

public final class Validation {
  private Validation() { }

  public static <T> T required(T value, String name) {
    if (value == null) throw new IllegalArgumentException(name + " required");
    return value;
  }

  public static String text(String value) {
    if (required(value, "Text").isBlank()) throw new IllegalArgumentException("Non-blank text required");
    return value;
  }
}
