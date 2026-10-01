package dev.webhooks;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Validator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController
public class WebhookController {
    private final SignatureService signatures;
    private final EventStore store;
    private final ObjectMapper json;
    private final Validator validator;
    public WebhookController(SignatureService signatures, EventStore store, ObjectMapper json, Validator validator) {
        this.signatures=signatures; this.store=store; this.json=json; this.validator=validator;
    }
    @PostMapping(value="/webhooks/events", consumes="application/json")
    public ResponseEntity<EventStore.Result> receive(@RequestBody String body,
            @RequestHeader(value="X-Webhook-Timestamp", required=false) String timestamp,
            @RequestHeader(value="X-Webhook-Signature", required=false) String signature,
            @RequestHeader(value="X-Demo-Fail-First", defaultValue="false") boolean failFirst) {
        // Authenticate the exact raw body BEFORE parsing it. Whitespace changes the signature.
        if (!signatures.valid(timestamp, body, signature))
            return ResponseEntity.status(401).body(new EventStore.Result("rejected", "Invalid signature or timestamp outside the five-minute window"));
        try {
            WebhookEvent event = json.readValue(body, WebhookEvent.class);
            if (event == null || !validator.validate(event).isEmpty() || !event.data().isObject())
                return ResponseEntity.badRequest().body(new EventStore.Result("invalid", "Provide id (1–100 characters), type, and a data object"));
            var result = store.receive(event, failFirst);
            int code = switch(result.status()) { case "unsupported" -> 400; case "retry" -> 503; case "conflict" -> 409; default -> 200; };
            return ResponseEntity.status(code).body(result);
        } catch (JsonProcessingException e) {
            return ResponseEntity.badRequest().body(new EventStore.Result("invalid", "Body must be valid event JSON"));
        }
    }
}
