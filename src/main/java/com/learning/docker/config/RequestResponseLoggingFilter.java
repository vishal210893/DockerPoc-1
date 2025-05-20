package com.learning.docker.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

@Component
@Order(1) // Ensure it runs early, but after security filters if you have them and want to log post-auth
public class RequestResponseLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);
    private static final String REQUEST_PREFIX = "=> REQUEST: ";
    private static final String RESPONSE_PREFIX = "<= RESPONSE: ";
    private static final int MAX_PAYLOAD_LENGTH = 10000; // Max characters of payload to log

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {

        if (!(request instanceof HttpServletRequest httpRequest) || !(response instanceof HttpServletResponse httpResponse)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Wrap request and response to allow caching of their bodies
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(httpRequest);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(httpResponse);

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            long timeTaken = System.currentTimeMillis() - startTime;
            logRequest(requestWrapper, timeTaken);
            logResponse(responseWrapper);
            // IMPORTANT: copy the cached response content to the original response's output stream
            responseWrapper.copyBodyToResponse();
        }
    }

    private void logRequest(ContentCachingRequestWrapper request, long timeTaken) {
        StringBuilder msg = new StringBuilder();
        msg.append("\n------------------------- REQUEST START -------------------------\n");
        msg.append(REQUEST_PREFIX).append("ID=").append(request.hashCode()).append("\n"); // Simple request ID
        msg.append(REQUEST_PREFIX).append("Method      : ").append(request.getMethod()).append("\n");
        msg.append(REQUEST_PREFIX).append("URI         : ").append(request.getRequestURI()).append("\n");
        if (request.getQueryString() != null) {
            msg.append(REQUEST_PREFIX).append("QueryString : ").append(request.getQueryString()).append("\n");
        }
        msg.append(REQUEST_PREFIX).append("Client IP   : ").append(request.getRemoteAddr()).append("\n");
        msg.append(REQUEST_PREFIX).append("Headers     : ").append(getHeaders(request)).append("\n");

        byte[] content = request.getContentAsByteArray();
        if (content.length > 0) {
            String contentString = getContentString(content, request.getCharacterEncoding());
            msg.append(REQUEST_PREFIX).append("Body        : \n").append(formatPayload(contentString)).append("\n");
        }
        msg.append(REQUEST_PREFIX).append("Processed in: ").append(timeTaken).append(" ms\n");
        msg.append("-------------------------- REQUEST END --------------------------\n");
        log.info(msg.toString());
    }

    private void logResponse(ContentCachingResponseWrapper response) {
        StringBuilder msg = new StringBuilder();
        msg.append("\n------------------------- RESPONSE START ------------------------\n");
        msg.append(RESPONSE_PREFIX).append("ID=").append(response.hashCode()).append("\n"); // Simple response ID
        msg.append(RESPONSE_PREFIX).append("Status  : ").append(response.getStatus()).append("\n");
        msg.append(RESPONSE_PREFIX).append("Headers : ").append(getHeaders(response)).append("\n");

        byte[] content = response.getContentAsByteArray();
        if (content.length > 0) {
            String contentString = getContentString(content, response.getCharacterEncoding());
            msg.append(RESPONSE_PREFIX).append("Body    : \n").append(formatPayload(contentString)).append("\n");
        }
        msg.append("-------------------------- RESPONSE END --------------------------\n");

        log.info(msg.toString());
    }

    private String getHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            headers.put(headerName, request.getHeader(headerName));
        }
        return headers.toString();
    }

    private String getHeaders(HttpServletResponse response) {
        Map<String, String> headers = new HashMap<>();
        Collection<String> headerNames = response.getHeaderNames();
        for (String headerName : headerNames) {
            headers.put(headerName, response.getHeader(headerName));
        }
        return headers.toString();
    }

    private String getContentString(byte[] content, String characterEncoding) {
        if (content == null || content.length == 0) {
            return "";
        }
        try {
            return new String(content, characterEncoding != null ? characterEncoding : StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            log.warn("Failed to parse payload with encoding '{}', falling back to UTF-8: {}", characterEncoding, e.getMessage());
            return new String(content, StandardCharsets.UTF_8);
        }
    }

    private String formatPayload(String payload) {
        if (payload == null || payload.isEmpty()) {
            return "[EMPTY BODY]";
        }
        // Basic pretty printing for JSON - you might want a more robust JSON library for complex formatting
        if (payload.trim().startsWith("{") || payload.trim().startsWith("[")) {
            try {
                // Attempt basic pretty print for JSON-like structures
                // For robust pretty printing, consider using Jackson's ObjectMapper
                ObjectMapper objectMapper = new ObjectMapper();
                Object json = objectMapper.readValue(payload, Object.class);
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
                // Simple indentation for now:
                //return payload.replaceAll("(?m)^", "  "); // Indent each line
            } catch (Exception e) {
                // Not valid JSON or other issue, log as is (truncated)
            }
        }
        return truncatePayload(payload);
    }

    private String truncatePayload(String payload) {
        if (payload.length() > MAX_PAYLOAD_LENGTH) {
            return payload.substring(0, MAX_PAYLOAD_LENGTH) + "... [TRUNCATED]";
        }
        return payload;
    }
}