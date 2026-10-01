package dev.webhooks;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
@Service
public class EventStore {
    public record Receipt(String receivedAt, WebhookEvent event, String message) {}
    public record Result(String status, String message) {}
    private final LinkedHashMap<String, Receipt> receipts = new LinkedHashMap<>();
    private final LinkedHashSet<String> failedOnce = new LinkedHashSet<>();
    private final Map<String, WebhookHandler> handlers = new HashMap<>();
    public EventStore(List<WebhookHandler> handlers) { handlers.forEach(h -> this.handlers.put(h.type(), h)); }
    public synchronized Result receive(WebhookEvent event, boolean failFirst) {
        if (!handlers.containsKey(event.type())) return new Result("unsupported", "Unknown event type");
        Receipt previous = receipts.get(event.id());
        if (previous != null) {
            if (!previous.event().equals(event)) return new Result("conflict", "This event ID already belongs to a different payload");
            return new Result("duplicate", "Already processed; no second side effect");
        }
        if (failFirst && failedOnce.add(event.id())) {
            if (failedOnce.size() > 500) failedOnce.remove(failedOnce.iterator().next());
            return new Result("retry", "Simulated temporary failure. Send the same event again.");
        }
        String message = handlers.get(event.type()).handle(event);
        receipts.put(event.id(), new Receipt(Instant.now().toString(), event, message));
        if (receipts.size() > 200) receipts.remove(receipts.keySet().iterator().next());
        return new Result("processed", message);
    }
    public synchronized List<Receipt> receipts() {
        var list = new ArrayList<>(receipts.values());
        Collections.reverse(list);
        return list;
    }
}
