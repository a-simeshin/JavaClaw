# Change: <!-- change name -->

> **Status**: Draft | Under Review | Approved | In Progress | Implemented | Archived
>
> **Created**: <!-- YYYY-MM-DD -->
>
> **Author**: <!-- analyst name -->
>
> **Version**: 1.0
>
> **Target specification**: <!-- path to main specification, e.g. openspec/specs/<service-name>/<service-name>.md -->

---

## 1. Proposal

### Purpose of Change

<!-- Why is this change needed? What problem are we solving? -->
<!-- EXAMPLE:
Add a promo code system to order-service so that customers can apply
discount codes when placing orders. This will allow marketing to launch promotions
and increase order completion conversion by 15%.
-->

### Initiator

<!-- Who requested the change (team, ticket, business requirement) -->
<!-- EXAMPLE:
Marketing team, ticket SHOP-1234. Problem: no mechanism for applying
promo codes — promotions have to be run manually by changing prices in the catalog.
-->

### Affected Components

<!-- List of services, APIs, data schemas that will be changed.
Checkbox is checked when impact on the component is confirmed. -->
- [ ] <!-- component 1 -->
- [ ] <!-- component 2 -->

<!-- EXAMPLE:
- [x] order-service — applying promo code during order placement, recalculating total
- [x] payment-service — passing the final amount with discount for payment
- [ ] notification-service — sending email with discount details (separate CR)
-->

### Priority

<!-- critical / high / medium / low -->

### Backward Compatibility

<!-- yes / no — if no, specify migration plan in section 9 -->

---

## 2. Business Logic

<!-- Description of new/changed processing logic: routing conditions, branching rules,
data transformation rules. This section explains to the developer agent
HOW the service should behave, not just WHAT data it accepts/returns.

Format: numbered rules. For conditions use IF/THEN/ELSE.
If no logic changes, state: "No changes" -->
<!-- EXAMPLE:
### Promo Code Application Rules for Order Placement

1. When placing an order, the customer MAY pass a `promoCode` field in the request.

2. **IF** the `promoCode` field is not passed or is empty,
**THEN** the order is placed at regular prices without a discount.

3. **IF** `promoCode` is passed,
**THEN** order-service validates the promo code in the `promo_codes` table:
- **IF** the promo code is not found in the table,
**THEN** return error 400 with message "Promo code not found".
- **IF** the promo code is found but `valid_until < now()`,
**THEN** return error 400 with message "Promo code has expired".
- **IF** the promo code is found but `usage_count >= max_usages`,
**THEN** return error 400 with message "Promo code is no longer active".
- **IF** the promo code is found and valid,
**THEN** apply discount to the order (see calculation rules below).

4. **IF** `discount_type = 'PERCENT'`,
**THEN** discount = `total_amount * discount_value / 100`.
**IF** `discount_type = 'FIXED'`,
**THEN** discount = `discount_value` (in currency units).

5. **IF** the calculated discount exceeds the order amount,
**THEN** the discount is capped at the order amount (total = 0, but not negative).

6. After successful promo code application:
- `usage_count` in the `promo_codes` table is incremented by 1.
- The order saves `promo_code_id` and `discount_amount`.
- The `ORDERS.CREATED` Kafka event contains `promoCode` and `discountAmount` fields.
-->

---

## 3. Data Model Changes

<!-- Description of changes to any data models: JSON/API schemas, database tables (DDL),
Avro schemas, Protobuf definitions, XML schemas.
If no changes, state: "No changes" -->

### ADDED

<!-- New fields, entities, tables, columns -->

|     Entity / Field     |        Data Type        |          Required          |    Default     |     Example      |  Rationale   |
|------------------------|-------------------------|----------------------------|----------------|------------------|--------------|
| <!-- path.to.field --> | <!-- string/int/... --> | <!-- required/optional --> | <!-- value --> | <!-- example --> | <!-- why --> |

<!-- EXAMPLE (API/JSON schema):
| Entity / Field | Data Type | Required | Default | Example | Rationale |
|---|---|---|---|---|---|
| Order.promoCode | string | optional | null | "SUMMER2026" | Promo code applied to the order |
| Order.discountAmount | decimal | optional | 0.00 | 150.00 | Discount amount from the promo code (in currency units) |
| Order.finalAmount | decimal | required | — | 1350.00 | Final order amount after discount |
-->
<!-- EXAMPLE (Database table — PostgreSQL DDL):
```sql
CREATE TABLE promo_codes (
id            BIGSERIAL PRIMARY KEY,
code          VARCHAR(50)  NOT NULL UNIQUE,
discount_type VARCHAR(10)  NOT NULL CHECK (discount_type IN ('PERCENT', 'FIXED')),
discount_value NUMERIC(10,2) NOT NULL,
valid_from    TIMESTAMP    NOT NULL DEFAULT now(),
valid_until   TIMESTAMP    NOT NULL,
max_usages    INT          NOT NULL DEFAULT 1000,
usage_count   INT          NOT NULL DEFAULT 0,
created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_promo_codes_code ON promo_codes (code);
```
| Table / Column | Type | PK | Description |
|---|---|---|---|
| promo_codes.id | BIGSERIAL | PK | Unique promo code identifier |
| promo_codes.code | VARCHAR(50) | UNIQUE | Text code of the promo (e.g., "SUMMER2026") |
| promo_codes.discount_type | VARCHAR(10) | — | Discount type: PERCENT or FIXED |
| promo_codes.discount_value | NUMERIC(10,2) | — | Discount value (percentage or amount in currency) |
| promo_codes.valid_until | TIMESTAMP | — | Promo code expiration date |
| promo_codes.max_usages | INT | — | Maximum number of uses |
| promo_codes.usage_count | INT | — | Current number of uses |
-->
<!-- EXAMPLE (Avro schema):
```json
{
"type": "record",
"name": "OrderCreatedEvent",
"namespace": "com.shop.orders",
"fields": [
{"name": "orderId", "type": "string"},
{"name": "userId", "type": "string"},
{"name": "totalAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}},
{"name": "promoCode", "type": ["null", "string"], "default": null},
{"name": "discountAmount", "type": {"type": "bytes", "logicalType": "decimal", "precision": 10, "scale": 2}},
{"name": "createdAt", "type": "long", "logicalType": "timestamp-millis"}
]
}
```
-->
<!-- EXAMPLE (Protobuf):
```protobuf
message PromoCodeValidation {
string code = 1;
string discount_type = 2;
double discount_value = 3;
bool is_valid = 4;
optional string rejection_reason = 5;
}
```
-->

### MODIFIED

<!-- Changed fields/columns/definitions — MUST specify BEFORE and AFTER -->

|     Entity / Field     |              Before              |              After               |  Rationale   |
|------------------------|----------------------------------|----------------------------------|--------------|
| <!-- path.to.field --> | <!-- type, required, default --> | <!-- type, required, default --> | <!-- why --> |

<!-- EXAMPLE (API):
| Entity / Field | Before | After | Rationale |
|---|---|---|---|
| CreateOrderRequest.items | array, required | array, required (no change) | — |
| CreateOrderResponse.totalAmount | decimal, required | decimal, required (renamed to originalAmount) | Distinguish between pre- and post-discount amounts |
-->
<!-- EXAMPLE (DDL):
```sql
ALTER TABLE orders ADD COLUMN promo_code_id BIGINT REFERENCES promo_codes(id);
ALTER TABLE orders ADD COLUMN discount_amount NUMERIC(10,2) NOT NULL DEFAULT 0;
```
| Table / Column | Before | After | Rationale |
|---|---|---|---|
| orders | no promo_code_id column | + promo_code_id BIGINT (FK → promo_codes) | Link order to the used promo code |
| orders | no discount_amount column | + discount_amount NUMERIC(10,2) DEFAULT 0 | Discount amount for reporting |
-->

### REMOVED

<!-- Removed fields/tables/columns -->

|     Entity / Field     | Removal Reason  |        Migration        |
|------------------------|-----------------|-------------------------|
| <!-- path.to.field --> | <!-- reason --> | <!-- how to migrate --> |

<!-- EXAMPLE:
| Entity / Field | Removal Reason | Migration |
|---|---|---|
| Order.manualDiscount | Replaced by promo code system | Stop using; existing orders with manualDiscount remain in DB as-is |
| orders.legacy_coupon_code (column) | Old coupon system decommissioned | DROP COLUMN after confirming reports don't use this field |
-->

---

## 4. Integration Changes

<!-- If no changes, state: "No changes" -->

### ADDED

<!-- New endpoints / Kafka topics / gRPC methods / SSE streams / cron tasks -->

#### <!-- Type: Method PATH / Kafka topic / gRPC method / SSE endpoint / Cron task -->

- **Description**: <!-- what it does -->
- **Request**:

```json
{
}
```

- **Response**:

```json
{
}
```

- **Timeout**: <!-- ms -->
- **Retry**: <!-- policy -->

<!-- EXAMPLE (REST):
#### POST /api/v1/orders/{orderId}/apply-promo — Apply promo code to order

- **Description**: Validates a promo code and applies a discount to the specified order
- **Request**:
```json
{
"promoCode": "SUMMER2026"
}
```
- **Response (200)**:
```json
{
"orderId": "ord-123",
"promoCode": "SUMMER2026",
"discountType": "PERCENT",
"discountValue": 10,
"discountAmount": 150.00,
"originalAmount": 1500.00,
"finalAmount": 1350.00
}
```
- **Timeout**: 5000 ms
- **Retry**: none (client can retry manually)
-->
<!-- EXAMPLE (Kafka):
#### Kafka: ORDERS.CREATED (updated producer)

- **Description**: Order creation event, now includes promo code information
- **Direction**: producer
- **Format**: JSON (OrderCreatedEvent schema)
- **Timeout**: 5000 ms (delivery.timeout.ms)
- **Retry**: Built-in Kafka producer retry
-->
<!-- EXAMPLE (gRPC):
#### gRPC: PaymentService.CreatePayment

- **Description**: Create payment for the final order amount (after discount)
- **Proto**:
```protobuf
service PaymentService {
rpc CreatePayment (CreatePaymentRequest) returns (CreatePaymentResponse);
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
- **Timeout**: 10000 ms
- **Retry**: 2 attempts with backoff 1s/3s
-->
<!-- EXAMPLE (SSE):
#### SSE: GET /api/v1/orders/{orderId}/status-stream — Order status streaming

- **Description**: Client subscribes to real-time order status updates
- **Request**: `GET /api/v1/orders/{orderId}/status-stream` with `Accept: text/event-stream`
- **Response**: Stream of SSE events:
```
event: ORDER_STATUS_CHANGED
id: evt-001
data: {"orderId": "ord-123", "status": "PAID", "updatedAt": "2026-04-03T12:00:00Z"}

event: ORDER_STATUS_CHANGED
id: evt-002
data: {"orderId": "ord-123", "status": "SHIPPED", "updatedAt": "2026-04-03T14:30:00Z"}
```
- **Timeout**: 300000 ms (5 minutes, then reconnect)
- **Reconnect**: Client reconnects with `Last-Event-ID`
-->
<!-- EXAMPLE (Cron/Batch):
#### Cron: Expired Promo Codes Cleanup — Deactivate expired promo codes

- **Description**: Periodic marking of expired promo codes as inactive
- **Schedule**: `0 0 1 * * *` (every day at 01:00)
- **Window duration**: 60 sec
- **Max retry**: 3 attempts per batch
- **Backoff**: 5000 ms between attempts
-->

### MODIFIED

<!-- Changed endpoints — BEFORE and AFTER -->

#### <!-- Method PATH -->

- **What changed**: <!-- description -->
- **Request (before)**: <!-- schema or diff -->
- **Request (after)**: <!-- schema or diff -->
- **Response (before)**: <!-- schema or diff -->
- **Response (after)**: <!-- schema or diff -->

<!-- EXAMPLE:
#### POST /api/v1/orders — Create order

- **What changed**: Added optional promoCode field to request, added discountAmount and finalAmount fields to response
- **Request (before)**: `{ "items": [...], "deliveryAddress": "..." }`
- **Request (after)**: `{ "items": [...], "deliveryAddress": "...", "promoCode": "SUMMER2026" }` — promoCode field is optional
- **Response (before)**: `{ "orderId": "...", "totalAmount": 1500.00, "status": "CREATED" }`
- **Response (after)**: `{ "orderId": "...", "originalAmount": 1500.00, "discountAmount": 150.00, "finalAmount": 1350.00, "promoCode": "SUMMER2026", "status": "CREATED" }`
-->

### REMOVED

<!-- Removed endpoints -->

|       Endpoint       |     Reason      |     Replacement      |
|----------------------|-----------------|----------------------|
| <!-- method path --> | <!-- reason --> | <!-- alternative --> |

---

## 5. Error Handling

<!-- If no changes, state: "No changes"
Error codes MUST follow the coding system from common-requirements.md:
- Code structure: X Y Z (X=event type, Y=module, Z=identifier)
- Reserved ranges: 0-General(1000-1099), 1-Authentication(1100-1199),
2-Authorization(1200-1299), 3-Agents(1300-1399), 4-External integrations(1400-1499)
- Response format: RFC 7807 (application/problem+json)
-->

|  Error Code   |   HTTP Status    |                   errorCode                   |        Scenario         |    Client Action    |
|---------------|------------------|-----------------------------------------------|-------------------------|---------------------|
| <!-- XXXX --> | <!-- 4xx/5xx --> | <!-- string code from common-requirements --> | <!-- when it occurs --> | <!-- what to do --> |

<!-- EXAMPLE:
| Error Code | HTTP Status | errorCode | Scenario | Client Action |
|---|---|---|---|---|
| 1003 | 400 | INVALID_PROMO_CODE | Promo code not found in database | Verify the entered code is correct |
| 1003 | 400 | PROMO_CODE_EXPIRED | Promo code validity period has ended | Use a different promo code |
| 1003 | 400 | PROMO_CODE_LIMIT_REACHED | Promo code used the maximum number of times | Use a different promo code |
| 1003 | 400 | INVALID_INPUT | Invalid request format (e.g., empty items) | Fix request body per schema |
| 1001 | 500 | INTERNAL_ERROR | Error accessing DB or Kafka | Retry the request after a few seconds |

Error response format (RFC 7807):
```json
{
"type": "https://api.shop.com/problems/bad-request",
"title": "Promo code not found",
"detail": "Promo code WINTER2025 does not exist or has been removed",
"errorCode": "INVALID_PROMO_CODE",
"status": 400,
"instance": "/api/v1/orders/ord-123/apply-promo"
}
```
-->

---

## 6. Headers and Metadata

<!-- Changes to headers and metadata for any transport:
HTTP headers, Kafka headers, gRPC metadata, SSE event fields, MCP protocol headers.
If no changes, state: "No changes" -->

|            Operation            |            Transport             |     Name      |         Direction         |          Required          |     Format      |     Example      |   Purpose    |
|---------------------------------|----------------------------------|---------------|---------------------------|----------------------------|-----------------|------------------|--------------|
| <!-- ADDED/MODIFIED/REMOVED --> | <!-- HTTP/Kafka/gRPC/SSE/MCP --> | <!-- name --> | <!-- request/response --> | <!-- required/optional --> | <!-- format --> | <!-- example --> | <!-- why --> |

<!-- EXAMPLE (HTTP):
| Operation | Transport | Name | Direction | Required | Format | Example | Purpose |
|---|---|---|---|---|---|---|---|
| ADDED | HTTP | X-Promo-Applied | response | optional | boolean | true | Indicates a promo code was applied to the order |
| ADDED | HTTP | X-Discount-Amount | response | optional | decimal | 150.00 | Discount amount for client-side logging |
| MODIFIED | HTTP | X-Request-ID | request | required (was optional) | UUID | 550e8400-... | Request tracing |
-->
<!-- EXAMPLE (Kafka):
| Operation | Transport | Name | Direction | Required | Format | Example | Purpose |
|---|---|---|---|---|---|---|---|
| ADDED | Kafka | ORDER_SOURCE | producer header | optional | string | "WEB" / "MOBILE" / "API" | Channel through which the order was placed |
| ADDED | Kafka | PROMO_APPLIED | producer header | optional | string | "true" / "false" | Whether a promo code was applied |
-->
<!-- EXAMPLE (gRPC metadata):
| Operation | Transport | Name | Direction | Required | Format | Example | Purpose |
|---|---|---|---|---|---|---|---|
| ADDED | gRPC | x-order-id | request metadata | required | string | "ord-123" | Order identifier for the payment service |
-->
<!-- EXAMPLE (SSE event fields):
| Operation | Transport | Name | Direction | Required | Format | Example | Purpose |
|---|---|---|---|---|---|---|---|
| ADDED | SSE | event: ORDER_STATUS_CHANGED | response | required | string | ORDER_STATUS_CHANGED | Order status change event |
| ADDED | SSE | retry: | response | optional | integer (ms) | 5000 | Client reconnection interval |
-->
<!-- EXAMPLE (MCP):
| Operation | Transport | Name | Direction | Required | Format | Example | Purpose |
|---|---|---|---|---|---|---|---|
| ADDED | MCP/HTTP | MCP-Protocol-Version | request | required | string | 2025-06-18 | MCP protocol version |
| ADDED | MCP/HTTP | x-agent-id | request | required | string | "order-agent" | Identifier of the called agent |
-->

---

## 7. Input Validation

<!-- If no changes, state: "No changes" -->

| Field (JSON path) |             Validation Type             |     Rule      | Error Message |  Error Code   |
|-------------------|-----------------------------------------|---------------|---------------|---------------|
| <!-- $.field -->  | <!-- format/range/regex/enum/length --> | <!-- rule --> | <!-- text --> | <!-- code --> |

<!-- EXAMPLE:
| Field (JSON path) | Validation Type | Rule | Error Message | Error Code |
|---|---|---|---|---|
| $.promoCode | length | 3 to 50 characters | "Promo code must be between 3 and 50 characters" | 1003 |
| $.promoCode | regex | `^[A-Z0-9_-]+$` (uppercase Latin letters, digits, hyphen, underscore only) | "Promo code may only contain uppercase letters, digits, hyphens and underscores" | 1003 |
| $.items | length | Minimum 1 element, maximum 100 | "Order must contain between 1 and 100 items" | 1003 |
| $.items[*].productId | format | UUID v4 | "productId must be in UUID v4 format" | 1003 |
| $.items[*].quantity | range | 1 to 999 | "Item quantity must be between 1 and 999" | 1003 |
| $.deliveryAddress | length | 10 to 500 characters | "Delivery address must be between 10 and 500 characters" | 1003 |
-->

---

## 8. Security Impact

<!-- If no changes, state: "No changes" -->
- **Authentication/authorization**: <!-- changes -->
- **New roles/permissions**: <!-- description -->
- **Data masking**: <!-- which fields, masking rules -->

<!-- EXAMPLE:
- **Authentication/authorization**: No changes — promo code is applied only for authenticated users (JWT token required)
- **New roles/permissions**: Role `promo-manager` — permission to create and deactivate promo codes via admin API
- **Data masking**: Promo codes do not contain personal data, masking not required. Delivery address `$.deliveryAddress` MUST be masked in logs (first 10 characters + `...`)
-->

---

## 9. Migration

<!-- If backward compatibility is maintained, state: "Not required" -->

### Data Migration Steps

1. <!-- step -->

### API Backward Compatibility

<!-- Description of transition period, versioning -->

### Rollback Plan

1. <!-- step -->

<!-- EXAMPLE (when migration is needed):
### Data Migration Steps
1. Create `promo_codes` table (CREATE TABLE, see section 3)
2. Add `promo_code_id` and `discount_amount` columns to `orders` table (ALTER TABLE)
3. Populate `discount_amount = 0` for all existing orders (batch UPDATE)
4. Load initial promo code set from the marketing team (INSERT)

### API Backward Compatibility
- Transition period: 2 sprints (4 weeks)
- The `promoCode` field in the request is optional — old clients continue to work without changes
- The `totalAmount` field in the response is preserved for backward compatibility (= finalAmount)
- After the transition period, the `totalAmount` field is marked deprecated

### Rollback Plan
1. Disable feature flag `shop.promo.enabled=false`
2. Restart order-service pods
3. Leave the `promo_codes` table and new columns in `orders` (they don't interfere with operation)
-->

---

## 10. Logging (new/changed events)

<!-- If no new logging events, state: "No changes"
Logging levels per common-requirements.md section 1. -->

|             Level              |                  Code                  |        Event         |  Message Format   |
|--------------------------------|----------------------------------------|----------------------|-------------------|
| <!-- ERROR/WARN/INFO/DEBUG --> | <!-- code from common-requirements --> | <!-- description --> | <!-- template --> |

<!-- EXAMPLE:
| Level | Code | Event | Message Format |
|---|---|---|---|
| ERROR | 1001 | Error writing promo code usage to DB | "Failed to increment promo usage for code={}: {}" |
| WARN | — | Attempt to use an expired promo code | "Expired promo code used: code={}, validUntil={}" |
| INFO | 3000 | Promo code successfully applied to order | "Promo code applied: orderId={}, code={}, discountAmount={}" |
| DEBUG | — | Starting promo code validation | "Validating promo code: code={}, orderId={}" |
-->

---

## 11. Monitoring (new/changed metrics)

<!-- If no new metrics, state: "No changes" -->

### New Metrics

#### <!-- metric_name -->

- **Type**: <!-- Counter / Gauge / Timer -->
- **Description**: <!-- what it measures -->

|     Tag      |           Required            |     Description      |  Example Value   |
|--------------|-------------------------------|----------------------|------------------|
| <!-- tag --> | <!-- Required/Conditional --> | <!-- description --> | <!-- example --> |

<!-- EXAMPLE:
#### shop.orders.promo_applied

- **Type**: Counter
- **Description**: Counter of orders with a successfully applied promo code

| Tag | Required | Description | Example Value |
|---|---|---|---|
| promoCode | Required | Promo code text | "SUMMER2026" |
| discountType | Required | Discount type | "PERCENT" / "FIXED" |

**Example:**
```
shop_orders_promo_applied{promoCode="SUMMER2026", discountType="PERCENT"} 42
```
-->

### New Calculated Metrics

|    Metric     |     Description      | Formula (PromQL) |
|---------------|----------------------|------------------|
| <!-- name --> | <!-- description --> | <!-- formula --> |

<!-- EXAMPLE:
| Metric | Description | Formula (PromQL) |
|---|---|---|
| shop_promo_usage_rate | % of orders with promo code over 5 min | `(sum(rate(shop_orders_promo_applied[5m])) / sum(rate(shop_orders_created_total[5m]))) * 100` |
-->

---

## 12. Configuration Changes

<!-- If no new/changed configuration parameters, state: "No changes" -->

### ADDED

|        Parameter        |    Required     |           Type           |    Default     |     Description      |
|-------------------------|-----------------|--------------------------|----------------|----------------------|
| <!-- parameter name --> | <!-- yes/no --> | <!-- string/int/bool --> | <!-- value --> | <!-- description --> |

<!-- EXAMPLE:
| Parameter | Required | Type | Default | Description |
|---|---|---|---|---|
| shop.promo.enabled | no | bool | false | Feature flag: enable promo code system |
| shop.promo.max-discount-percent | no | int | 50 | Maximum discount percentage (protection against input errors) |
| shop.promo.cleanup-cron | no | string | "0 0 1 * * *" | Schedule for cleaning up expired promo codes |
-->

### MODIFIED

|   Parameter   |         Before         |         After          |  Rationale   |
|---------------|------------------------|------------------------|--------------|
| <!-- name --> | <!-- type, default --> | <!-- type, default --> | <!-- why --> |

### REMOVED

|   Parameter   |     Reason      |        Migration        |
|---------------|-----------------|-------------------------|
| <!-- name --> | <!-- reason --> | <!-- how to migrate --> |

---

## 13. Acceptance Criteria Recommendations

<!-- Verifiable conditions by which the developer agent (or QA) determines
that the change is correctly implemented. Format: WHEN/THEN.
Each criterion must be verifiable — through a test, request, or observation.
Cover: main scenario, edge cases, error scenarios. -->

|        #        |            WHEN             |           THEN           |
|-----------------|-----------------------------|--------------------------|
| <!-- number --> | <!-- condition / action --> | <!-- expected result --> |

<!-- EXAMPLE:
| # | WHEN | THEN |
|---|---|---|
| 1 | Customer places an order for 1500 and passes valid promo code "SUMMER2026" (10% discount) | Order created, discountAmount = 150.00, finalAmount = 1350.00, Kafka event ORDERS.CREATED contains promoCode and discountAmount |
| 2 | Customer places an order without promo code (promoCode field absent) | Order created at full price, discountAmount = 0, finalAmount = totalAmount |
| 3 | Customer passes non-existent promo code "FAKEPROMO" | Response 400, errorCode = INVALID_PROMO_CODE, order NOT created |
| 4 | Customer passes promo code with expired validity (valid_until < now()) | Response 400, errorCode = PROMO_CODE_EXPIRED, order NOT created |
| 5 | Customer passes promo code where usage_count >= max_usages | Response 400, errorCode = PROMO_CODE_LIMIT_REACHED, order NOT created |
| 6 | Promo code discount (FIXED = 2000) exceeds order amount (1500) | Discount capped at order amount: discountAmount = 1500.00, finalAmount = 0.00 |
| 7 | Feature flag `shop.promo.enabled=false` | promoCode field in request is ignored, order created without discount |
-->
