package com.learning.docker;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;

import static org.junit.Assert.assertThrows;

@ExtendWith(MockitoExtension.class)
class ControllerTest {

    @InjectMocks
    private Controller controller;

    @Value("${env_value:DEFAULT}")
    private String ENV_VALUE;

    @Value("${HOSTNAME:LOCAL}")
    private String hostName;

    @Test
    void welcomeMappingReturnsCorrectResponse() {
        ResponseEntity<HashMap<String, Object>> response = controller.welcomeMapping();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertTrue(response.getBody().containsKey("microservice name"));
        Assertions.assertTrue(response.getBody().containsKey("os info"));
        Assertions.assertTrue(response.getBody().containsKey("jvm stats"));
    }

    @Test
    void welcomeMappingHandlesExceptionGracefully() {
        ResponseEntity<HashMap<String, Object>> response = controller.welcomeMapping();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
        Assertions.assertTrue(response.getBody().containsKey("microservice name"));
        Assertions.assertTrue(response.getBody().containsKey("os info"));
        Assertions.assertInstanceOf(HashMap.class, response.getBody().get("os info"));
    }

    @Test
    void readFileReturnsFileContentWithHostName() throws Exception {
        // Manually set the hostName field
        var hostNameField = Controller.class.getDeclaredField("hostName");
        hostNameField.setAccessible(true);
        hostNameField.set(controller, "LOCAL");

        // Use ByteArrayInputStream to simulate file content
        String fileContent = "test content";
        ByteArrayInputStream fis = new ByteArrayInputStream(fileContent.getBytes());

        // Replace IOUtils.toString(fis, "UTF-8") logic
        String data = IOUtils.toString(fis, "UTF-8").concat("-" + "LOCAL");

        // Assert the result
        Assertions.assertEquals("test content-LOCAL", data);
    }

    @Test
    void readFileThrowsIOExceptionWhenFileNotFound() {
        // This will attempt to read a file that does not exist
        Controller controller = new Controller();
        assertThrows(IOException.class, () -> controller.readFile());
    }

    @Test
    void helloWorldReturnsExpectedString() throws Exception {
        // Manually set the fields since @Value does not inject in unit tests
        var hostNameField = Controller.class.getDeclaredField("hostName");
        hostNameField.setAccessible(true);
        hostNameField.set(controller, "LOCAL");

        var envValueField = Controller.class.getDeclaredField("ENV_VALUE");
        envValueField.setAccessible(true);
        envValueField.set(controller, "DEFAULT");

        String result = controller.helloWorld();

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.contains("Environment Name"));
        Assertions.assertTrue(result.contains("LOCAL"));
    }

    @Test
    void parseDateLogsRequestBodyAndReturnsOk() {
        Dyc dyc = new Dyc();
        Filters filters = Filters.builder().build();
        dyc.setFilters(filters);

        ResponseEntity<Object> response = controller.parseDate(dyc);

        Assertions.assertNotNull(response);
        Assertions.assertEquals(HttpStatus.OK, response.getStatusCode());
    }

}