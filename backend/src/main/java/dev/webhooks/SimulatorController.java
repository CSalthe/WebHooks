package dev.webhooks;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
@RestController
@RequestMapping("/api")
public class SimulatorController {
    public record SendRequest(@NotNull @Valid WebhookEvent event, boolean invalidSignature, boolean failFirst, boolean autoRetry) {}
    public record Attempt(int number, int httpStatus, String response) {}
    public record Delivery(String url, String timestamp, String signature, String body, List<Attempt> attempts) {}
    private final ObjectMapper json;
    private final SignatureService signatures;
    private final EventStore store;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    public SimulatorController(ObjectMapper json, SignatureService signatures, EventStore store) { this.json=json; this.signatures=signatures; this.store=store; }
    @GetMapping("/events") public List<EventStore.Receipt> events() { return store.receipts(); }
    @PostMapping("/simulate")
    public ResponseEntity<?> send(@Valid @RequestBody SendRequest request, HttpServletRequest servlet) throws java.io.IOException {
        String body = json.writeValueAsString(request.event());
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = request.invalidSignature() ? "sha256=invalid-demo-signature" : signatures.sign(timestamp, body);
        // Fixed loopback destination: this learning tool cannot send to arbitrary external URLs.
        String url = "http://127.0.0.1:" + servlet.getLocalPort() + "/webhooks/events";
        var http = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json").header("X-Webhook-Timestamp", timestamp)
                .header("X-Webhook-Signature", signature).header("X-Demo-Fail-First", Boolean.toString(request.failFirst()))
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        var attempts = new ArrayList<Attempt>();
        try {
            for (int n=1; n <= (request.autoRetry() ? 3 : 1); n++) {
                var response = client.send(http, HttpResponse.BodyHandlers.ofString());
                attempts.add(new Attempt(n, response.statusCode(), response.body()));
                if (response.statusCode() < 500 || n == 3 || !request.autoRetry()) break;
                Thread.sleep(250L * (1L << (n-1)));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(503).body(Map.of("message", "Delivery interrupted"));
        } catch (java.io.IOException e) {
            return ResponseEntity.status(502).body(Map.of("message", "Local receiver could not be reached"));
        }
        return ResponseEntity.ok(new Delivery(url, timestamp, signature, body, attempts));
    }
}
