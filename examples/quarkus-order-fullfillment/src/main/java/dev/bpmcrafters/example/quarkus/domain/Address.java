package dev.bpmcrafters.example.quarkus.domain;

public record Address(String streetLine, String zipCode, String city, String country) {
  public Address {
    Validation.text(streetLine);
    Validation.text(zipCode);
    Validation.text(city);
    Validation.text(country);
  }
}
