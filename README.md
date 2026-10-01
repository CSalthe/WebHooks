# Webhook Lab

A small Java Spring Boot + Angular playground for learning webhooks. No database, Docker, external accounts, or cloud services required.

## Run on Windows

Prerequisites: JDK 21+ and Node.js 24 LTS (or an Angular 21 compatible Node version). Maven is downloaded by the included wrapper on first use. If a portable JDK exists in .tools, the backend launcher uses it automatically.

Open two PowerShell terminals in D:\Codex\WebHooks:

    .\Start-Backend.ps1

    .\Start-Frontend.ps1

Open http://localhost:4200. Stop each server with Ctrl+C. Both servers listen only on loopback. The Angular proxy forwards API calls to port 8080; no CORS configuration is needed. First-time dependency installation needs internet access.

Alternatively, run backend/mvnw.cmd spring-boot:run from backend (with JAVA_HOME set), and npm ci followed by npm start from frontend.

## How it works

    Angular → POST /api/simulate (producer)
                   → actual HTTP POST /webhooks/events (receiver)
                   → verify signature and timestamp
                   → check event ID and route to a Java handler
                   → acknowledge with HTTP status and JSON

The Angular screen is a control panel. The Java simulator plays the external service that sends a webhook. It performs a real HTTP call to the receiver in the same application. In a real integration these would usually be separate systems. The received-events list refreshes after sending or clicking Refresh; it is not a webhook to the browser.

## Practice exercises

1. **Successful event:** select order.created and Send webhook. Inspect HTTP 200, the response, and the received event.
2. **Duplicates:** click Send again without changing ID or data. The response says duplicate; the handler does not run a second time.
3. **Other handlers:** select payment.succeeded or shipment.delivered. Each choice loads example JSON and creates a new ID.
4. **Authentication:** enable invalid signature. Expect HTTP 401 and no received event.
5. **Retry:** create a new ID, disable invalid signature, enable fail first and automatic retry. See 503 then 200. Disable automatic retry to perform the second attempt manually with the same ID.
6. **Conflicting IDs:** after a success, edit the payload without changing the ID. Expect 409. A new event needs a new ID.
7. **Extend it:** create a RefundWebhookHandler implementing WebhookHandler, annotate it with @Component, return refund.created from type(), and add an example in frontend/src/app.ts. Spring discovers the handler automatically.

## Java classes to explore

| Class | Responsibility |
| --- | --- |
| SimulatorController | Producer: serialize, sign, send real HTTP requests, retry 5xx responses |
| WebhookController | Receiver endpoint: verify before parsing, validate, acknowledge |
| SignatureService | HMAC-SHA256 of timestamp + period + exact raw JSON; five-minute timestamp tolerance |
| EventStore | Bounded in-memory history and atomic duplicate protection |
| OrderWebhookHandler | Demonstrate order confirmation |
| PaymentWebhookHandler | Demonstrate marking an order paid |
| ShipmentWebhookHandler | Demonstrate delivery notification |

Handlers return explanatory messages; they do not actually send email, charge cards, or update a database. Example data fields are illustrative; validation enforces the event envelope and data object, not a business-specific schema.

## Endpoints and request format

- POST /api/simulate: { "event": { "id": "demo-1", "type": "order.created", "data": { "orderId": "ORD-1" } }, "invalidSignature": false, "failFirst": false, "autoRetry": true }
- GET /api/events: latest successfully processed events, newest first.
- POST /webhooks/events: raw event object with Content-Type: application/json, X-Webhook-Timestamp (Unix seconds), and X-Webhook-Signature (sha256= followed by lowercase HMAC hex). Optional X-Demo-Fail-First: true simulates one temporary failure.

The inspector shows the exact body and headers. You can replay these in Postman within five minutes to observe duplicate handling. Editing the body requires recalculating the signature. /api/simulate returning 200 means the simulation completed; inspect each attempt's HTTP status to determine receiver success.

## Build and test

With JDK 21+ available, from backend:

    .\mvnw.cmd verify

From frontend:

    npm ci
    npm run build

Integration tests exercise real HTTP handling for all three event types, duplicates, conflicting IDs, malformed input, invalid or expired signatures, and retry behavior.

## Learning scope

History and duplicate protection retain the latest 200 successful IDs. Failure simulation remembers 500 IDs. Restarting clears everything; evicted IDs can be processed again. The shared local demo secret is local-learning-secret; override WEBHOOK_SECRET before starting the backend. This deliberately local tool includes an unauthenticated producer and failure-simulation header for experimentation. Before adapting it for deployment, remove those demo endpoints/controls, use per-provider secrets and HTTPS, and introduce durable idempotency storage, queued processing, and retry handling for network failures. The short synchronous retry loop is for observation, not a production worker.

Versions: Spring Boot 3.5.16, Java 21, Angular 21.2, TypeScript 5.9. Compatibility references: https://docs.spring.io/spring-boot/3.5/system-requirements.html and https://angular.dev/reference/versions.
