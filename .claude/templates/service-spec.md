# Specification: {{service-name}}

Specification version — 1.0

<!-- TOC -->
* [1. General Information](#1-general-information)
* [2. Functional Requirements](#2-functional-requirements)
  * [2.1. Primary Use Case](#21-primary-use-case)
  * [2.2. Alternative and Error Scenarios](#22-alternative-and-error-scenarios)
  * [2.3. Business Rules and Processing Logic](#23-business-rules-and-processing-logic)
  * [2.4. API](#24-api)
  * [2.5. Integrations](#25-integrations)
  * [2.6. Data Models](#26-data-models)
  * [2.7. Configuration Requirements](#27-configuration-requirements)
  * [2.8. Logging](#28-logging)
  * [2.9. Input Validation](#29-input-validation)
  * [2.10. Error Handling](#210-error-handling)
  * [2.11. Headers and Metadata](#211-headers-and-metadata)
* [3. Acceptance Criteria Recommendations](#3-acceptance-criteria-recommendations)
* [4. Non-Functional Requirements](#4-non-functional-requirements)
  * [4.1. Security](#41-security)
  * [4.2. Performance](#42-performance)
  * [4.3. Reliability](#43-reliability)
  * [4.4. Monitoring](#44-monitoring)
  * [4.5. Audit](#45-audit)

<!-- TOC -->

---

## 1. General Information

<!-- Description of the service's purpose, its role in the platform architecture -->

**{{service-name}}** is a microservice designed for <!-- service purpose -->.

<!-- EXAMPLE:
**order-service** is a microservice of the online store platform, designed for
managing the order lifecycle: creating orders, applying promo codes,
calculating the final amount, submitting for payment, and tracking delivery status.
The service accepts requests from client applications (web, mobile), interacts
with payment-service for payments, and sends events to Kafka for notification-service.
-->

---

## 2. Functional Requirements

### 2.1. Primary Use Case

<!-- Description of the main processing flow. Recommended to use
numbered steps and links to PlantUML sequence diagrams. -->
<!-- For UML diagrams use the format:
![Diagram name](diagrams/name.puml)
-->

#### 2.1.1. <!-- First step name -->

<!-- Step description. For each step specify:
- Input data (format, source)
- Processing (what happens)
- Output data (format, recipient)
-->
<!-- EXAMPLE:
#### 2.1.1. Create Order

1. Client sends a POST request to `/api/v1/orders` with an array of items, delivery address, and (optionally) a promo code.
2. order-service validates input data (item presence, productId correctness, address format).
3. Calculates the total order amount based on catalog prices.
4. If a promo code is provided — validates it and applies the discount (see section 2.3).
5. Saves the order to PostgreSQL with status `CREATED`.
6. Sends an `ORDERS.CREATED` event to Kafka.
7. Returns a response to the client with `orderId`, amount, and status.

#### 2.1.2. Order Payment

1. Client calls POST `/api/v1/orders/{orderId}/pay`.
2. order-service sends a gRPC request to payment-service with the final amount.
3. payment-service returns `paymentId` and `redirectUrl` for payment.
4. order-service updates the order status to `PENDING_PAYMENT` and saves `paymentId`.
5. Returns `redirectUrl` to the client for navigating to the payment page.

#### 2.1.3. Payment Confirmation (callback)

1. payment-service sends a `PAYMENTS.COMPLETED` event to Kafka.
2. order-service receives the event, finds the order by `paymentId`.
3. Updates the order status to `PAID`.
4. Sends an `ORDERS.PAID` event to Kafka.
5. notification-service receives the event and sends email/push to the client.
-->

#### 2.1.2. <!-- Second step name -->

<!-- ... -->

### 2.2. Alternative and Error Scenarios

<!-- Use color coding for scenario types:
<font color="red">**Alt**</font> — alternative flow
<font color="blue">**Opt**</font> — optional element
<font color="blue">**Loop**</font> — loop element
-->

#### 2.2.1. <font color="red">**Alt**</font> <!-- Error scenario name -->

<!-- EXAMPLE:
#### 2.2.1. <font color="red">**Alt**</font> **Payment Failure**

1. payment-service sends a `PAYMENTS.FAILED` event to Kafka.
2. order-service receives the event and updates the order status to `PAYMENT_FAILED`.
3. The client can retry payment within 30 minutes (parameter `shop.order.payment-retry-window-min`).
4. If payment is not completed within 30 minutes — the order is automatically cancelled (status `CANCELLED`).

#### 2.2.2. <font color="red">**Alt**</font> **Item Out of Stock**

1. When creating an order, order-service checks item availability (reservation).
2. If at least one item is out of stock — returns error 409 CONFLICT indicating which items are unavailable.
3. The order is NOT created.

#### 2.2.3. <font color="blue">**Opt**</font> **Promo Code Application**

1. If a `promoCode` is provided in the order creation request, promo code validation is performed (see section 2.3).
2. If the promo code is invalid, returns error 400 with a description of the reason.
-->

### 2.3. Business Rules and Processing Logic

<!-- Explicit description of the service's decision-making logic: routing conditions,
data transformation rules, processing branching. This section answers the question
"HOW the service makes decisions", not just "WHAT it accepts/returns".

Format: numbered rules. For conditions — IF/THEN/ELSE.
If the service has no complex logic, state: "Linear processing, see section 2.1" -->
<!-- EXAMPLE:
### Promo Code Application Rules

1. **IF** the `promoCode` field is not provided or is empty,
**THEN** the order is placed at full prices, `discountAmount = 0`.

2. **IF** `promoCode` is provided,
**THEN** validate the promo code in the `promo_codes` table:
- **IF** the promo code is not found, **THEN** return error 400 "Promo code not found".
- **IF** `valid_until < now()`, **THEN** return error 400 "Promo code has expired".
- **IF** `usage_count >= max_usages`, **THEN** return error 400 "Promo code is no longer active".

3. **IF** the promo code is valid and `discount_type = 'PERCENT'`,
**THEN** discount = `totalAmount * discount_value / 100`.
**IF** `discount_type = 'FIXED'`,
**THEN** discount = `discount_value`.

4. **IF** the discount exceeds the order amount,
**THEN** `discountAmount = totalAmount`, `finalAmount = 0`.

### Order Status Transition Rules

1. `CREATED` → `PENDING_PAYMENT` — after calling `/pay`.
2. `PENDING_PAYMENT` → `PAID` — after receiving `PAYMENTS.COMPLETED` event.
3. `PENDING_PAYMENT` → `PAYMENT_FAILED` — after receiving `PAYMENTS.FAILED` event.
4. `PAYMENT_FAILED` → `PENDING_PAYMENT` — on payment retry (within 30 min).
5. `PAYMENT_FAILED` → `CANCELLED` — automatically by cron, if > 30 min elapsed.
6. `PAID` → `SHIPPED` — on status update from the delivery system.
7. `SHIPPED` → `DELIVERED` — on delivery confirmation.
8. Any other status transition is prohibited — return 409 CONFLICT.

### Notification Rules

1. **IF** the order transitions to `PAID` status,
**THEN** send `ORDERS.PAID` event to Kafka — notification-service sends "Order paid" email.
2. **IF** the order transitions to `SHIPPED` status,
**THEN** send `ORDERS.SHIPPED` event — notification-service sends "Order shipped" push.
3. **IF** the order transitions to `CANCELLED` status,
**THEN** send `ORDERS.CANCELLED` event — notification-service sends "Order cancelled" email.
-->

### 2.4. API

<!-- Description of the service's external APIs: REST, gRPC, WebSocket (if applicable).
For each endpoint/method: description, request/response, error codes.
If available — link to OpenAPI specification or .proto file. -->

#### <!-- Type: METHOD /path or gRPC ServiceName.Method -->

<!-- EXAMPLE (REST):
#### POST /api/v1/orders — Create Order

**Request:**
```json
{
"items": [
{"productId": "prod-001", "quantity": 2, "price": 500.00},
{"productId": "prod-002", "quantity": 1, "price": 500.00}
],
"deliveryAddress": "Moscow, Lenin St., 1, apt. 10",
"promoCode": "SUMMER2026"
}
```

**Response (201 Created):**
```json
{
"orderId": "ord-123",
"status": "CREATED",
"items": [
{"productId": "prod-001", "quantity": 2, "price": 500.00, "subtotal": 1000.00},
{"productId": "prod-002", "quantity": 1, "price": 500.00, "subtotal": 500.00}
],
"originalAmount": 1500.00,
"promoCode": "SUMMER2026",
"discountAmount": 150.00,
"finalAmount": 1350.00,
"createdAt": "2026-04-03T10:00:00Z"
}
```

**Response (400 Bad Request):**
```json
{
"type": "https://api.shop.com/problems/bad-request",
"title": "Invalid request",
"errorCode": "INVALID_INPUT",
"status": 400
}
```

#### GET /api/v1/orders/{orderId} — Get Order by ID

**Response (200 OK):**
```json
{
"orderId": "ord-123",
"status": "PAID",
"items": [...],
"originalAmount": 1500.00,
"discountAmount": 150.00,
"finalAmount": 1350.00,
"paymentId": "pay-456",
"createdAt": "2026-04-03T10:00:00Z",
"updatedAt": "2026-04-03T10:05:00Z"
}
```

**Response (404 Not Found):**
```json
{
"type": "https://api.shop.com/problems/not-found",
"title": "Order not found",
"errorCode": "ORDER_NOT_FOUND",
"status": 404
}
```
-->
<!-- EXAMPLE (gRPC):
#### gRPC: PaymentService

```protobuf
service PaymentService {
rpc CreatePayment (CreatePaymentRequest) returns (CreatePaymentResponse);
rpc GetPaymentStatus (GetPaymentStatusRequest) returns (GetPaymentStatusResponse);
}

message CreatePaymentRequest {
string order_id = 1;
double amount = 2;
string currency = 3;
}

message CreatePaymentResponse {
string payment_id = 1;
string status = 2;
string redirect_url = 3;
}
```

Full proto file: [proto/payment_service.proto](proto/payment_service.proto)
-->

### 2.5. Integrations

<!-- Description of integrations with external systems: Kafka, gRPC, SSE, HTTP services,
databases, cron/batch tasks. For each integration: protocol, address/topic,
data format, timeout, retry policy, behavior on unavailability. -->
<!-- EXAMPLE (Kafka):

#### Kafka: ORDERS.CREATED (outgoing)
- **Direction**: producer
- **Format**: JSON (OrderCreatedEvent schema)
- **Group**: —
- **Description**: event about new order creation, consumed by notification-service

#### Kafka: PAYMENTS.COMPLETED (incoming)
- **Direction**: consumer
- **Format**: JSON (PaymentCompletedEvent schema)
- **Group**: `order-service`
- **Concurrency**: 3 threads (parameter `spring.kafka.listener.concurrency`)
- **Description**: callback from payment-service about successful payment
-->
<!-- EXAMPLE (gRPC):
#### gRPC: payment-service
- **Method**: `CreatePayment`
- **Timeout**: 10000 ms
- **Retry**: 2 attempts, backoff 1s/3s
- **On unavailability**: return 503 "Payment service temporarily unavailable" to client
-->
<!-- EXAMPLE (SSE):
#### SSE: GET /api/v1/orders/{orderId}/status-stream — Order Status Streaming
- **Direction**: server → client (event stream)
- **Format**: `text/event-stream`
- **Event**: `event: ORDER_STATUS_CHANGED`, `data: {"status": "SHIPPED"}`
- **Timeout**: 300000 ms (5 minutes)
- **Reconnect**: client reconnects with `Last-Event-ID`
-->
<!-- EXAMPLE (DB):
#### PostgreSQL: shopdb (database)
- **Direction**: read/write
- **Port**: 5432
- **Tables**: orders, order_items, promo_codes
- **Connection pool**: HikariCP, max 20 connections
- **Query timeout**: 5000 ms
- **Retry**: built-in HikariCP retry on connection loss
-->
<!-- EXAMPLE (Cron/Batch):
#### Cron: Cancel Expired Orders — Cancel Unpaid Orders
- **Schedule**: `0 */5 * * * *` (every 5 minutes)
- **Description**: find orders in PAYMENT_FAILED status older than 30 minutes and transition to CANCELLED
- **Max retry**: 3 attempts per batch
- **Backoff**: 5000 ms between attempts
-->

### 2.6. Data Models

<!-- Description of main data structures of any type:
JSON/API schemas, database tables (DDL/CQL), Avro schemas, Protobuf definitions.
Can use: inline definitions, field tables, links to external files. -->
<!-- EXAMPLE (JSON schema):
#### CreateOrderRequest

```json
{
"items": [
{
"productId": "string — product UUID from catalog",
"quantity": "integer — number of units (1 to 999)",
"price": "decimal — price per unit in currency"
}
],
"deliveryAddress": "string — delivery address (10 to 500 characters)",
"promoCode": "string (optional) — promo code for discount"
}
```
-->
<!-- EXAMPLE (field table):
#### OrderCreatedEvent (Kafka)

| Field | Type | Required | Description |
|---|---|---|---|
| orderId | string (UUID) | required | Order identifier |
| userId | string (UUID) | required | Customer identifier |
| items | array | required | Array of items in the order |
| items[].productId | string (UUID) | required | Item ID |
| items[].quantity | integer | required | Quantity |
| items[].price | decimal | required | Price per unit |
| originalAmount | decimal | required | Order amount before discount |
| promoCode | string | optional | Applied promo code (null if not applied) |
| discountAmount | decimal | required | Discount amount (0 if no promo code) |
| finalAmount | decimal | required | Final amount to pay |
| createdAt | long (unix ms) | required | Order creation time |
-->
<!-- EXAMPLE (Database table — PostgreSQL DDL):
#### PostgreSQL: orders

```sql
CREATE TABLE orders (
id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
user_id         UUID         NOT NULL,
status          VARCHAR(20)  NOT NULL DEFAULT 'CREATED',
original_amount NUMERIC(12,2) NOT NULL,
discount_amount NUMERIC(12,2) NOT NULL DEFAULT 0,
final_amount    NUMERIC(12,2) NOT NULL,
promo_code_id   BIGINT       REFERENCES promo_codes(id),
payment_id      VARCHAR(100),
delivery_address TEXT         NOT NULL,
created_at      TIMESTAMP    NOT NULL DEFAULT now(),
updated_at      TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_user_id ON orders (user_id);
CREATE INDEX idx_orders_status ON orders (status);
CREATE INDEX idx_orders_payment_id ON orders (payment_id);

CREATE TABLE order_items (
id         BIGSERIAL PRIMARY KEY,
order_id   UUID         NOT NULL REFERENCES orders(id),
product_id UUID         NOT NULL,
quantity   INT          NOT NULL,
price      NUMERIC(10,2) NOT NULL,
subtotal   NUMERIC(12,2) NOT NULL
);

CREATE INDEX idx_order_items_order_id ON order_items (order_id);
```

| Table / Column | Type | PK / FK | Description |
|---|---|---|---|
| orders.id | UUID | PK | Unique order identifier |
| orders.user_id | UUID | — | Customer ID |
| orders.status | VARCHAR(20) | — | Status: CREATED, PENDING_PAYMENT, PAID, SHIPPED, DELIVERED, CANCELLED |
| orders.original_amount | NUMERIC(12,2) | — | Amount before discount |
| orders.discount_amount | NUMERIC(12,2) | — | Discount amount |
| orders.final_amount | NUMERIC(12,2) | — | Final amount to pay |
| orders.promo_code_id | BIGINT | FK → promo_codes | Reference to used promo code |
| orders.payment_id | VARCHAR(100) | — | Payment ID from payment-service |
| order_items.order_id | UUID | FK → orders | Reference to order |
| order_items.product_id | UUID | — | Item ID from catalog |
| order_items.quantity | INT | — | Number of units |
| order_items.price | NUMERIC(10,2) | — | Price per unit |
| order_items.subtotal | NUMERIC(12,2) | — | Line total (price * quantity) |
-->
<!-- EXAMPLE (Avro schema):
#### Avro: OrderCreatedEvent

```json
{
"type": "record",
"name": "OrderCreatedEvent",
"namespace": "com.shop.orders",
"fields": [
{"name": "orderId", "type": "string"},
{"name": "userId", "type": "string"},
{"name": "originalAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 12, "scale": 2}},
{"name": "promoCode", "type": ["null", "string"], "default": null},
{"name": "discountAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 12, "scale": 2}},
{"name": "finalAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 12, "scale": 2}},
{"name": "createdAt", "type": "long", "logicalType": "timestamp-millis"}
]
}
```

Full schema: [schemas/order-created-event.avsc](schemas/order-created-event.avsc)
-->
<!-- EXAMPLE (Protobuf):
#### Proto: PaymentService

```protobuf
message CreatePaymentRequest {
string order_id = 1;
double amount = 2;
string currency = 3;
}

message CreatePaymentResponse {
string payment_id = 1;
string status = 2;
string redirect_url = 3;
}
```

Full proto file: [proto/payment_service.proto](proto/payment_service.proto)
-->
<!-- EXAMPLE (external file link):
Full schema: [schemas/OrderCreatedEvent.json](schemas/OrderCreatedEvent.json)
-->

### 2.7. Configuration Requirements

<!-- Configuration parameters table -->

|        Parameter        |    Required     |           Type           |    Default     |     Description      |
|-------------------------|-----------------|--------------------------|----------------|----------------------|
| <!-- parameter name --> | <!-- yes/no --> | <!-- string/int/bool --> | <!-- value --> | <!-- description --> |

<!-- EXAMPLE:
| Parameter | Required | Type | Default | Description |
|---|---|---|---|---|
| spring.datasource.url | yes | string | — | PostgreSQL connection URL |
| spring.datasource.hikari.maximum-pool-size | no | int | 20 | Maximum connections in pool |
| spring.kafka.bootstrap-servers | yes | string | — | Kafka broker addresses |
| spring.kafka.listener.concurrency | no | int | 3 | Number of consumer threads |
| shop.promo.enabled | no | bool | false | Feature flag: enable promo code system |
| shop.promo.max-discount-percent | no | int | 50 | Maximum discount percentage |
| shop.order.payment-retry-window-min | no | int | 30 | Time for payment retry (minutes) |
| shop.order.cancel-expired-cron | no | string | "0 */5 * * * *" | Schedule for cancelling unpaid orders |
| payment-service.grpc.host | yes | string | — | gRPC payment-service host |
| payment-service.grpc.port | no | int | 9090 | gRPC payment-service port |
| payment-service.grpc.timeout-ms | no | int | 10000 | payment-service call timeout (ms) |
-->

### 2.8. Logging

<!-- Logging events table. Levels per common-requirements.md section 1. -->

|             Level              |           Event            |      Message Format       |
|--------------------------------|----------------------------|---------------------------|
| <!-- ERROR/WARN/INFO/DEBUG --> | <!-- event description --> | <!-- message template --> |

<!-- EXAMPLE:
| Level | Event | Message Format |
|---|---|---|
| ERROR | Error calling payment-service | "Payment service error for orderId={}: {}" |
| ERROR | Error saving order to DB | "Failed to save order: {}" |
| ERROR | Error sending event to Kafka | "Failed to send event to topic={}: {}" |
| WARN | Payment attempt for order in wrong status | "Invalid status transition for orderId={}: {} -> PENDING_PAYMENT" |
| WARN | Attempt to use expired promo code | "Expired promo code: code={}, validUntil={}" |
| INFO | Order successfully created | "Order created: orderId={}, userId={}, finalAmount={}" |
| INFO | Order status changed | "Order status changed: orderId={}, {} -> {}" |
| INFO | Promo code successfully applied | "Promo applied: orderId={}, code={}, discount={}" |
| DEBUG | Starting promo code validation | "Validating promo code={} for orderId={}" |
| DEBUG | Sending gRPC request to payment-service | "Calling payment-service: orderId={}, amount={}" |
-->

### 2.9. Input Validation

<!-- Input data validation rules -->

| Field (JSON path) |             Validation Type             |     Rule      | Error Message |  Error Code   |
|-------------------|-----------------------------------------|---------------|---------------|---------------|
| <!-- $.field -->  | <!-- format/range/regex/enum/length --> | <!-- rule --> | <!-- text --> | <!-- code --> |

<!-- EXAMPLE:
| Field (JSON path) | Validation Type | Rule | Error Message | Error Code |
|---|---|---|---|---|
| $.items | length | Minimum 1 element, maximum 100 | "Order must contain between 1 and 100 items" | 1003 |
| $.items[*].productId | format | UUID v4 | "productId must be in UUID v4 format" | 1003 |
| $.items[*].quantity | range | 1 to 999 | "Item quantity must be between 1 and 999" | 1003 |
| $.deliveryAddress | length | 10 to 500 characters | "Delivery address must be between 10 and 500 characters" | 1003 |
| $.promoCode | regex | `^[A-Z0-9_-]+$` | "Promo code may only contain uppercase letters, digits, hyphens and underscores" | 1003 |
-->

### 2.10. Error Handling

<!-- Error codes tied to API endpoints. For each endpoint — error table. -->

|    HTTP Code     |  Error Code   |      errorCode       |        Scenario         |    Client Action    |
|------------------|---------------|----------------------|-------------------------|---------------------|
| <!-- 4xx/5xx --> | <!-- XXXX --> | <!-- string code --> | <!-- when it occurs --> | <!-- what to do --> |

<!-- EXAMPLE:

**POST /api/v1/orders:**

| HTTP Code | Error Code | errorCode | Scenario | Client Action |
|---|---|---|---|---|
| 400 | 1003 | INVALID_INPUT | Invalid request format | Fix request body |
| 400 | 1003 | INVALID_PROMO_CODE | Promo code not found | Verify code correctness |
| 400 | 1003 | PROMO_CODE_EXPIRED | Promo code expired | Use a different promo code |
| 404 | 1004 | PRODUCT_NOT_FOUND | Product not found in catalog | Verify productId |
| 500 | 1001 | INTERNAL_ERROR | Service error | Retry after a few seconds |

**GET /api/v1/orders/{orderId}:**

| HTTP Code | Error Code | errorCode | Scenario | Client Action |
|---|---|---|---|---|
| 404 | 1004 | ORDER_NOT_FOUND | Order not found | Verify orderId |

Response format (RFC 7807):
```json
{
"type": "https://api.shop.com/problems/bad-request",
"title": "Promo code not found",
"detail": "Promo code WINTER2025 does not exist",
"errorCode": "INVALID_PROMO_CODE",
"status": 400,
"instance": "/api/v1/orders"
}
```
-->

### 2.11. Headers and Metadata

<!-- HTTP headers, Kafka headers, gRPC metadata used by the service -->

|        Transport         |     Name      |         Direction         |          Required          |     Format      |   Description    |
|--------------------------|---------------|---------------------------|----------------------------|-----------------|------------------|
| <!-- HTTP/Kafka/gRPC --> | <!-- name --> | <!-- request/response --> | <!-- required/optional --> | <!-- format --> | <!-- purpose --> |

<!-- EXAMPLE:
| Transport | Name | Direction | Required | Format | Description |
|---|---|---|---|---|---|
| HTTP | X-Request-ID | request | required | UUID | Request identifier for tracing |
| HTTP | X-User-ID | request | required | UUID | User identifier from JWT |
| HTTP | X-Promo-Applied | response | optional | boolean | Indicates promo code was applied |
| Kafka | ORDER_SOURCE | producer header | optional | string | Order channel: WEB / MOBILE / API |
| gRPC | x-order-id | request metadata | required | string | Order identifier for payment-service |
-->

---

## 3. Acceptance Criteria Recommendations

<!-- Verifiable conditions that determine correctness of implementation.
Format: WHEN/THEN. Each criterion must be testable.
Cover: main scenario, edge cases, error scenarios.
If the service is new — describe criteria for all key scenarios from 2.1-2.2. -->

|        #        |            WHEN             |           THEN           |
|-----------------|-----------------------------|--------------------------|
| <!-- number --> | <!-- condition / action --> | <!-- expected result --> |

<!-- EXAMPLE:
| # | WHEN | THEN |
|---|---|---|
| 1 | Client sends POST /api/v1/orders with a valid list of items and address | Order created in DB with status CREATED, response 201 with orderId and finalAmount, ORDERS.CREATED event sent to Kafka |
| 2 | Valid promo code "SUMMER2026" (10% discount) passed in order creation request | Discount calculated, discountAmount > 0, finalAmount = originalAmount - discountAmount, promo code usage_count incremented by 1 |
| 3 | Non-existent promo code passed | Response 400, errorCode = INVALID_PROMO_CODE, order NOT created |
| 4 | Client calls POST /api/v1/orders/{orderId}/pay for order in CREATED status | Status changed to PENDING_PAYMENT, payment-service called via gRPC, redirectUrl returned to client |
| 5 | payment-service sends PAYMENTS.COMPLETED event | Order status changed to PAID, ORDERS.PAID event sent to Kafka |
| 6 | payment-service sends PAYMENTS.FAILED event | Order status changed to PAYMENT_FAILED |
| 7 | Order in PAYMENT_FAILED status for more than 30 minutes | Cron task transitions order to CANCELLED status, ORDERS.CANCELLED event sent to Kafka |
| 8 | Client calls /pay for order in PAID status | Response 409 CONFLICT, status NOT changed |
| 9 | PostgreSQL unavailable during order creation | Response 503, ERROR "Failed to save order" in logs |
| 10 | payment-service unavailable (gRPC timeout) | Response 503, ERROR "Payment service error" in logs, order status NOT changed |
-->

---

## 4. Non-Functional Requirements

### 4.1. Security

<!-- Security requirements. Standard set for all platform services: -->
1. Communication between services MUST use TLS version 1.2 or higher.
2. Inter-service communication MUST use mTLS (mutual TLS).
3. Access control MUST be based on ACL by DN (Distinguished Name) from certificates.
4. Secrets (passwords, keys, tokens) MUST NOT be stored in code, configuration files, or environment variables.
5. Confidential information K1/K2 MUST be masked in logs at 100%.

<!-- Additional service-specific requirements: -->
<!-- EXAMPLE:
6. Customer authorization MUST be performed via auth-service using JWT token.
7. A client can only view and pay for their own orders (userId check from JWT).
8. Delivery address MUST be masked in logs (first 10 characters + `...`).
-->

### 4.2. Performance

<!-- Performance requirements, timeouts, limits -->

|     Parameter      |     Value      |     Description      |
|--------------------|----------------|----------------------|
| <!-- parameter --> | <!-- value --> | <!-- description --> |

<!-- EXAMPLE:
| Parameter | Value | Description |
|---|---|---|
| max.request.size | 1 MB | Maximum incoming request size |
| order.create.timeout | 5000 ms | Order creation timeout (including promo code validation) |
| payment.grpc.timeout | 10000 ms | payment-service call timeout |
| kafka.delivery.timeout.ms | 5000 | Kafka message delivery timeout |
| db.query.timeout | 5000 ms | PostgreSQL query timeout |
| max.items.per.order | 100 | Maximum number of items in a single order |
-->

### 4.3. Reliability

<!-- Fault tolerance and reliability requirements -->
<!-- EXAMPLE:
1. The service MUST support graceful shutdown.
2. Minimum pod count — 3 (multi-pod deployment).
3. Pod Disruption Budget: minimum 2 available pods.
4. Readiness Probe MUST check PostgreSQL and Kafka availability.
5. When payment-service is unavailable — return 503, order remains in CREATED status.
6. On Kafka event delivery failure — retry 3 times with backoff, then log ERROR.
7. Duplicate Kafka events are handled idempotently (by orderId).
-->

### 4.4. Monitoring

<!-- Service metrics description -->

#### 4.4.1. Standard Metrics

<!-- If the service uses Spring Boot Actuator or similar framework -->
<!-- EXAMPLE:
The service provides standard Spring Boot Actuator metrics:
- `http_server_requests_seconds` — HTTP request duration
- `jvm_memory_used_bytes` — JVM memory usage
- `hikaricp_connections_active` — active DB connections

Health-check endpoints:
- `/actuator/health` — overall status
- `/actuator/health/readiness` — readiness to receive traffic (PostgreSQL + Kafka)
- `/actuator/health/liveness` — process liveness
-->

#### 4.4.2. Custom Business Metrics

<!-- For each metric: name, type, description, tags -->

##### <!-- Metric name -->

- **Type**: <!-- Counter / Gauge / Timer (Histogram) -->
- **Description**: <!-- what it measures -->

|        Tag        |           Required            |     Description      |  Example Value   |
|-------------------|-------------------------------|----------------------|------------------|
| <!-- tag name --> | <!-- Required/Conditional --> | <!-- description --> | <!-- example --> |

**Example value:**

```
metric_name{tag1="value1", tag2="value2"} 1
```

<!-- EXAMPLE:
##### shop.orders.created

- **Type**: Counter
- **Description**: Counter of created orders

| Tag | Required | Description | Example Value |
|---|---|---|---|
| promoApplied | Required | Whether a promo code was applied | "true" / "false" |
| channel | Conditional | Order channel | "WEB" / "MOBILE" |

**Example value:**
```
shop_orders_created{promoApplied="true", channel="WEB"} 42
```

##### shop.orders.payment_duration

- **Type**: Timer (Histogram)
- **Description**: payment-service gRPC call duration

| Tag | Required | Description | Example Value |
|---|---|---|---|
| status | Required | Call result | "SUCCESS" / "TIMEOUT" / "ERROR" |

**Example value:**
```
shop_orders_payment_duration_seconds_bucket{status="SUCCESS", le="1.0"} 150
```
-->

#### 4.4.3. Calculated Metrics

<!-- Metrics calculated on the monitoring system side (Prometheus/Grafana) -->

|     Category      |    Metric     |     Description      |     Formula     |
|-------------------|---------------|----------------------|-----------------|
| <!-- category --> | <!-- name --> | <!-- description --> | <!-- PromQL --> |

<!-- EXAMPLE:
| Category | Metric | Description | Formula |
|---|---|---|---|
| Conversion | shop_order_payment_success_rate | Percentage of successfully paid orders | `(sum(rate(shop_orders_created{status="PAID"}[5m])) / sum(rate(shop_orders_created[5m]))) * 100` |
| Promo codes | shop_promo_usage_rate | Percentage of orders with promo code | `(sum(rate(shop_orders_created{promoApplied="true"}[5m])) / sum(rate(shop_orders_created[5m]))) * 100` |
| Errors | shop_5xx_error_rate | Server error percentage | `(sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))) * 100` |
-->

### 4.5. Audit

<!-- Audit requirements (if applicable to the service) -->
<!-- EXAMPLE:
1. All customer actions (order creation, payment, cancellation) MUST be recorded in the audit log.
2. Audit entries MUST contain: userId (from JWT), action, orderId, timestamp, result (success/error).
3. Audit log MUST NOT contain delivery address or payment data.
4. All administrator actions (promo code creation/deactivation) MUST be recorded separately.
-->
