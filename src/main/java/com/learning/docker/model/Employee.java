package com.learning.docker.model;

import jakarta.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

// @Entity marks this class as a JPA entity, mapping to a database table
@Entity
// @Table(name = "employees") // Uncomment to specify custom table name
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    // @Id marks the primary key
    @Id
    // @GeneratedValue configures the primary key generation strategy
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Use IDENTITY for auto-increment
    private Long id;

    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    // @Embedded means the fields of Address will be columns in the Employee table
    @Embedded
    @NotNull(message = "Address is required")
    @Valid // Validate the nested Address object
    private Address address;

    // @Embedded means the fields of ContactInfo will be columns in the Employee table
    @Embedded
    @NotNull(message = "Contact info is required")
    @Valid // Validate the nested ContactInfo object
    private ContactInfo contactInfo;

    // @ElementCollection is used for collections of basic types or embeddables
    @ElementCollection // Creates a join table (e.g., employee_skills)
    @CollectionTable(name = "employee_skills", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "skill") // Customize the column name in the join table
    @Size(min = 1, message = "Employee must have at least one skill") // Ensure at least one skill
    private List<String> skills = new ArrayList<>(); // Initialize to avoid NPE
}
