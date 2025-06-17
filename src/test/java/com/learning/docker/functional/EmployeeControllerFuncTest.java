package com.learning.docker.functional;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.docker.entity.Address;
import com.learning.docker.entity.ContactInfo;
import com.learning.docker.entity.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeControllerFuncTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private Employee employee;

    @BeforeEach
    void setup() {
        employee = new Employee();
        employee.setFirstName("Jane");
        employee.setLastName("Doe");
        employee.setAddress(new Address("Street","City","000"));
        employee.setContactInfo(new ContactInfo("jane@test.com","321"));
        employee.setSkills(Collections.singletonList("Spring"));
    }

    @Test
    void postThenFetch() throws Exception {
        String json = objectMapper.writeValueAsString(employee);
        String response = mockMvc.perform(post("/api/employees")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        Employee created = objectMapper.readValue(response, Employee.class);

        mockMvc.perform(get("/api/employees/" + created.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"));
    }
}
