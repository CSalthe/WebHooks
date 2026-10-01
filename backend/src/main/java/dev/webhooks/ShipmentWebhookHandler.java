package dev.webhooks;
import org.springframework.stereotype.Component;
@Component
public class ShipmentWebhookHandler implements WebhookHandler {
    public String type() { return "shipment.delivered"; }
    public String handle(WebhookEvent event) {
        return "Shipment " + event.data().path("trackingNumber").asText("unknown") + " delivered. Demo: notify the customer.";
    }
}
