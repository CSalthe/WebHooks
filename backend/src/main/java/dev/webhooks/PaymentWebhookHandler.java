package dev.webhooks;
import org.springframework.stereotype.Component;
@Component
public class PaymentWebhookHandler implements WebhookHandler {
    public String type() { return "payment.succeeded"; }
    public String handle(WebhookEvent event) {
        return "Payment " + event.data().path("paymentId").asText("unknown") + " received. Demo: mark the order as paid.";
    }
}
