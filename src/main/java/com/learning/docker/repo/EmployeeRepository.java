package com.learning.docker.repo;

import com.learning.docker.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for managing {@link Employee} entities.
 * <p>
 * Exposes standard CRUD operations and count:
 * - {@link #findAll()}
 * - {@link #findById(Long)}
 * - {@link #save(Employee)}
 * - {@link #deleteById(Long)}
 * - {@link #existsById(Long)}
 * - {@link #count()}
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Retrieve all employees.
     *
     * @return list of all {@link Employee} entities
     */
    @Override
    List<Employee> findAll();

    /**
     * Find an employee by its ID.
     *
     * @param id the employee’s ID
     * @return an {@link Optional} containing the employee if found
     */
    @Override
    Optional<Employee> findById(Long id);

    /**
     * Save (create or update) an employee.
     *
     * @param employee the employee to save
     * @return the saved {@link Employee}
     */
    @Override
    Employee save(Employee employee);

    /**
     * Delete the employee with the given ID.
     *
     * @param id the employee’s ID
     */
    @Override
    void deleteById(Long id);

    /**
     * Check whether an employee exists by its ID.
     *
     * @param id the employee’s ID
     * @return true if an employee with the given ID exists
     */
    @Override
    boolean existsById(Long id);

    /**
     * Count the total number of employees.
     *
     * @return the number of {@link Employee} records
     */
    @Override
    long count();
}
