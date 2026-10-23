// Tiny webhook receiver: verifies Keycloak's signature and prints each event
import { createServer } from "node:http";
import { createHmac, timingSafeEqual } from "node:crypto";

const SECRET = process.env.WEBHOOK_SECRET;

createServer((req, res) => {
  let body = "";
  req.on("data", (c) => (body += c));
  req.on("end", () => {
    const expected = "sha256=" + createHmac("sha256", SECRET).update(body).digest("hex");
    const got = req.headers["x-keycloak-signature"] ?? "";
    const ok = got.length === expected.length && timingSafeEqual(Buffer.from(got), Buffer.from(expected));
    res.writeHead(ok ? 204 : 401).end();
    const e = JSON.parse(body);
    console.log(`\n${ok ? "✔ signature valid" : "✘ BAD SIGNATURE"}  ←  ${e.type}`);
    console.log(JSON.stringify(e, null, 2));
  });
}).listen(4000, () => console.log("Listening for Keycloak webhooks on :4000"));
