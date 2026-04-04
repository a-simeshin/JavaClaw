# Спецификация на {{service-name}}

Версия спецификации - 1.0

<!-- TOC -->
* [1. Общие сведения](#1-общие-сведения)
* [2. Функциональные требования](#2-функциональные-требования)
  * [2.1. Основной сценарий использования](#21-основной-сценарий-использования)
  * [2.2. Альтернативные и ошибочные сценарии](#22-альтернативные-и-ошибочные-сценарии)
  * [2.3. Бизнес-правила и логика обработки](#23-бизнес-правила-и-логика-обработки)
  * [2.4. API](#24-api)
  * [2.5. Интеграции](#25-интеграции)
  * [2.6. Модели данных](#26-модели-данных)
  * [2.7. Требования к конфигурационному файлу](#27-требования-к-конфигурационному-файлу)
  * [2.8. Логирование](#28-логирование)
  * [2.9. Валидация входящих значений](#29-валидация-входящих-значений)
  * [2.10. Обработка ошибок](#210-обработка-ошибок)
  * [2.11. Хедеры и метаданные](#211-хедеры-и-метаданные)
* [3. Рекомендации к критериям приёмки](#3-критерии-приёмки)
* [4. Нефункциональные требования](#4-нефункциональные-требования)
  * [4.1. Безопасность](#41-безопасность)
  * [4.2. Производительность](#42-производительность)
  * [4.3. Надежность](#43-надежность)
  * [4.4. Мониторинг](#44-мониторинг)
  * [4.5. Аудит](#45-аудит)
<!-- TOC -->

---

## 1. Общие сведения

<!-- Описание назначения сервиса, его роли в архитектуре платформы -->

**{{service-name}}** — микросервис, предназначенный для <!-- назначение сервиса -->.

<!-- ПРИМЕР:
**order-service** — микросервис платформы интернет-магазина, предназначенный для
управления жизненным циклом заказов: создание заказа, применение промокодов,
расчёт итоговой стоимости, передача на оплату и отслеживание статуса доставки.
Сервис принимает запросы от клиентских приложений (веб, мобильное), взаимодействует
с payment-service для оплаты и отправляет события в Kafka для notification-service.
-->

---

## 2. Функциональные требования

### 2.1. Основной сценарий использования

<!-- Описание основного потока обработки. Рекомендуется использовать
     нумерованные шаги и ссылки на PlantUML-диаграммы последовательности. -->

<!-- Для UML-диаграмм используйте формат:
     ![Название диаграммы](diagrams/название.puml)
-->

#### 2.1.1. <!-- Название первого шага -->

<!-- Описание шага. Для каждого шага указывать:
     - Входные данные (формат, источник)
     - Обработка (что происходит)
     - Выходные данные (формат, получатель)
-->

<!-- ПРИМЕР:
#### 2.1.1. Создание заказа

1. Клиент отправляет POST-запрос на `/api/v1/orders` с массивом товаров, адресом доставки и (опционально) промокодом.
2. order-service валидирует входные данные (наличие товаров, корректность productId, формат адреса).
3. Рассчитывает общую стоимость заказа на основе цен из каталога.
4. Если передан промокод — проверяет его валидность и применяет скидку (см. раздел 2.3).
5. Сохраняет заказ в PostgreSQL со статусом `CREATED`.
6. Отправляет событие `ORDERS.CREATED` в Kafka.
7. Возвращает клиенту ответ с `orderId`, суммой и статусом.

#### 2.1.2. Оплата заказа

1. Клиент вызывает POST `/api/v1/orders/{orderId}/pay`.
2. order-service отправляет gRPC-запрос в payment-service с итоговой суммой.
3. payment-service возвращает `paymentId` и `redirectUrl` для оплаты.
4. order-service обновляет статус заказа на `PENDING_PAYMENT` и сохраняет `paymentId`.
5. Возвращает клиенту `redirectUrl` для перехода на страницу оплаты.

#### 2.1.3. Подтверждение оплаты (callback)

1. payment-service отправляет событие `PAYMENTS.COMPLETED` в Kafka.
2. order-service получает событие, находит заказ по `paymentId`.
3. Обновляет статус заказа на `PAID`.
4. Отправляет событие `ORDERS.PAID` в Kafka.
5. notification-service получает событие и отправляет email/push клиенту.
-->

#### 2.1.2. <!-- Название второго шага -->

<!-- ... -->

### 2.2. Альтернативные и ошибочные сценарии

<!-- Используйте цветовую маркировку для типов сценариев:
     <font color="red">**Alt**</font> — альтернативный поток
     <font color="blue">**Opt**</font> — опциональный элемент
     <font color="blue">**Loop**</font> — циклический элемент
-->

#### 2.2.1. <font color="red">**Alt**</font> <!-- Название ошибочного сценария -->

<!-- ПРИМЕР:
#### 2.2.1. <font color="red">**Alt**</font> **Ошибка оплаты**

1. payment-service отправляет событие `PAYMENTS.FAILED` в Kafka.
2. order-service получает событие и обновляет статус заказа на `PAYMENT_FAILED`.
3. Клиент может повторить оплату в течение 30 минут (параметр `shop.order.payment-retry-window-min`).
4. Если оплата не завершена за 30 минут — заказ автоматически отменяется (статус `CANCELLED`).

#### 2.2.2. <font color="red">**Alt**</font> **Товар закончился на складе**

1. При создании заказа order-service проверяет наличие товара (резервирование).
2. Если хотя бы одного товара нет в наличии — возвращается ошибка 409 CONFLICT с указанием, какие товары недоступны.
3. Заказ НЕ создаётся.

#### 2.2.3. <font color="blue">**Opt**</font> **Применение промокода**

1. Если в запросе на создание заказа передан `promoCode`, выполняется валидация промокода (см. раздел 2.3).
2. При невалидном промокоде возвращается ошибка 400 с описанием причины.
-->

### 2.3. Бизнес-правила и логика обработки

<!-- Явное описание решающей логики сервиса: условия маршрутизации, правила
     трансформации данных, ветвления обработки. Этот раздел отвечает на вопрос
     "КАК сервис принимает решения", а не просто "ЧТО он принимает/отдаёт".

     Формат: пронумерованные правила. Для условий — ЕСЛИ/ТОГДА/ИНАЧЕ.
     Если сервис не содержит сложной логики, указать: "Линейная обработка, см. раздел 2.1" -->

<!-- ПРИМЕР:
### Правила применения промокода

1. **ЕСЛИ** поле `promoCode` не передано или пустое,
   **ТОГДА** заказ оформляется по полным ценам, `discountAmount = 0`.

2. **ЕСЛИ** `promoCode` передан,
   **ТОГДА** проверить промокод в таблице `promo_codes`:
   - **ЕСЛИ** промокод не найден, **ТОГДА** вернуть ошибку 400 "Промокод не найден".
   - **ЕСЛИ** `valid_until < now()`, **ТОГДА** вернуть ошибку 400 "Срок действия промокода истёк".
   - **ЕСЛИ** `usage_count >= max_usages`, **ТОГДА** вернуть ошибку 400 "Промокод больше не действует".

3. **ЕСЛИ** промокод валиден и `discount_type = 'PERCENT'`,
   **ТОГДА** скидка = `totalAmount * discount_value / 100`.
   **ЕСЛИ** `discount_type = 'FIXED'`,
   **ТОГДА** скидка = `discount_value`.

4. **ЕСЛИ** скидка > суммы заказа,
   **ТОГДА** `discountAmount = totalAmount`, `finalAmount = 0`.

### Правила перехода статусов заказа

1. `CREATED` → `PENDING_PAYMENT` — после вызова `/pay`.
2. `PENDING_PAYMENT` → `PAID` — после получения события `PAYMENTS.COMPLETED`.
3. `PENDING_PAYMENT` → `PAYMENT_FAILED` — после получения события `PAYMENTS.FAILED`.
4. `PAYMENT_FAILED` → `PENDING_PAYMENT` — при повторной попытке оплаты (в пределах 30 мин).
5. `PAYMENT_FAILED` → `CANCELLED` — автоматически по cron, если прошло > 30 мин.
6. `PAID` → `SHIPPED` — при обновлении статуса из системы доставки.
7. `SHIPPED` → `DELIVERED` — при подтверждении доставки.
8. Любой другой переход статуса запрещён — вернуть 409 CONFLICT.

### Правила отправки уведомлений

1. **ЕСЛИ** заказ перешёл в статус `PAID`,
   **ТОГДА** отправить событие `ORDERS.PAID` в Kafka — notification-service отправит email "Заказ оплачен".
2. **ЕСЛИ** заказ перешёл в статус `SHIPPED`,
   **ТОГДА** отправить событие `ORDERS.SHIPPED` — notification-service отправит push "Заказ передан в доставку".
3. **ЕСЛИ** заказ перешёл в статус `CANCELLED`,
   **ТОГДА** отправить событие `ORDERS.CANCELLED` — notification-service отправит email "Заказ отменён".
-->

### 2.4. API

<!-- Описание внешних API сервиса: REST, gRPC, WebSocket (если применимо).
     Для каждого эндпоинта/метода: описание, request/response, коды ошибок.
     При наличии — ссылка на OpenAPI-спецификацию или .proto-файл. -->

#### <!-- Тип: METHOD /path или gRPC ServiceName.Method -->

<!-- ПРИМЕР (REST):
#### POST /api/v1/orders — Создание заказа

**Request:**
```json
{
  "items": [
    {"productId": "prod-001", "quantity": 2, "price": 500.00},
    {"productId": "prod-002", "quantity": 1, "price": 500.00}
  ],
  "deliveryAddress": "Москва, ул. Ленина, д. 1, кв. 10",
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
  "title": "Некорректный запрос",
  "errorCode": "INVALID_INPUT",
  "status": 400
}
```

#### GET /api/v1/orders/{orderId} — Получение заказа по ID

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
  "title": "Заказ не найден",
  "errorCode": "ORDER_NOT_FOUND",
  "status": 404
}
```
-->

<!-- ПРИМЕР (gRPC):
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

Полный proto-файл: [proto/payment_service.proto](proto/payment_service.proto)
-->

### 2.5. Интеграции

<!-- Описание интеграций с внешними системами: Kafka, gRPC, SSE, HTTP-сервисы,
     базы данных, cron/batch-задачи. Для каждой интеграции: протокол, адрес/топик,
     формат данных, таймаут, retry-политика, поведение при недоступности. -->

<!-- ПРИМЕР (Kafka):

#### Kafka: ORDERS.CREATED (исходящий)
- **Направление**: producer
- **Формат**: JSON (схема OrderCreatedEvent)
- **Группа**: —
- **Описание**: событие о создании нового заказа, потребляется notification-service

#### Kafka: PAYMENTS.COMPLETED (входящий)
- **Направление**: consumer
- **Формат**: JSON (схема PaymentCompletedEvent)
- **Группа**: `order-service`
- **Concurrency**: 3 потока (параметр `spring.kafka.listener.concurrency`)
- **Описание**: callback от payment-service об успешной оплате
-->

<!-- ПРИМЕР (gRPC):
#### gRPC: payment-service
- **Метод**: `CreatePayment`
- **Таймаут**: 10000 мс
- **Retry**: 2 попытки, backoff 1s/3s
- **При недоступности**: вернуть клиенту 503 "Сервис оплаты временно недоступен"
-->

<!-- ПРИМЕР (SSE):
#### SSE: GET /api/v1/orders/{orderId}/status-stream — Стриминг статуса заказа
- **Направление**: server → client (event stream)
- **Формат**: `text/event-stream`
- **Событие**: `event: ORDER_STATUS_CHANGED`, `data: {"status": "SHIPPED"}`
- **Таймаут**: 300000 мс (5 минут)
- **Reconnect**: клиент переподключается с `Last-Event-ID`
-->

<!-- ПРИМЕР (БД):
#### PostgreSQL: shopdb (база данных)
- **Направление**: read/write
- **Порт**: 5432
- **Таблицы**: orders, order_items, promo_codes
- **Connection pool**: HikariCP, max 20 соединений
- **Таймаут запроса**: 5000 мс
- **Retry**: встроенный HikariCP retry при потере соединения
-->

<!-- ПРИМЕР (Cron/Batch):
#### Cron: Cancel Expired Orders — Отмена неоплаченных заказов
- **Расписание**: `0 */5 * * * *` (каждые 5 минут)
- **Описание**: поиск заказов в статусе PAYMENT_FAILED старше 30 минут и перевод в CANCELLED
- **Max retry**: 3 попытки на batch
- **Backoff**: 5000 мс между попытками
-->

### 2.6. Модели данных

<!-- Описание основных структур данных любого типа:
     JSON/API-схемы, таблицы БД (DDL/CQL), Avro-схемы, Protobuf-определения.
     Можно использовать: inline-определения, таблицы полей, ссылки на внешние файлы. -->

<!-- ПРИМЕР (JSON-схема):
#### CreateOrderRequest

```json
{
  "items": [
    {
      "productId": "string — UUID товара из каталога",
      "quantity": "integer — количество единиц (от 1 до 999)",
      "price": "decimal — цена за единицу в рублях"
    }
  ],
  "deliveryAddress": "string — адрес доставки (от 10 до 500 символов)",
  "promoCode": "string (optional) — промокод для скидки"
}
```
-->

<!-- ПРИМЕР (таблица полей):
#### OrderCreatedEvent (Kafka)

| Поле | Тип | Обязательность | Описание |
|------|-----|---------------|----------|
| orderId | string (UUID) | required | Идентификатор заказа |
| userId | string (UUID) | required | Идентификатор покупателя |
| items | array | required | Массив товаров в заказе |
| items[].productId | string (UUID) | required | ID товара |
| items[].quantity | integer | required | Количество |
| items[].price | decimal | required | Цена за единицу |
| originalAmount | decimal | required | Сумма заказа до скидки |
| promoCode | string | optional | Применённый промокод (null если не применялся) |
| discountAmount | decimal | required | Сумма скидки (0 если без промокода) |
| finalAmount | decimal | required | Итоговая сумма к оплате |
| createdAt | long (unix ms) | required | Время создания заказа |
-->

<!-- ПРИМЕР (Таблица БД — PostgreSQL DDL):
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

| Таблица / Колонка | Тип | PK / FK | Описание |
|-------------------|-----|---------|----------|
| orders.id | UUID | PK | Уникальный идентификатор заказа |
| orders.user_id | UUID | — | ID покупателя |
| orders.status | VARCHAR(20) | — | Статус: CREATED, PENDING_PAYMENT, PAID, SHIPPED, DELIVERED, CANCELLED |
| orders.original_amount | NUMERIC(12,2) | — | Сумма до скидки |
| orders.discount_amount | NUMERIC(12,2) | — | Сумма скидки |
| orders.final_amount | NUMERIC(12,2) | — | Итоговая сумма к оплате |
| orders.promo_code_id | BIGINT | FK → promo_codes | Ссылка на использованный промокод |
| orders.payment_id | VARCHAR(100) | — | ID платежа из payment-service |
| order_items.order_id | UUID | FK → orders | Ссылка на заказ |
| order_items.product_id | UUID | — | ID товара из каталога |
| order_items.quantity | INT | — | Количество единиц |
| order_items.price | NUMERIC(10,2) | — | Цена за единицу |
| order_items.subtotal | NUMERIC(12,2) | — | Стоимость позиции (price * quantity) |
-->

<!-- ПРИМЕР (Avro-схема):
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

Полная схема: [schemas/order-created-event.avsc](schemas/order-created-event.avsc)
-->

<!-- ПРИМЕР (Protobuf):
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

Полный proto-файл: [proto/payment_service.proto](proto/payment_service.proto)
-->

<!-- ПРИМЕР (ссылка на внешний файл):
Полная схема: [schemas/OrderCreatedEvent.json](schemas/OrderCreatedEvent.json)
-->

### 2.7. Требования к конфигурационному файлу

<!-- Таблица конфигурационных параметров -->

| Параметр | Обяз. | Тип | Значение по умолчанию | Описание |
|----------|-------|-----|----------------------|----------|
| <!-- имя параметра --> | <!-- да/нет --> | <!-- string/int/bool --> | <!-- значение --> | <!-- описание --> |

<!-- ПРИМЕР:
| Параметр | Обяз. | Тип | Значение по умолчанию | Описание |
|----------|-------|-----|----------------------|----------|
| spring.datasource.url | да | string | — | URL подключения к PostgreSQL |
| spring.datasource.hikari.maximum-pool-size | нет | int | 20 | Максимум соединений в пуле |
| spring.kafka.bootstrap-servers | да | string | — | Адреса Kafka-брокеров |
| spring.kafka.listener.concurrency | нет | int | 3 | Количество consumer-потоков |
| shop.promo.enabled | нет | bool | false | Feature flag: включить систему промокодов |
| shop.promo.max-discount-percent | нет | int | 50 | Максимальный процент скидки |
| shop.order.payment-retry-window-min | нет | int | 30 | Время на повторную оплату (минут) |
| shop.order.cancel-expired-cron | нет | string | "0 */5 * * * *" | Расписание отмены неоплаченных заказов |
| payment-service.grpc.host | да | string | — | Хост gRPC payment-service |
| payment-service.grpc.port | нет | int | 9090 | Порт gRPC payment-service |
| payment-service.grpc.timeout-ms | нет | int | 10000 | Таймаут вызова payment-service (мс) |
-->

### 2.8. Логирование

<!-- Таблица событий логирования. Уровни согласно common-requirements.md раздел 1. -->

| Уровень | Событие | Формат сообщения |
|---------|---------|-----------------|
| <!-- ERROR/WARN/INFO/DEBUG --> | <!-- описание события --> | <!-- шаблон сообщения --> |

<!-- ПРИМЕР:
| Уровень | Событие | Формат сообщения |
|---------|---------|-----------------|
| ERROR | Ошибка при вызове payment-service | "Payment service error for orderId={}: {}" |
| ERROR | Ошибка при сохранении заказа в БД | "Failed to save order: {}" |
| ERROR | Ошибка при отправке события в Kafka | "Failed to send event to topic={}: {}" |
| WARN | Попытка оплаты заказа в неверном статусе | "Invalid status transition for orderId={}: {} -> PENDING_PAYMENT" |
| WARN | Попытка использовать просроченный промокод | "Expired promo code: code={}, validUntil={}" |
| INFO | Заказ успешно создан | "Order created: orderId={}, userId={}, finalAmount={}" |
| INFO | Статус заказа изменён | "Order status changed: orderId={}, {} -> {}" |
| INFO | Промокод успешно применён | "Promo applied: orderId={}, code={}, discount={}" |
| DEBUG | Начало валидации промокода | "Validating promo code={} for orderId={}" |
| DEBUG | Отправка gRPC-запроса в payment-service | "Calling payment-service: orderId={}, amount={}" |
-->

### 2.9. Валидация входящих значений

<!-- Правила валидации входных данных -->

| Поле (JSON path) | Тип валидации | Правило | Сообщение об ошибке | Код ошибки |
|------------------|--------------|---------|--------------------|-----------|
| <!-- $.field --> | <!-- format/range/regex/enum/length --> | <!-- правило --> | <!-- текст --> | <!-- код --> |

<!-- ПРИМЕР:
| Поле (JSON path) | Тип валидации | Правило | Сообщение об ошибке | Код ошибки |
|------------------|--------------|---------|--------------------|-----------|
| $.items | length | Минимум 1 элемент, максимум 100 | "Заказ должен содержать от 1 до 100 товаров" | 1003 |
| $.items[*].productId | format | UUID v4 | "productId должен быть в формате UUID v4" | 1003 |
| $.items[*].quantity | range | От 1 до 999 | "Количество товара должно быть от 1 до 999" | 1003 |
| $.deliveryAddress | length | От 10 до 500 символов | "Адрес доставки должен содержать от 10 до 500 символов" | 1003 |
| $.promoCode | regex | `^[A-Z0-9_-]+$` | "Промокод может содержать только заглавные буквы, цифры, дефис и подчёркивание" | 1003 |
-->

### 2.10. Обработка ошибок

<!-- Коды ошибок привязаны к API-эндпоинтам. Для каждого эндпоинта — таблица ошибок. -->

| HTTP-код | Код ошибки | errorCode | Ситуация | Действие клиента |
|----------|-----------|-----------|----------|-----------------|
| <!-- 4xx/5xx --> | <!-- XXXX --> | <!-- строковый код --> | <!-- когда возникает --> | <!-- что делать --> |

<!-- ПРИМЕР:

**POST /api/v1/orders:**

| HTTP-код | Код ошибки | errorCode | Ситуация | Действие клиента |
|----------|-----------|-----------|----------|-----------------|
| 400 | 1003 | INVALID_INPUT | Невалидный формат запроса | Исправить тело запроса |
| 400 | 1003 | INVALID_PROMO_CODE | Промокод не найден | Проверить правильность кода |
| 400 | 1003 | PROMO_CODE_EXPIRED | Промокод просрочен | Использовать другой промокод |
| 404 | 1004 | PRODUCT_NOT_FOUND | Товар не найден в каталоге | Проверить productId |
| 500 | 1001 | INTERNAL_ERROR | Ошибка сервиса | Повторить через несколько секунд |

**GET /api/v1/orders/{orderId}:**

| HTTP-код | Код ошибки | errorCode | Ситуация | Действие клиента |
|----------|-----------|-----------|----------|-----------------|
| 404 | 1004 | ORDER_NOT_FOUND | Заказ не найден | Проверить orderId |

Формат ответа (RFC 7807):
```json
{
  "type": "https://api.shop.com/problems/bad-request",
  "title": "Промокод не найден",
  "detail": "Промокод WINTER2025 не существует",
  "errorCode": "INVALID_PROMO_CODE",
  "status": 400,
  "instance": "/api/v1/orders"
}
```
-->

### 2.11. Хедеры и метаданные

<!-- HTTP-хедеры, Kafka-хедеры, gRPC metadata, используемые сервисом -->

| Транспорт | Имя | Направление | Обязательность | Формат | Описание |
|-----------|-----|-------------|---------------|--------|----------|
| <!-- HTTP/Kafka/gRPC --> | <!-- имя --> | <!-- request/response --> | <!-- required/optional --> | <!-- формат --> | <!-- зачем --> |

<!-- ПРИМЕР:
| Транспорт | Имя | Направление | Обязательность | Формат | Описание |
|-----------|-----|-------------|---------------|--------|----------|
| HTTP | X-Request-ID | request | required | UUID | Идентификатор запроса для трейсинга |
| HTTP | X-User-ID | request | required | UUID | Идентификатор пользователя из JWT |
| HTTP | X-Promo-Applied | response | optional | boolean | Признак применения промокода |
| Kafka | ORDER_SOURCE | producer header | optional | string | Канал оформления: WEB / MOBILE / API |
| gRPC | x-order-id | request metadata | required | string | Идентификатор заказа для payment-service |
-->

---

## 3. Рекомендации к критериям приёмки

<!-- Верифицируемые условия, по которым определяется корректность реализации.
     Формат: КОГДА/ТОГДА. Каждый критерий должен быть проверяемым.
     Покрывать: основной сценарий, граничные случаи, ошибочные сценарии.
     Если сервис новый — описать критерии для всех ключевых сценариев из 2.1-2.2. -->

| # | КОГДА | ТОГДА |
|---|-------|-------|
| <!-- номер --> | <!-- условие / действие --> | <!-- ожидаемый результат --> |

<!-- ПРИМЕР:
| # | КОГДА | ТОГДА |
|---|-------|-------|
| 1 | Клиент отправляет POST /api/v1/orders с корректным списком товаров и адресом | Заказ создан в БД со статусом CREATED, ответ 201 с orderId и finalAmount, событие ORDERS.CREATED отправлено в Kafka |
| 2 | В запросе на создание заказа передан валидный промокод "SUMMER2026" (скидка 10%) | Скидка рассчитана, discountAmount > 0, finalAmount = originalAmount - discountAmount, usage_count промокода увеличен на 1 |
| 3 | Передан несуществующий промокод | Ответ 400, errorCode = INVALID_PROMO_CODE, заказ НЕ создан |
| 4 | Клиент вызывает POST /api/v1/orders/{orderId}/pay для заказа в статусе CREATED | Статус изменён на PENDING_PAYMENT, payment-service вызван по gRPC, клиенту возвращён redirectUrl |
| 5 | payment-service отправляет событие PAYMENTS.COMPLETED | Статус заказа изменён на PAID, событие ORDERS.PAID отправлено в Kafka |
| 6 | payment-service отправляет событие PAYMENTS.FAILED | Статус заказа изменён на PAYMENT_FAILED |
| 7 | Заказ в статусе PAYMENT_FAILED более 30 минут | Cron-задача переводит заказ в статус CANCELLED, событие ORDERS.CANCELLED отправлено в Kafka |
| 8 | Клиент вызывает /pay для заказа в статусе PAID | Ответ 409 CONFLICT, статус НЕ изменён |
| 9 | PostgreSQL недоступна при создании заказа | Ответ 503, в логе ERROR "Failed to save order" |
| 10 | payment-service недоступен (таймаут gRPC) | Ответ 503, в логе ERROR "Payment service error", статус заказа НЕ изменён |
-->

---

## 4. Нефункциональные требования

### 4.1. Безопасность

<!-- Требования к безопасности. Стандартный набор для всех сервисов платформы: -->

1. Взаимодействие между сервисами ДОЛЖНО осуществляться по протоколу TLS версии 1.2 и выше.
2. Для межсервисного взаимодействия ДОЛЖЕН использоваться mTLS (mutual TLS).
3. Управление доступом ДОЛЖНО осуществляться на основе ACL по DN (Distinguished Name) из сертификатов.
4. Секреты (пароли, ключи, токены) НЕ ДОЛЖНЫ храниться в коде, конфигурационных файлах или переменных окружения.
5. Конфиденциальная информация K1/K2 ДОЛЖНА маскироваться в логах на 100%.

<!-- Дополнительные требования, специфичные для сервиса: -->

<!-- ПРИМЕР:
6. Авторизация покупателей ДОЛЖНА выполняться через auth-service по JWT-токену.
7. Клиент может просматривать и оплачивать только свои заказы (проверка userId из JWT).
8. Адрес доставки ДОЛЖЕН маскироваться в логах (первые 10 символов + `...`).
-->

### 4.2. Производительность

<!-- Требования к производительности, таймауты, лимиты -->

| Параметр | Значение | Описание |
|----------|----------|----------|
| <!-- параметр --> | <!-- значение --> | <!-- описание --> |

<!-- ПРИМЕР:
| Параметр | Значение | Описание |
|----------|----------|----------|
| max.request.size | 1 MB | Максимальный размер входящего запроса |
| order.create.timeout | 5000 мс | Таймаут создания заказа (включая валидацию промокода) |
| payment.grpc.timeout | 10000 мс | Таймаут вызова payment-service |
| kafka.delivery.timeout.ms | 5000 | Таймаут доставки сообщения в Kafka |
| db.query.timeout | 5000 мс | Таймаут запроса к PostgreSQL |
| max.items.per.order | 100 | Максимальное количество товаров в одном заказе |
-->

### 4.3. Надежность

<!-- Требования к отказоустойчивости и надёжности -->

<!-- ПРИМЕР:
1. Сервис ДОЛЖЕН поддерживать graceful shutdown.
2. Минимальное количество pod — 3 (multi-pod deployment).
3. Pod Disruption Budget: минимум 2 доступных пода.
4. Readiness Probe ДОЛЖНА проверять доступность PostgreSQL и Kafka.
5. При недоступности payment-service — вернуть 503, заказ остаётся в статусе CREATED.
6. При ошибке отправки события в Kafka — retry 3 раза с backoff, затем логирование ERROR.
7. Дублирование Kafka-событий обрабатывается идемпотентно (по orderId).
-->

### 4.4. Мониторинг

<!-- Описание метрик сервиса -->

#### 4.4.1. Стандартные метрики

<!-- Если сервис использует Spring Boot Actuator или аналогичный фреймворк -->

<!-- ПРИМЕР:
Сервис предоставляет стандартные метрики Spring Boot Actuator:
- `http_server_requests_seconds` — длительность HTTP-запросов
- `jvm_memory_used_bytes` — использование памяти JVM
- `hikaricp_connections_active` — активные соединения с БД

Health-check endpoints:
- `/actuator/health` — общее состояние
- `/actuator/health/readiness` — готовность к приёму трафика (PostgreSQL + Kafka)
- `/actuator/health/liveness` — живучесть процесса
-->

#### 4.4.2. Кастомные бизнес-метрики

<!-- Для каждой метрики: имя, тип, описание, теги -->

##### <!-- Имя метрики -->

- **Тип**: <!-- Counter / Gauge / Timer (Histogram) -->
- **Описание**: <!-- что измеряет -->

| Тег | Обязательность | Описание | Пример значения |
|-----|---------------|----------|----------------|
| <!-- имя тега --> | <!-- Обязательно/Условно --> | <!-- описание --> | <!-- пример --> |

**Пример значения:**
```
metric_name{tag1="value1", tag2="value2"} 1
```

<!-- ПРИМЕР:
##### shop.orders.created

- **Тип**: Counter
- **Описание**: Счётчик созданных заказов

| Тег | Обязательность | Описание | Пример значения |
|-----|---------------|----------|----------------|
| promoApplied | Обязательно | Был ли применён промокод | "true" / "false" |
| channel | Условно | Канал оформления | "WEB" / "MOBILE" |

**Пример значения:**
```
shop_orders_created{promoApplied="true", channel="WEB"} 42
```

##### shop.orders.payment_duration

- **Тип**: Timer (Histogram)
- **Описание**: Время вызова payment-service по gRPC

| Тег | Обязательность | Описание | Пример значения |
|-----|---------------|----------|----------------|
| status | Обязательно | Результат вызова | "SUCCESS" / "TIMEOUT" / "ERROR" |

**Пример значения:**
```
shop_orders_payment_duration_seconds_bucket{status="SUCCESS", le="1.0"} 150
```
-->

#### 4.4.3. Вычисляемые метрики (Calculated Metrics)

<!-- Метрики, вычисляемые на стороне системы мониторинга (Prometheus/Grafana) -->

| Категория | Метрика | Описание | Формула |
|-----------|---------|----------|---------|
| <!-- категория --> | <!-- имя --> | <!-- описание --> | <!-- PromQL --> |

<!-- ПРИМЕР:
| Категория | Метрика | Описание | Формула |
|-----------|---------|----------|---------|
| Конверсия | shop_order_payment_success_rate | Процент успешно оплаченных заказов | `(sum(rate(shop_orders_created{status="PAID"}[5m])) / sum(rate(shop_orders_created[5m]))) * 100` |
| Промокоды | shop_promo_usage_rate | Процент заказов с промокодом | `(sum(rate(shop_orders_created{promoApplied="true"}[5m])) / sum(rate(shop_orders_created[5m]))) * 100` |
| Ошибки | shop_5xx_error_rate | Процент серверных ошибок | `(sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))) * 100` |
-->

### 4.5. Аудит

<!-- Требования к аудиту действий (если применимо для сервиса) -->

<!-- ПРИМЕР:
1. Все действия покупателей (создание заказа, оплата, отмена) ДОЛЖНЫ записываться в журнал аудита.
2. Запись аудита ДОЛЖНА содержать: userId (из JWT), действие, orderId, timestamp, результат (успех/ошибка).
3. Журнал аудита НЕ ДОЛЖЕН содержать адрес доставки и платёжные данные.
4. Все действия администратора (создание/деактивация промокодов) ДОЛЖНЫ записываться отдельно.
-->
