package com.learning.docker.e2e;

import com.learning.docker.entity.Address;
import com.learning.docker.entity.ContactInfo;
import com.learning.docker.entity.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class EmployeeControllerE2ETest {

    @LocalServerPort
    private int port;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createAndGetEmployeeFlow() {
        Employee employee = new Employee();
        employee.setFirstName("John");
        employee.setLastName("Doe");
        employee.setAddress(new Address("Street 1","City","12345"));
        employee.setContactInfo(new ContactInfo("john@test.com","123"));
        employee.setSkills(Collections.singletonList("Java"));

        ResponseEntity<Employee> postResponse = restTemplate.postForEntity("http://localhost:" + port + "/api/employees", employee, Employee.class);
        assertThat(postResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Long id = postResponse.getBody().getId();

        Employee fetched = restTemplate.getForObject("http://localhost:" + port + "/api/employees/"+id, Employee.class);
        assertThat(fetched.getFirstName()).isEqualTo("John");

        ResponseEntity<Employee[]> listResp = restTemplate.getForEntity("http://localhost:" + port + "/api/employees", Employee[].class);
        assertThat(listResp.getBody()).hasSize(1);

        restTemplate.delete("http://localhost:" + port + "/api/employees/"+id);
        ResponseEntity<Employee[]> empty = restTemplate.getForEntity("http://localhost:" + port + "/api/employees", Employee[].class);
        assertThat(empty.getBody()).isEmpty();
    }
}
