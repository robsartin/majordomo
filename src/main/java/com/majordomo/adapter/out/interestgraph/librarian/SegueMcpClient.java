package com.majordomo.adapter.out.interestgraph.librarian;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A minimal MCP client over Segue's Streamable HTTP transport.
 *
 * <p>Only what Librarian needs: initialise a session, then call tools. Segue is
 * a local, single-user server (it binds to loopback and has no authentication),
 * so there is no reconnection, no server-initiated stream, and no notification
 * handling here — adding them would be code with no caller.
 *
 * <p>Deliberately sends no {@code Origin} header. Segue refuses a non-loopback
 * origin to close DNS rebinding, and treats an absent one as an ordinary
 * non-browser client, which is exactly what this is.
 */
public class SegueMcpClient {

    private static final Logger LOG = LoggerFactory.getLogger(SegueMcpClient.class);
    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final RestClient http;
    private final String endpoint;
    private final AtomicLong nextId = new AtomicLong(1);

    private volatile String sessionId;

    /**
     * Constructs the client.
     *
     * @param http     the HTTP client
     * @param endpoint Segue's MCP endpoint, e.g. {@code http://127.0.0.1:8080/mcp}
     */
    public SegueMcpClient(RestClient http, String endpoint) {
        this.http = http;
        this.endpoint = endpoint;
    }

    /**
     * Calls one tool, initialising the session first if needed.
     *
     * @param toolName  the tool to call
     * @param arguments its arguments
     * @return the tool's text content, joined
     * @throws InterestGraphException if the call fails or the tool reports an error
     */
    public String callTool(String toolName, Map<String, Object> arguments) {
        ensureSession();
        var params = new LinkedHashMap<String, Object>();
        params.put("name", toolName);
        params.put("arguments", arguments);

        JsonNode result = rpc("tools/call", params).path("result");
        if (result.path("isError").asBoolean(false)) {
            throw new InterestGraphException(
                    "segue tool " + toolName + " reported: " + textOf(result), null);
        }
        return textOf(result);
    }

    private synchronized void ensureSession() {
        if (sessionId != null) {
            return;
        }
        var params = new LinkedHashMap<String, Object>();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.put("capabilities", Map.of());
        params.put("clientInfo", Map.of("name", "majordomo-librarian", "version", "1.0"));

        ResponseEntity<String> response = post(envelope("initialize", params));
        readResult(response.getBody());
        String returned = response.getHeaders().getFirst(SESSION_HEADER);
        this.sessionId = returned;

        // The initialized notification is fire-and-forget; a server that does
        // not need it simply accepts it.
        try {
            post(notification());
        } catch (RuntimeException e) {
            LOG.debug("segue did not accept the initialized notification: {}", e.toString());
        }
    }

    private JsonNode rpc(String method, Map<String, Object> params) {
        return readResult(post(envelope(method, params)).getBody());
    }

    private ResponseEntity<String> post(String body) {
        try {
            return http.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Accept", "application/json, text/event-stream")
                    .headers(h -> {
                        if (sessionId != null) {
                            h.set(SESSION_HEADER, sessionId);
                        }
                    })
                    .body(body)
                    .retrieve()
                    .toEntity(String.class);
        } catch (RuntimeException e) {
            throw new InterestGraphException("segue is unreachable at " + endpoint, e);
        }
    }

    private JsonNode readResult(String body) {
        JsonNode node;
        try {
            node = MAPPER.readTree(unwrapEventStream(body));
        } catch (Exception e) {
            throw new InterestGraphException("Could not read segue's response", e);
        }
        if (node.has("error")) {
            throw new InterestGraphException(
                    "segue returned an error: " + node.path("error").path("message").asText(), null);
        }
        return node;
    }

    /**
     * Streamable HTTP may answer either as JSON or as a single SSE event. Both
     * carry the same JSON-RPC envelope, so the data line is unwrapped rather
     * than requiring a second code path.
     */
    private String unwrapEventStream(String body) {
        if (body == null) {
            return "";
        }
        String trimmed = body.trim();
        if (!trimmed.startsWith("event:") && !trimmed.startsWith("data:")) {
            return trimmed;
        }
        return trimmed.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).trim())
                .reduce("", String::concat);
    }

    private String envelope(String method, Map<String, Object> params) {
        var message = new LinkedHashMap<String, Object>();
        message.put("jsonrpc", "2.0");
        message.put("id", String.valueOf(nextId.getAndIncrement()));
        message.put("method", method);
        message.put("params", params);
        return write(message);
    }

    private String notification() {
        var message = new LinkedHashMap<String, Object>();
        message.put("jsonrpc", "2.0");
        message.put("method", "notifications/initialized");
        return write(message);
    }

    private String write(Map<String, Object> message) {
        try {
            return MAPPER.writeValueAsString(message);
        } catch (Exception e) {
            throw new InterestGraphException("Could not serialise an MCP message", e);
        }
    }

    private String textOf(JsonNode result) {
        var text = new StringBuilder();
        for (JsonNode block : result.path("content")) {
            if ("text".equals(block.path("type").asText())) {
                text.append(block.path("text").asText());
            }
        }
        return text.toString();
    }
}
