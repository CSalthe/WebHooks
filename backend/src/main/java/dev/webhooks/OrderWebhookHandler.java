package dev.webhooks;
import org.springframework.stereotype.Component;
@Component
public class OrderWebhookHandler implements WebhookHandler {
    public String type() { return "order.created"; }
    public String handle(WebhookEvent event) {
        return "Order " + event.data().path("orderId").asText("unknown") + " received. Demo: prepare a confirmation email.";
    }
}
