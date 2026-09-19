package com.majordomo.adapter.out.interestgraph.librarian;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SegueMcpClientTest {

    private MockWebServer server;
    private SegueMcpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(2).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(2).toMillis());
        client = new SegueMcpClient(
                RestClient.builder().requestFactory(factory).build(),
                server.url("/mcp").toString());
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    /** initialize is followed by the MCP-required initialized notification. */
    private void enqueueHandshake() {
        enqueueInitialize();
        server.enqueue(new MockResponse().setResponseCode(202));
    }

    private void enqueueInitialize() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setHeader("Mcp-Session-Id", "session-abc")
                .setBody("""
                        {"jsonrpc":"2.0","id":"1","result":{"protocolVersion":"2025-06-18",
                         "capabilities":{"tools":{}},"serverInfo":{"name":"segue","version":"1.0"}}}
                        """));
    }

    private void enqueueOk() {
        server.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"jsonrpc":"2.0","id":"2","result":{"isError":false,
                         "content":[{"type":"text","text":"ok"}]}}
                        """));
    }

    @Test
    void callTool_initialisesTheSessionBeforeTheFirstCall() throws Exception {
        enqueueHandshake();
        enqueueOk();

        client.callTool("add_entity", Map.of("qid", "Q7000573"));

        RecordedRequest init = server.takeRequest();
        assertThat(init.getBody().readUtf8()).contains("\"method\":\"initialize\"");
    }

    @Test
    void callTool_sendsTheSessionIdReturnedByInitialize() throws Exception {
        enqueueHandshake();
        enqueueOk();

        client.callTool("add_entity", Map.of("qid", "Q7000573"));

        server.takeRequest();   // initialize
        server.takeRequest();   // initialized notification
        RecordedRequest call = server.takeRequest();
        assertThat(call.getHeader("Mcp-Session-Id")).isEqualTo("session-abc");
    }

    @Test
    void callTool_sendsToolNameAndArguments() throws Exception {
        enqueueHandshake();
        enqueueOk();

        client.callTool("note_affinity", Map.of("qid", "Q1", "rating", 4));

        server.takeRequest();   // initialize
        server.takeRequest();   // initialized notification
        String body = server.takeRequest().getBody().readUtf8();
        assertThat(body).contains("\"method\":\"tools/call\"");
        assertThat(body).contains("\"name\":\"note_affinity\"");
        assertThat(body).contains("\"qid\":\"Q1\"");
        assertThat(body).contains("\"rating\":4");
    }

    @Test
    void callTool_reusesAnEstablishedSessionRatherThanReinitialising() throws Exception {
        enqueueHandshake();
        enqueueOk();
        enqueueOk();

        client.callTool("add_entity", Map.of("qid", "Q1"));
        client.callTool("add_entity", Map.of("qid", "Q2"));

        assertThat(server.getRequestCount())
                .as("initialize + initialized notification + two tool calls")
                .isEqualTo(4);
    }

    @Test
    void callTool_sendsNoOriginHeaderSoSegueDoesNotRefuseIt() throws Exception {
        enqueueHandshake();
        enqueueOk();

        client.callTool("add_entity", Map.of("qid", "Q1"));

        assertThat(server.takeRequest().getHeader("Origin"))
                .as("segue refuses a non-loopback Origin; an MCP client sends none")
                .isNull();
    }

    @Test
    void callTool_throwsWhenTheToolReportsAnError() {
        enqueueHandshake();
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"jsonrpc":"2.0","id":"2","result":{"isError":true,
                         "content":[{"type":"text","text":"Wikidata has no entity Q999"}]}}
                        """));

        assertThatThrownBy(() -> client.callTool("add_entity", Map.of("qid", "Q999")))
                .isInstanceOf(InterestGraphException.class)
                .hasMessageContaining("Wikidata has no entity");
    }

    @Test
    void callTool_throwsOnAJsonRpcError() {
        enqueueHandshake();
        server.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {"jsonrpc":"2.0","id":"2","error":{"code":-32602,"message":"Unknown tool"}}
                        """));

        assertThatThrownBy(() -> client.callTool("nope", Map.of()))
                .isInstanceOf(InterestGraphException.class)
                .hasMessageContaining("Unknown tool");
    }

    @Test
    void callTool_throwsWhenSegueIsUnreachable() {
        server.enqueue(new MockResponse().setResponseCode(503));

        assertThatThrownBy(() -> client.callTool("add_entity", Map.of("qid", "Q1")))
                .isInstanceOf(InterestGraphException.class);
    }
}
