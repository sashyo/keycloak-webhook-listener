package au.com.ais.keycloak.webhook;

import java.net.http.HttpClient;
import java.time.Duration;
import org.keycloak.Config;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventListenerProviderFactory;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;

public class WebhookEventListenerProviderFactory implements EventListenerProviderFactory {

    // the name you pick in Realm settings -> Events -> Event listeners
    public static final String ID = "webhook";

    private HttpClient http;
    private String url;
    private String secret;

    @Override
    public void init(Config.Scope config) {
        // read from KC_SPI_EVENTS_LISTENER_WEBHOOK_URL / _SECRET
        url = config.get("url");
        secret = config.get("secret");
        http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Override
    public EventListenerProvider create(KeycloakSession session) {
        return new WebhookEventListenerProvider(session, http, url, secret);
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void postInit(KeycloakSessionFactory factory) {
    }

    @Override
    public void close() {
    }
}
