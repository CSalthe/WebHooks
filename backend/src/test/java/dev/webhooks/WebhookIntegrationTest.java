package dev.webhooks;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import java.net.URI;
import java.net.http.*;
import java.time.Instant;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebhookIntegrationTest {
    @LocalServerPort int port;
    @Autowired SignatureService signatures;
    @Autowired ObjectMapper json;
    final HttpClient client=HttpClient.newHttpClient();
    String body(String type) throws Exception {
        return json.writeValueAsString(Map.of("id", UUID.randomUUID().toString(), "type", type, "data", Map.of("orderId", "ORD-1")));
    }
    HttpResponse<String> post(String body, String timestamp, String signature, boolean fail) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/webhooks/events"))
            .header("Content-Type","application/json").header("X-Webhook-Timestamp",timestamp)
            .header("X-Webhook-Signature",signature).header("X-Demo-Fail-First",Boolean.toString(fail))
            .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpResponse<String> valid(String body, boolean fail) throws Exception {
        String time=Long.toString(Instant.now().getEpochSecond()); return post(body,time,signatures.sign(time,body),fail);
    }
    @Test void handlesAllThreeTypesAndDeduplicates() throws Exception {
        for(String type:List.of("order.created","payment.succeeded","shipment.delivered")) {
            String body=body(type);
            assertThat(valid(body,false).body()).contains("processed");
            var duplicate=valid(body,false);
            assertThat(duplicate.statusCode()).isEqualTo(200);
            assertThat(duplicate.body()).contains("duplicate");
        }
    }
    @Test void rejectsTamperedAndStaleRequests() throws Exception {
        String body=body("order.created"), time=Long.toString(Instant.now().getEpochSecond());
        assertThat(post(body+" ",time,signatures.sign(time,body),false).statusCode()).isEqualTo(401);
        String old=Long.toString(Instant.now().minusSeconds(600).getEpochSecond());
        assertThat(post(body,old,signatures.sign(old,body),false).statusCode()).isEqualTo(401);
    }
    @Test void validatesJsonEventTypeAndConflictingIds() throws Exception {
        assertThat(valid("broken",false).statusCode()).isEqualTo(400);
        assertThat(valid("{}",false).statusCode()).isEqualTo(400);
        assertThat(valid(body("unknown.event"),false).statusCode()).isEqualTo(400);
        String body=body("order.created"); valid(body,false);
        assertThat(valid(body.replace("ORD-1","ORD-2"),false).statusCode()).isEqualTo(409);
    }
    @Test void failedAttemptDoesNotMarkEventAsProcessed() throws Exception {
        String body=body("order.created");
        assertThat(valid(body,true).statusCode()).isEqualTo(503);
        assertThat(valid(body,true).body()).contains("processed");
        assertThat(valid(body,true).body()).contains("duplicate");
    }
    @Test void simulatorPerformsRealHttpRetry() throws Exception {
        String request=json.writeValueAsString(Map.of("event",json.readTree(body("order.created")),"failFirst",true,"autoRetry",true));
        var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/simulate"))
            .header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(request)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var attempts=json.readTree(response.body()).path("attempts");
        assertThat(attempts.size()).isEqualTo(2);
        assertThat(attempts.get(0).path("httpStatus").asInt()).isEqualTo(503);
        assertThat(attempts.get(1).path("httpStatus").asInt()).isEqualTo(200);
    }
}
