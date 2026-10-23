<p align="center">
  <img src="assets/logo.png" alt="Australian IAM Solutions" width="96">
</p>

<h1 align="center">Keycloak webhook event listener</h1>

<p align="center">
  Send a signed webhook to your own systems on every Keycloak login, sign-up and admin change.<br>
  <a href="https://australianiamsolutions.com/guides/keycloak-webhook-extension/">Step-by-step guide</a> ·
  <a href="https://australianiamsolutions.com">Australian IAM Solutions</a>
</p>

---

A Keycloak **Event Listener SPI** extension that posts every user event (`LOGIN`, `LOGIN_ERROR`, `REGISTER`, …) and admin event (users, clients and roles created, updated or deleted) as JSON to a URL you choose.

- **Signed:** each request carries `X-Keycloak-Signature: sha256=<HMAC-SHA256 of the body>`, so the receiver can prove it came from your Keycloak.
- **After commit:** events are only sent once Keycloak's transaction commits, so you never announce a change that was rolled back.
- **Never blocks a login:** delivery is asynchronous. A slow or offline receiver is logged, not felt by users.

Three files, an 8 KB jar. Tested on **Keycloak 26.4.0** with Java 21 (compiles for Java 17+).

## Build

```bash
mvn -q package
# target/keycloak-webhook-listener-1.0.0.jar
```

Set `keycloak.version` in `pom.xml` to match your server.

## Run the demo

`receiver.mjs` is a tiny receiver that verifies the signature and prints each event.

```bash
docker network create kc-demo
docker run -d --name webhook-receiver --network kc-demo -v ./receiver.mjs:/app/receiver.mjs \
  -e WEBHOOK_SECRET=demo-secret-123 node:22-alpine node /app/receiver.mjs
docker run -d --name keycloak --network kc-demo -p 8180:8080 \
  -v ./target/keycloak-webhook-listener-1.0.0.jar:/opt/keycloak/providers/keycloak-webhook-listener-1.0.0.jar \
  -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin \
  -e KC_SPI_EVENTS_LISTENER_WEBHOOK_URL=http://webhook-receiver:4000/keycloak \
  -e KC_SPI_EVENTS_LISTENER_WEBHOOK_SECRET=demo-secret-123 \
  quay.io/keycloak/keycloak:26.4.0 start-dev
docker logs -f webhook-receiver
```

Then turn it on for a realm: **Realm settings → Events → Event listeners → add `webhook`**. Also enable **Admin events** with **Include representation** if you want admin changes with the full record. Or use the CLI:

```bash
kcadm() { docker exec keycloak /opt/keycloak/bin/kcadm.sh "$@"; }
kcadm config credentials --server http://localhost:8080 --realm master --user admin --password admin
kcadm create realms -s realm=acme -s enabled=true \
  -s 'eventsListeners=["jboss-logging","webhook"]' -s adminEventsEnabled=true -s adminEventsDetailsEnabled=true
```

## Configuration

| Environment variable | Meaning |
|---|---|
| `KC_SPI_EVENTS_LISTENER_WEBHOOK_URL` | Where events are POSTed. If unset, the listener does nothing. |
| `KC_SPI_EVENTS_LISTENER_WEBHOOK_SECRET` | Shared secret for the HMAC signature. Required. |

The `KC-SERVICES0047 … internal SPI` warning at startup is expected for event listeners.

## Before production

- Delivery is fire-and-forget. If every event matters, add retries or an outbox and queue (Kafka, SQS).
- Filter to the event types you need and send the minimum personal data.
- Use HTTPS, keep the secret in a vault and rotate it, and verify signatures with a constant-time compare.
- For optimised images, copy the jar into `/opt/keycloak/providers/` and run `kc.sh build`.
- Pin your Keycloak version and retest on every upgrade (internal SPI).

## Need help?

Australian IAM Solutions builds, reviews and hardens Keycloak extensions. **[australianiamsolutions.com](https://australianiamsolutions.com)**

## License

Apache License 2.0. See [LICENSE](LICENSE).
