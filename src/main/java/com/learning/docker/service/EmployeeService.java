package com.learning.docker.service;

import com.learning.docker.entity.Employee;

import java.util.List;
import java.util.Optional;

/**
 * Service layer API for managing Employee entities.
 */
public interface EmployeeService {

    /**
     * Retrieve all employees.
     *
     * @return list of all employees
     */
    List<Employee> getAllEmployees();

    /**
     * Find an employee by its ID.
     *
     * @param id the employee’s ID
     * @return an Optional containing the employee if found, or empty otherwise
     */
    Optional<Employee> getEmployeeById(Long id);

    /**
     * Create a new employee.
     *
     * @param employee the employee to create
     * @return the created employee
     */
    Employee createEmployee(Employee employee);

    /**
     * Update an existing employee.
     *
     * @param id      the ID of the employee to update
     * @param details the new data for the employee
     * @return the updated employee
     */
    Employee updateEmployee(Long id, Employee details);

    /**
     * Delete an employee by its ID.
     *
     * @param id the ID of the employee to delete
     */
    void deleteEmployee(Long id);

    /**
     * Check if an employee exists by its ID.
     *
     * @param id the employee’s ID
     * @return true if an employee with the given ID exists, false otherwise
     */
    boolean existsById(Long id);
}
