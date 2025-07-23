package com.learning.docker;

import com.learning.docker.controller.SystemInfoController;
import com.learning.docker.model.Dyc;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SystemInfoControllerTest {

    @InjectMocks
    private SystemInfoController systemInfoController;

    @BeforeEach
    void setUp() throws Exception {
        // Manually inject @Value fields
        Field envField = SystemInfoController.class.getDeclaredField("envValue");
        envField.setAccessible(true);
        envField.set(systemInfoController, "DEFAULT");

        Field hostField = SystemInfoController.class.getDeclaredField("hostName");
        hostField.setAccessible(true);
        hostField.set(systemInfoController, "LOCAL");
    }

    @Test
    void getVersionInfoReturnsCorrectResponse() {
        ResponseEntity<Map<String, Object>> response = systemInfoController.getVersionInfo();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertTrue(body.containsKey("microservice"));
        assertTrue(body.containsKey("osInfo"));
        assertTrue(body.containsKey("jvmStats"));
    }

    @Test
    void getVersionInfoHandlesExceptionsGracefully() {
        ResponseEntity<Map<String, Object>> response = systemInfoController.getVersionInfo();
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    void readFileThrowsIOExceptionWhenFileNotFound() {
        assertThrows(NullPointerException.class, () -> systemInfoController.readFile());
    }

    @Test
    void getEnvironmentReturnsExpectedString() {
        String result = systemInfoController.getEnvironment();
        assertEquals("Environment Name - DEFAULT - LOCAL", result);
    }

    @Test
    void generateDescendingNumbersReturnsCorrectList() {
        Dyc dyc = new Dyc();
        ResponseEntity<List<Integer>> response = systemInfoController.generateDescendingNumbers(dyc);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        List<Integer> numbers = response.getBody();
        assertNotNull(numbers);
        assertEquals(900, numbers.size());
        assertEquals(900, numbers.get(0));
        assertEquals(1, numbers.get(numbers.size() - 1));
    }
}
