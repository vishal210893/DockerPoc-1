package com.learning.docker.model;

import jakarta.persistence.Embeddable;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Embeddable entity representing contact details.
 */
@Embeddable
public class ContactInfo {

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    @NotBlank(message = "Phone number is required")
    private String phone;

    /**
     * Default constructor required by JPA.
     */
    public ContactInfo() {
    }

    /**
     * Creates contact info with specified email and phone.
     *
     * @param email valid email address (required)
     * @param phone phone number (required)
     */
    public ContactInfo(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }

    // Getters & setters

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    @Override
    public String toString() {
        return "ContactInfo{" +
                "email='" + email + '\'' +
                ", phone='" + phone + '\'' +
                '}';
    }
}
