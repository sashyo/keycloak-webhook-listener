package au.com.ais.keycloak.webhook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.AbstractKeycloakTransaction;
import org.keycloak.models.KeycloakSession;
import org.keycloak.util.JsonSerialization;

public class WebhookEventListenerProvider implements EventListenerProvider {

    private static final Logger LOG = Logger.getLogger(WebhookEventListenerProvider.class);

    private final KeycloakSession session;
    private final HttpClient http;
    private final String url;
    private final String secret;

    public WebhookEventListenerProvider(KeycloakSession session, HttpClient http, String url, String secret) {
        this.session = session;
        this.http = http;
        this.url = url;
        this.secret = secret;
    }

    // user events: LOGIN, LOGIN_ERROR, REGISTER, UPDATE_PASSWORD, LOGOUT ...
    @Override
    public void onEvent(Event event) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", event.getType().name());
        body.put("realm", event.getRealmName());
        body.put("clientId", event.getClientId());
        body.put("userId", event.getUserId());
        body.put("ipAddress", event.getIpAddress());
        body.put("error", event.getError());
        body.put("details", event.getDetails());
        body.put("time", event.getTime());
        send(body);
    }

    // admin events: users, roles, clients created/updated/deleted via console or REST API
    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "ADMIN_" + event.getOperationType().name());
        body.put("realm", event.getRealmName());
        body.put("resourceType", event.getResourceTypeAsString());
        body.put("resourcePath", event.getResourcePath());
        body.put("time", event.getTime());
        if (includeRepresentation && event.getRepresentation() != null) {
            body.put("representation", parse(event.getRepresentation()));
        }
        send(body);
    }

    private void send(Map<String, Object> body) {
        if (url == null || secret == null) return; // not configured: do nothing
        // only fire once Keycloak's transaction has committed - never for rolled-back changes
        session.getTransactionManager().enlistAfterCompletion(new AbstractKeycloakTransaction() {
            @Override
            protected void commitImpl() {
                post(toJson(body));
            }

            @Override
            protected void rollbackImpl() {
            }
        });
    }

    private void post(String json) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-Type", "application/json")
                .header("X-Keycloak-Signature", "sha256=" + sign(json))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        // async: a slow webhook must never slow down a user's login
        http.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                .whenComplete((res, err) -> {
                    if (err != null) LOG.warnf("Webhook delivery failed: %s", err.getMessage());
                    else LOG.debugf("Webhook delivered: HTTP %d", res.statusCode());
                });
    }

    // HMAC-SHA256 so the receiver can prove the call really came from Keycloak
    private String sign(String json) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(json.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String toJson(Object value) {
        try {
            return JsonSerialization.writeValueAsString(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Object parse(String json) {
        try {
            return JsonSerialization.readValue(json, Map.class);
        } catch (IOException e) {
            return json;
        }
    }

    @Override
    public void close() {
    }
}
