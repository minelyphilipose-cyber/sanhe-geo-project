package com.huanjing.geo.manual;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Manual mock CMS server for P1.4-c E2E verification.
 *
 * <p>Run with: {@code mvn test-compile exec:java -Dexec.mainClass=com.huanjing.geo.manual.MockCmsServer
 *                       -Dexec.classpathScope=test}
 * <br>Or directly: {@code java -cp target/test-classes com.huanjing.geo.manual.MockCmsServer}
 *
 * <p>Default port: 9090. Override with -Dport=&lt;n&gt;.
 *
 * <p>Behavior controlled by request header X-Mock-Behavior:
 * <ul>
 *   <li>"ok" or absent: 200 with success body</li>
 *   <li>"auth_expired": 401</li>
 *   <li>"server_error": 500</li>
 *   <li>"client_error": 400</li>
 *   <li>"garbled": 200 with invalid JSON body (tests adapter exception path)</li>
 * </ul>
 * Or default behavior via -Ddefault-behavior=auth_expired (etc.)
 *
 * <p>This is a manual test tool, NOT shipped with production code.
 */
public class MockCmsServer {

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getProperty("port", "9090"));
        String defaultBehavior = System.getProperty("default-behavior", "ok");

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/api/publish", new PublishHandler(defaultBehavior));
        server.createContext("/api/health", new HealthHandler());
        server.setExecutor(null);
        server.start();

        System.out.println("Mock CMS listening on port " + port +
                           " (default behavior: " + defaultBehavior + ")");
        System.out.println("Routes: POST /api/publish, GET /api/health");
        System.out.println("Override per-request via header: X-Mock-Behavior");
    }

    static class PublishHandler implements HttpHandler {
        private final String defaultBehavior;

        PublishHandler(String defaultBehavior) {
            this.defaultBehavior = defaultBehavior;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String behavior = exchange.getRequestHeaders().getFirst("X-Mock-Behavior");
            if (behavior == null || behavior.isBlank()) {
                behavior = defaultBehavior;
            }
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            System.out.println("[mock-cms] " + exchange.getRequestMethod() +
                               " " + exchange.getRequestURI() +
                               " behavior=" + behavior +
                               " auth=" + (authHeader == null ? "NONE" : "PRESENT"));

            switch (behavior.toLowerCase()) {
                case "auth_expired":
                    respond(exchange, 401, "{\"error\":\"unauthorized\"}");
                    break;
                case "server_error":
                    respond(exchange, 500, "{\"error\":\"internal\"}");
                    break;
                case "client_error":
                    respond(exchange, 400, "{\"error\":\"bad request\"}");
                    break;
                case "garbled":
                    respond(exchange, 200, "this is not json {{");
                    break;
                case "ok":
                default:
                    String randomId = UUID.randomUUID().toString().substring(0, 8);
                    String body = String.format(
                        "{\"id\":\"mock_article_%s\"," +
                        "\"url\":\"https://mock-cms.local/posts/%s\"," +
                        "\"status\":\"ok\"}",
                        randomId, randomId);
                    respond(exchange, 200, body);
                    break;
            }
        }
    }

    static class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            respond(exchange, 200, "{\"status\":\"ok\"}");
        }
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
