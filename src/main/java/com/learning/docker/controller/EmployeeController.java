package com.learning.docker.controller;

import com.learning.docker.model.Employee;
import com.learning.docker.service.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for managing Employee resources.
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;

    /**
     * Retrieve all employees.
     *
     * @return list of all employees
     */
    @GetMapping
    public List<Employee> getAllEmployees() {
        return employeeService.getAllEmployees();
    }

    /**
     * Retrieve an employee by its ID.
     *
     * @param id the employee’s ID
     * @return HTTP 200 with the employee, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable Long id) {
        return employeeService.getEmployeeById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Create a new employee.
     *
     * @param employee the employee to create
     * @return HTTP 201 with the created employee
     */
    @PostMapping
    public ResponseEntity<Employee> createEmployee(@Valid @RequestBody Employee employee) {
        Employee created = employeeService.createEmployee(employee);
        return ResponseEntity.status(201).body(created);
    }

    /**
     * Update an existing employee.
     *
     * @param id      the employee’s ID
     * @param details updated employee data
     * @return HTTP 200 with the updated employee, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Employee> updateEmployee(@PathVariable Long id,
                                                   @Valid @RequestBody Employee details) {
        if (!employeeService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        Employee updated = employeeService.updateEmployee(id, details);
        return ResponseEntity.ok(updated);
    }

    /**
     * Delete an employee by its ID.
     *
     * @param id the employee’s ID
     * @return HTTP 204 if deleted, or 404 if not found
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteEmployee(@PathVariable Long id) {
        if (!employeeService.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        employeeService.deleteEmployee(id);
        return ResponseEntity.noContent().build();
    }
}
