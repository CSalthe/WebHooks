package dev.webhooks;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record WebhookEvent(@NotBlank @Size(max=100) String id,
        @NotBlank String type, @NotNull JsonNode data) {}
