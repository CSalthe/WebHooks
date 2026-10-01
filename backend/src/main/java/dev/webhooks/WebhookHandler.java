package dev.webhooks;
/** Implement this interface and add @Component to introduce another event type. */
public interface WebhookHandler {
    String type();
    String handle(WebhookEvent event);
}
