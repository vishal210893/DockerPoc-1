package com.learning.docker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.docker.controller.EmployeeController;
import com.learning.docker.entity.Address;
import com.learning.docker.entity.ContactInfo;
import com.learning.docker.entity.Employee;
import com.learning.docker.service.EmployeeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EmployeeController.class) // Focus only on EmployeeController
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean// Mocks the EmployeeService dependency
    private EmployeeService employeeService;

    @Autowired
    private ObjectMapper objectMapper; // For JSON serialization/deserialization

    private Employee employee1;
    private Employee employee2;
    private Address address1, address2;
    private ContactInfo contactInfo1, contactInfo2;

    @BeforeEach
    void setUp() {
        // Initialize common test data
        address1 = new Address("123 Main St", "Anytown", "12345");
        contactInfo1 = new ContactInfo("john.doe@example.com", "555-0101");
        employee1 = new Employee();
        employee1.setId(1L);
        employee1.setFirstName("John");
        employee1.setLastName("Doe");
        employee1.setAddress(address1);
        employee1.setContactInfo(contactInfo1);
        employee1.setSkills(Arrays.asList("Java", "Spring"));

        address2 = new Address("456 Oak Ave", "Otherville", "67890");
        contactInfo2 = new ContactInfo("jane.smith@example.com", "555-0202");
        employee2 = new Employee();
        employee2.setId(2L);
        employee2.setFirstName("Jane");
        employee2.setLastName("Smith");
        employee2.setAddress(address2);
        employee2.setContactInfo(contactInfo2);
        employee2.setSkills(List.of("Python"));
    }

    @Test
    void getAllEmployees_shouldReturnListOfEmployees() throws Exception {
        // Given
        List<Employee> employees = Arrays.asList(employee1, employee2);
        given(employeeService.getAllEmployees()).willReturn(employees);

        // When
        ResultActions resultActions = mockMvc.perform(get("/api/employees"));

        // Then
        resultActions.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id", is(employee1.getId().intValue())))
                .andExpect(jsonPath("$[0].firstName", is(employee1.getFirstName())))
                .andExpect(jsonPath("$[0].address.street", is(employee1.getAddress().getStreet())))
                .andExpect(jsonPath("$[0].contactInfo.email", is(employee1.getContactInfo().getEmail())))
                .andExpect(jsonPath("$[0].skills[0]", is("Java")))
                .andExpect(jsonPath("$[1].id", is(employee2.getId().intValue())))
                .andExpect(jsonPath("$[1].firstName", is(employee2.getFirstName())));

        verify(employeeService).getAllEmployees();
    }

    @Test
    void getAllEmployees_shouldReturnEmptyListWhenNoEmployees() throws Exception {
        // Given
        given(employeeService.getAllEmployees()).willReturn(Collections.emptyList());

        // When
        ResultActions resultActions = mockMvc.perform(get("/api/employees"));

        // Then
        resultActions.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(0)));

        verify(employeeService).getAllEmployees();
    }

    @Test
    void getEmployeeById_shouldReturnEmployeeWhenFound() throws Exception {
        // Given
        Long employeeId = 1L;
        given(employeeService.getEmployeeById(employeeId)).willReturn(Optional.of(employee1));

        // When
        ResultActions resultActions = mockMvc.perform(get("/api/employees/{id}", employeeId));

        // Then
        resultActions.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(employee1.getId().intValue())))
                .andExpect(jsonPath("$.firstName", is(employee1.getFirstName())))
                .andExpect(jsonPath("$.address.city", is(employee1.getAddress().getCity())))
                .andExpect(jsonPath("$.contactInfo.phone", is(employee1.getContactInfo().getPhone())))
                .andExpect(jsonPath("$.skills", hasSize(2)))
                .andExpect(jsonPath("$.skills[1]", is("Spring")));

        verify(employeeService).getEmployeeById(employeeId);
    }

    @Test
    void getEmployeeById_shouldReturnNotFoundWhenEmployeeDoesNotExist() throws Exception {
        // Given
        Long employeeId = 99L;
        given(employeeService.getEmployeeById(employeeId)).willReturn(Optional.empty());

        // When
        ResultActions resultActions = mockMvc.perform(get("/api/employees/{id}", employeeId));

        // Then
        resultActions.andExpect(status().isNotFound());

        verify(employeeService).getEmployeeById(employeeId);
    }

    @Test
    void createEmployee_shouldReturnCreatedEmployee() throws Exception {
        // Given
        Address newAddress = new Address("789 Pine Ln", "Newcity", "11223");
        ContactInfo newContactInfo = new ContactInfo("new.employee@example.com", "555-0303");
        Employee newEmployeeToSave = new Employee();
        newEmployeeToSave.setFirstName("New");
        newEmployeeToSave.setLastName("Employee");
        newEmployeeToSave.setAddress(newAddress);
        newEmployeeToSave.setContactInfo(newContactInfo);
        newEmployeeToSave.setSkills(List.of("Communication"));


        Employee savedEmployee = new Employee();
        savedEmployee.setId(3L);
        savedEmployee.setFirstName("New");
        savedEmployee.setLastName("Employee");
        savedEmployee.setAddress(newAddress);
        savedEmployee.setContactInfo(newContactInfo);
        savedEmployee.setSkills(List.of("Communication"));

        given(employeeService.createEmployee(any(Employee.class))).willReturn(savedEmployee);

        // When
        ResultActions resultActions = mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newEmployeeToSave)));

        // Then
        resultActions.andExpect(status().isCreated()) // HTTP 201
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(savedEmployee.getId().intValue())))
                .andExpect(jsonPath("$.firstName", is(savedEmployee.getFirstName())))
                .andExpect(jsonPath("$.address.street", is(newAddress.getStreet())))
                .andExpect(jsonPath("$.contactInfo.email", is(newContactInfo.getEmail())))
                .andExpect(jsonPath("$.skills[0]", is("Communication")));

        verify(employeeService).createEmployee(any(Employee.class));
    }

    @Test
    void updateEmployee_shouldReturnUpdatedEmployeeWhenFound() throws Exception {
        // Given
        Long employeeId = 1L;
        Address updatedAddress = new Address("123 Main St Updated", "Anytown Updated", "12345U");
        ContactInfo updatedContactInfo = new ContactInfo("john.doe.updated@example.com", "555-0101U");
        Employee updatedDetails = new Employee();
        updatedDetails.setFirstName("John Updated");
        updatedDetails.setLastName("Doe Updated");
        updatedDetails.setAddress(updatedAddress);
        updatedDetails.setContactInfo(updatedContactInfo);
        updatedDetails.setSkills(List.of("Java Updated", "Cloud"));

        Employee returnedUpdatedEmployee = new Employee();
        returnedUpdatedEmployee.setId(employeeId);
        returnedUpdatedEmployee.setFirstName("John Updated");
        returnedUpdatedEmployee.setLastName("Doe Updated");
        returnedUpdatedEmployee.setAddress(updatedAddress);
        returnedUpdatedEmployee.setContactInfo(updatedContactInfo);
        returnedUpdatedEmployee.setSkills(List.of("Java Updated", "Cloud"));

        given(employeeService.existsById(employeeId)).willReturn(true);
        given(employeeService.updateEmployee(eq(employeeId), any(Employee.class))).willReturn(returnedUpdatedEmployee);

        // When
        ResultActions resultActions = mockMvc.perform(put("/api/employees/{id}", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedDetails)));

        // Then
        resultActions.andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id", is(employeeId.intValue())))
                .andExpect(jsonPath("$.firstName", is(updatedDetails.getFirstName())))
                .andExpect(jsonPath("$.address.city", is(updatedAddress.getCity())))
                .andExpect(jsonPath("$.contactInfo.phone", is(updatedContactInfo.getPhone())))
                .andExpect(jsonPath("$.skills[1]", is("Cloud")));

        verify(employeeService).existsById(employeeId);
        verify(employeeService).updateEmployee(eq(employeeId), any(Employee.class));
    }

    @Test
    void updateEmployee_shouldReturnNotFoundWhenEmployeeDoesNotExist() throws Exception {
        // Given
        Long employeeId = 99L;
        Address dummyAddress = new Address("N/A", "N/A", "N/A");
        ContactInfo dummyContactInfo = new ContactInfo("na@na.com", "000");
        Employee updatedDetails = new Employee();
        updatedDetails.setId(employeeId);
        updatedDetails.setFirstName("Non Existent");
        updatedDetails.setLastName("Employee");
        updatedDetails.setAddress(dummyAddress); // Need to provide valid nested objects for @Valid
        updatedDetails.setContactInfo(dummyContactInfo);
        updatedDetails.setSkills(List.of("Skill")); // And skills for @Size(min=1)

        given(employeeService.existsById(employeeId)).willReturn(false);

        // When
        ResultActions resultActions = mockMvc.perform(put("/api/employees/{id}", employeeId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(updatedDetails)));

        // Then
        resultActions.andExpect(status().isNotFound());

        verify(employeeService).existsById(employeeId);
    }

    @Test
    void deleteEmployee_shouldReturnNoContentWhenEmployeeExists() throws Exception {
        // Given
        Long employeeId = 1L;
        given(employeeService.existsById(employeeId)).willReturn(true);
        doNothing().when(employeeService).deleteEmployee(employeeId); // For void methods

        // When
        ResultActions resultActions = mockMvc.perform(delete("/api/employees/{id}", employeeId));

        // Then
        resultActions.andExpect(status().isNoContent()); // HTTP 204

        verify(employeeService).existsById(employeeId);
        verify(employeeService).deleteEmployee(employeeId);
    }

    @Test
    void deleteEmployee_shouldReturnNotFoundWhenEmployeeDoesNotExist() throws Exception {
        // Given
        Long employeeId = 99L;
        given(employeeService.existsById(employeeId)).willReturn(false);

        // When
        ResultActions resultActions = mockMvc.perform(delete("/api/employees/{id}", employeeId));

        // Then
        resultActions.andExpect(status().isNotFound());

        verify(employeeService).existsById(employeeId);
    }
}