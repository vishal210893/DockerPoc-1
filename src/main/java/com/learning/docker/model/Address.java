package com.learning.docker.model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.NotBlank;

/**
 * Represents an address that can be embedded into other JPA entities.
 */
@Embeddable
public class Address {

    @NotBlank(message = "Street is required")
    private String street;

    @NotBlank(message = "City is required")
    private String city;

    @NotBlank(message = "Zip code is required")
    private String zipCode;

    /**
     * Default constructor required by JPA.
     */
    public Address() {
    }

    /**
     * Creates an Address with all fields.
     *
     * @param street  street name (required)
     * @param city    city name (required)
     * @param zipCode postal code (required)
     */
    public Address(String street, String city, String zipCode) {
        this.street = street;
        this.city = city;
        this.zipCode = zipCode;
    }

    // Getters & setters

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    @Override
    public String toString() {
        return "Address{" +
                "street='" + street + '\'' +
                ", city='" + city + '\'' +
                ", zipCode='" + zipCode + '\'' +
                '}';
    }
}
