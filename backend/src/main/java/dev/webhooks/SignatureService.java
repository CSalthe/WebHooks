package dev.webhooks;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
@Service
public class SignatureService {
    private final byte[] secret;
    public SignatureService(@Value("${playground.secret}") String secret) { this.secret = secret.getBytes(StandardCharsets.UTF_8); }
    public String sign(String timestamp, String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException e) { throw new IllegalStateException(e); }
    }
    public boolean valid(String timestamp, String body, String signature) {
        if (timestamp == null || signature == null) return false;
        try {
            long sent = Long.parseLong(timestamp), now = Instant.now().getEpochSecond();
            if (sent < now - 300 || sent > now + 300) return false;
            return MessageDigest.isEqual(sign(timestamp, body).getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
        } catch (NumberFormatException e) { return false; }
    }
}
