package com.learning.docker.controller;

import com.learning.docker.model.Dyc;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.management.Attribute;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.IntStream;

/**
 * Exposes application, system, and utility endpoints.
 */
@RestController
@RequestMapping("/api/v1")
@Slf4j
public class SystemInfoController {

    @Value("${env_value:DEFAULT}")
    private String envValue;

    @Value("${HOSTNAME:LOCAL}")
    private String hostName;

    /**
     * Returns microservice, OS, system, and JVM information.
     */
    @GetMapping(value = "/version", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> getVersionInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("microservice", "DockerPoc-1");
        result.put("version", 1.0);
        result.put("dockerInstance", hostName);
        result.put("javaVersion", System.getProperty("java.version"));
        result.put("springBootVersion", "2.3.4.RELEASE");
        result.put("osInfo", fetchOsInfo());
        result.put("systemInfo", fetchSystemInfo());
        result.put("jvmStats", fetchJvmStats());
        log.debug("Version endpoint called: {}", result);
        return ResponseEntity.ok(result);
    }

    /**
     * Reads and returns the contents of /opt/file/fileTest.txt, suffixed with hostname.
     */
    @GetMapping(value = "/readfile", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> readFile() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/opt/file/fileTest.txt")) {
            String content = IOUtils.toString(in, StandardCharsets.UTF_8);
            return ResponseEntity.ok(content + "-" + hostName);
        }
    }

    /**
     * Returns current environment values.
     */
    @GetMapping(path = "/service", produces = MediaType.TEXT_PLAIN_VALUE)
    public String getEnvironment() {
        return String.format("Environment Name - %s - %s", envValue, hostName);
    }

    /**
     * Generates a descending list of integers from 999 to 100.
     */
    @PostMapping(path = "/numbers", consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Integer>> generateDescendingNumbers(@RequestBody Dyc dyc) {
        log.info("Received Dyc payload: {}", dyc);
        List<Integer> numbers = IntStream.rangeClosed(100, 999)
                .map(i -> 1000 - i)
                .boxed()
                .toList();
        return ResponseEntity.ok(numbers);
    }

    // --- Helpers ----------------------------------------------------------

    private Map<String, String> fetchOsInfo() {
        Map<String, String> osInfo = new HashMap<>();
        String[] cmd = {"/bin/sh", "-c", "cat /etc/*-release"};
        try {
            Process p = Runtime.getRuntime().exec(cmd);
            try (var reader = new Scanner(p.getInputStream())) {
                while (reader.hasNextLine()) {
                    String[] parts = reader.nextLine().split("=", 2);
                    if (parts.length == 2) {
                        osInfo.put(parts[0], parts[1].replace("\"", ""));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to fetch OS info", e);
        }
        return osInfo;
    }

    private Map<String, Object> fetchSystemInfo() {
        Map<String, Object> systemInfo = new LinkedHashMap<>();
        systemInfo.put("availableProcessors", Runtime.getRuntime().availableProcessors());

        try {
            MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            ObjectName on = osBean.getObjectName();
            @SuppressWarnings("unchecked")
            var attrs = mbs.getAttributes(on, new String[]{"TotalPhysicalMemorySize", "FreePhysicalMemorySize"})
                    .asList();
            for (Attribute attr : attrs) {
                long mb = (Long) attr.getValue() / (1024 * 1024);
                systemInfo.put(attr.getName(), mb);
            }
        } catch (Exception e) {
            log.error("Failed to fetch system info", e);
        }

        return systemInfo;
    }

    private Map<String, Object> fetchJvmStats() {
        Map<String, Object> jvm = new LinkedHashMap<>();
        long mb = 1024L * 1024;
        Runtime rt = Runtime.getRuntime();
        jvm.put("maxMemory", rt.maxMemory() / mb);
        jvm.put("totalMemory", rt.totalMemory() / mb);
        jvm.put("freeMemory", rt.freeMemory() / mb);
        return jvm;
    }
}
