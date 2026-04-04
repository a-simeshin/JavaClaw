# Change: Пакетная отправка webhook'ов для уведомлений

> **Статус**: Черновик
>
> **Дата создания**: 2026-04-02
>
> **Автор**: AI Hub Analytics
>
> **Версия**: 1.0
>
> **Целевая спецификация**: openspec/specs/notification-service/notification-service.md

---

## 1. Предложение

### Цель изменения

Добавить в notification-service функциональность пакетной отправки статусов уведомлений во внешние системы через webhook'и. 

**Проблемы, которые решаем:**
- Внешние системы-подписчики должны опрашивать notification-service через polling API для получения статусов уведомлений
- Высокая нагрузка на notification-service при частом polling от множества клиентов
- Задержки в получении статусов внешними системами (до интервала polling)
- Отсутствие push-механизма для информирования внешних систем о событиях

**Решение:**
- notification-service самостоятельно отправляет webhook'и во внешние системы при наступлении событий
- Пакетирование событий для снижения количества HTTP-вызовов
- Гарантированная доставка с retry-механизмом
- Гибкая настройка подписок на события

### Инициатор

Команда интеграции AI Risk Hub. Необходимость снижения нагрузки на notification-service и уменьшения задержек доставки статусов уведомлений во внешние системы.

### Затронутые компоненты

- [x] notification-service — основная функциональность webhook batch отправки
- [x] openspec/specs/notification-service/notification-service.md — дополнение спецификации
- [ ] external-consumer-services — адаптация внешних систем для приёма webhook'ов (отдельный CR)

### Приоритет

**Высокий** — критично для снижения нагрузки на систему и улучшения времени доставки событий.

### Обратная совместимость

**Да** — функциональность webhook'ов является дополнительной к существующим механизмам (SSE, Kafka, polling). 

Конфигурация по умолчанию отключена (`webhook.batch.enabled=false`), что сохраняет текущее поведение системы.

---

## 2. Бизнес-логика

### Правила отправки webhook'ов

1. **ЕСЛИ** `webhook.batch.enabled = true`, **ТОГДА**:
   - Сервис активирует механизм пакетной отправки webhook'ов
   - События накапливаются в батч до достижения `batch.size` или `batch.interval`
   - **ИНАЧЕ** webhook'и не отправляются (существующие механизмы работают штатно)

2. **ЕСЛИ** размер батча достигает `batch.size` (параметр `webhook.batch.size`), **ТОГДА**:
   - Батч немедленно отправляется всем настроенным подписчикам
   - Создаётся новый пустой батч

3. **ЕСЛИ** интервал времени достигает `batch.interval` (параметр `webhook.batch.interval-ms`), **ТОГДА**:
   - Накопленные события отправляются подписчикам (даже если батч не заполнен)
   - Создаётся новый пустой батч

4. **ЕСЛИ** событие имеет `priority = high`, **ТОГДА**:
   - Событие отправляется немедленно, без пакетирования
   - Пропускает очередь батча

### Правила маршрутизации webhook'ов

1. **ЕСЛИ** событие относится к каналу `channel`, **ТОГДА**:
   - Отправляется только подписчикам, указавшим этот канал в `subscribedChannels`
   - **ИНАЧЕ** событие игнорируется для данного подписчика

2. **ЕСЛИ** статус уведомления совпадает с `eventTypes` подписчика, **ТОГДА**:
   - Событие включается в батч для этого подписчика
   - **ИНАЧЕ** событие не отправляется этому подписчику

### Retry-механизм для webhook'ов

1. **ЕСЛИ** отправка webhook'а завершилась ошибкой (таймаут, HTTP 5xx, ошибка соединения), **ТОГДА**:
   - Увеличивается `attemptCount` для этого webhook'а
   - **ЕСЛИ** `attemptCount < webhook.retry.max-attempts`, **ТОГДА**:
     - Запланировать повторную попытку через `webhook.retry.backoff-ms * 2^attemptCount`
     - Использовать экспоненциальную задержку
   - **ИНАЧЕ**:
     - Статус webhook'а: FAILED
     - Событие перемещается в DLT-очередь `WEBHOOK.DLT`
     - Генерируется алерт в систему мониторинга

2. **ЕСЛИ** получен HTTP-статус 4xx (кроме 429), **ТОГДА**:
   - Повторные попытки НЕ выполняются
   - Статус webhook'а: FAILED (client error)
   - Запись в лог WARN

3. **ЕСЛИ** получен HTTP-статус 429 (Too Many Requests), **ТОГДА**:
   - Использовать значение хедера `Retry-After` для задержки
   - **ЕСЛИ** хедер отсутствует, **ТОГДА** использовать стандартный backoff

### Идемпотентность webhook'ов

1. Каждый webhook содержит уникальный `webhookId` (UUID)
2. **ЕСЛИ** подписчик получил дублирующийся `webhookId`, **ТОГДА**:
   - Должен вернуть HTTP 200/201 без повторной обработки
   - Должен использовать `webhookId` для дедупликации

---

## 3. Изменения моделей данных

### ADDED

#### WebhookSubscription — конфигурация подписки

| Поле | Тип данных | Обязательность | По умолчанию | Пример | Обоснование |
|------|-----------|---------------|--------------|--------|-------------|
| subscriptionId | string (UUID) | required | — | "sub-uuid-12345" | Уникальный идентификатор подписки |
| name | string | required | — | "external-analytics" | Человекочитаемое имя подписки |
| url | string (URL) | required | — | "https://analytics.example.com/webhooks/notifications" | Endpoint для приёма webhook'ов |
| secret | string | required | — | "whsec_..." | Секрет для подписи HMAC-SHA256 |
| subscribedChannels | array\<enum\> | required | — | ["email", "push", "sms"] | Каналы, на которые подписан |
| eventTypes | array\<enum\> | required | — | ["sent", "failed", "delivered"] | Типы событий для отправки |
| active | boolean | required | true | true | Флаг активности подписки |
| headers | map\<string, string\> | optional | {} | {"X-Custom-Header": "value"} | Кастомные HTTP-хедеры |
| createdAt | timestamp (ms) | required | — | 1711929600000 | Время создания подписки |
| updatedAt | timestamp (ms) | optional | — | 1711929700000 | Время последнего обновления |

#### WebhookBatch — пакет событий

| Поле | Тип данных | Обязательность | По умолчанию | Пример | Обоснование |
|------|-----------|---------------|--------------|--------|-------------|
| batchId | string (UUID) | required | — | "batch-uuid-67890" | Уникальный идентификатор батча |
| subscriptionId | string (UUID) | required | — | "sub-uuid-12345" | Ссылка на подписку |
| events | array\<WebhookEvent\> | required | — | [...] | Массив событий в батче |
| createdAt | timestamp (ms) | required | — | 1711929600000 | Время создания батча |
| sentAt | timestamp (ms) | optional | — | 1711929605000 | Время отправки |
| status | enum | required | PENDING | "SENT" | Статус батча |
| attemptCount | integer | required | 0 | 2 | Количество попыток отправки |
| lastError | string | optional | — | "Connection timeout" | Текст последней ошибки |

#### WebhookEvent — событие в батче

| Поле | Тип данных | Обязательность | По умолчанию | Пример | Обоснование |
|------|-----------|---------------|--------------|--------|-------------|
| eventId | string (UUID) | required | — | "event-uuid-11111" | Уникальный идентификатор события |
| notificationId | string (UUID) | required | — | "notif-uuid-12345" | ID уведомления |
| eventType | enum | required | — | "sent" | Тип события: sent/failed/delivered/retrying |
| channel | enum | required | — | "email" | Канал отправки |
| status | enum | required | — | "sent" | Финальный статус |
| recipient | string | required | — | "u***@example.com" | Получатель (маскированный) |
| timestamp | timestamp (ms) | required | — | 1711929600000 | Время события |
| attemptCount | integer | optional | 1 | 1 | Количество попыток отправки уведомления |
| errorCode | string | optional | — | "PROVIDER_ERROR" | Код ошибки (если есть) |
| errorMessage | string | optional | — | "SMTP timeout" | Текст ошибки (если есть) |
| metadata | map\<string, string\> | optional | {} | {"userId": "user-123"} | Дополнительные метаданные |

#### Cassandra: webhook_subscriptions

```cql
CREATE TABLE IF NOT EXISTS notification_keyspace.webhook_subscriptions (
    subscription_id uuid,
    name text,
    url text,
    secret text,
    subscribed_channels frozen<list<text>>,
    event_types frozen<list<text>>,
    active boolean,
    headers map<text, text>,
    created_at timestamp,
    updated_at timestamp,
    PRIMARY KEY (subscription_id)
) WITH default_time_to_live = 0
  AND compaction = {'class': 'SizeTieredCompactionStrategy'}
  AND compression = {'sstable_compression': 'LZ4Compressor'}
  AND gc_grace_seconds = 86400;

CREATE INDEX IF NOT EXISTS idx_active ON notification_keyspace.webhook_subscriptions (active);
```

| Таблица / Колонка | Тип | PK | Описание |
|-------------------|-----|-----|----------|
| webhook_subscriptions.subscription_id | uuid | PK | Уникальный идентификатор подписки |
| webhook_subscriptions.name | text | — | Имя подписки |
| webhook_subscriptions.url | text | — | URL endpoint |
| webhook_subscriptions.secret | text | — | Секрет для HMAC-подписи |
| webhook_subscriptions.subscribed_channels | list\<text\> | — | Список каналов |
| webhook_subscriptions.event_types | list\<text\> | — | Типы событий |
| webhook_subscriptions.active | boolean | — | Флаг активности |
| webhook_subscriptions.headers | map\<text,text\> | — | Кастомные хедеры |
| webhook_subscriptions.created_at | timestamp | — | Время создания |
| webhook_subscriptions.updated_at | timestamp | — | Время обновления |

#### Cassandra: webhook_batches

```cql
CREATE TABLE IF NOT EXISTS notification_keyspace.webhook_batches (
    batch_id uuid,
    subscription_id uuid,
    events frozen<list<text>>,  -- JSON-сериализованные WebhookEvent
    created_at timestamp,
    sent_at timestamp,
    status text,
    attempt_count int,
    last_error text,
    PRIMARY KEY (batch_id)
) WITH default_time_to_live = 259200
  AND compaction = {'class': 'TimeWindowCompactionStrategy', 'compaction_window_size': '1', 'compaction_window_unit': 'DAYS'}
  AND compression = {'sstable_compression': 'LZ4Compressor'}
  AND gc_grace_seconds = 86400;

CREATE INDEX IF NOT EXISTS idx_subscription_id ON notification_keyspace.webhook_batches (subscription_id);
CREATE INDEX IF NOT EXISTS idx_status ON notification_keyspace.webhook_batches (status);
CREATE INDEX IF NOT EXISTS idx_created_at ON notification_keyspace.webhook_batches (created_at);
```

| Таблица / Колонка | Тип | PK | Описание |
|-------------------|-----|-----|----------|
| webhook_batches.batch_id | uuid | PK | Уникальный идентификатор батча |
| webhook_batches.subscription_id | uuid | — | Ссылка на подписку |
| webhook_batches.events | list\<text\> | — | JSON-сериализованные события |
| webhook_batches.created_at | timestamp | — | Время создания |
| webhook_batches.sent_at | timestamp | — | Время отправки |
| webhook_batches.status | text | — | Статус: PENDING/SENT/FAILED/RETRYING |
| webhook_batches.attempt_count | int | — | Количество попыток |
| webhook_batches.last_error | text | — | Текст ошибки |

---

## 4. Изменения интеграций

### ADDED

#### REST API: Управление подписками

#### POST /api/v1/webhook/subscriptions — Создание подписки

- **Описание**: Регистрация новой webhook-подписки
- **Request**:
```json
{
  "name": "external-analytics",
  "url": "https://analytics.example.com/webhooks/notifications",
  "secret": "whsec_abc123...",
  "subscribedChannels": ["email", "push"],
  "eventTypes": ["sent", "failed"],
  "active": true,
  "headers": {
    "X-Custom-Header": "value"
  }
}
```
- **Response (201 Created)**:
```json
{
  "subscriptionId": "sub-uuid-12345",
  "name": "external-analytics",
  "url": "https://analytics.example.com/webhooks/notifications",
  "subscribedChannels": ["email", "push"],
  "eventTypes": ["sent", "failed"],
  "active": true,
  "createdAt": 1711929600000
}
```
- **Таймаут**: 5000 мс
- **Retry**: нет (синхронный запрос)

#### GET /api/v1/webhook/subscriptions/{subscriptionId} — Получение подписки

- **Описание**: Получение конфигурации подписки по ID
- **Response (200 OK)**:
```json
{
  "subscriptionId": "sub-uuid-12345",
  "name": "external-analytics",
  "url": "https://analytics.example.com/webhooks/notifications",
  "subscribedChannels": ["email", "push"],
  "eventTypes": ["sent", "failed"],
  "active": true,
  "headers": {
    "X-Custom-Header": "value"
  },
  "createdAt": 1711929600000,
  "updatedAt": 1711929700000
}
```

#### PUT /api/v1/webhook/subscriptions/{subscriptionId} — Обновление подписки

- **Описание**: Обновление конфигурации подписки
- **Request**: (аналогично POST, все поля кроме subscriptionId)
- **Response (200 OK)**: (аналогично GET)

#### DELETE /api/v1/webhook/subscriptions/{subscriptionId} — Удаление подписки

- **Описание**: Деактивация и удаление подписки
- **Response (204 No Content)**

#### GET /api/v1/webhook/subscriptions — Список подписок

- **Описание**: Получение списка всех активных подписок
- **Query параметры**: `active` (boolean, optional), `channel` (string, optional)
- **Response (200 OK)**:
```json
{
  "subscriptions": [
    {
      "subscriptionId": "sub-uuid-12345",
      "name": "external-analytics",
      "url": "https://analytics.example.com/webhooks/notifications",
      "subscribedChannels": ["email", "push"],
      "eventTypes": ["sent", "failed"],
      "active": true,
      "createdAt": 1711929600000
    }
  ],
  "total": 1
}
```

#### Webhook Batch: Отправка событий во внешние системы

- **Описание**: Пакетная отправка событий уведомления подписчикам
- **Направление**: producer (HTTP POST во внешние системы)
- **Request Format** (POST во внешний URL из подписки):
```json
{
  "webhookId": "webhook-uuid-99999",
  "batchId": "batch-uuid-67890",
  "subscriptionId": "sub-uuid-12345",
  "events": [
    {
      "eventId": "event-uuid-11111",
      "notificationId": "notif-uuid-12345",
      "eventType": "sent",
      "channel": "email",
      "status": "sent",
      "recipient": "u***@example.com",
      "timestamp": 1711929600000,
      "attemptCount": 1,
      "metadata": {
        "userId": "user-123"
      }
    }
  ],
  "sentAt": 1711929605000
}
```
- **Response Format** (ожидаемый от внешней системы):
```json
{
  "received": true,
  "processedCount": 1
}
```
- **Таймаут**: 10000 мс (параметр `webhook.http.timeout-ms`)
- **Retry**: 5 попыток, exponential backoff 2000 мс (параметры `webhook.retry.max-attempts`, `webhook.retry.backoff-ms`)

#### Cron: Webhook Batch Sender — Периодическая отправка батчей

- **Описание**: Периодический запуск отправки накопленных батчей
- **Расписание**: `*/5 * * * * *` (каждые 5 секунд, параметр `webhook.batch.scheduler.cron`)
- **Длительность окна**: 300 сек (максимальное время выполнения)
- **Batch size**: до 100 событий в одном батче (параметр `webhook.batch.size`)
- **Batch interval**: 5000 мс (параметр `webhook.batch.interval-ms`)

---

## 5. Обработка ошибок

| Код ошибки | HTTP-статус | errorCode | Ситуация | Действие клиента |
|-----------|------------|-----------|----------|------------------|
| 1003 | 400 | INVALID_INPUT | Некорректный формат URL подписки | Исправить URL на валидный формат |
| 1003 | 400 | INVALID_WEBHOOK_SECRET | Некорректный формат секрета (минимум 32 символа) | Указать секрет длиной от 32 символов |
| 1004 | 400 | MISSING_REQUIRED_BODY_PARAMS | Отсутствуют обязательные поля (url, secret, subscribedChannels) | Добавить недостающие поля |
| 1201 | 403 | FORBIDDEN | Недостаточно прав для создания подписки | Запросить роль webhook-manager |
| 1400 | 500 | CONNECTION_TIMEOUT | Таймаут соединения при отправке webhook'а | Не требуется — сработает retry |
| 1402 | 500 | RETRIES_EXHAUSTED | Все retry-попытки отправки webhook'а исчерпаны | Проверить доступность endpoint |
| 1403 | 500 | WEBHOOK_DELIVERY_FAILED | Ошибка от внешнего сервиса при доставке webhook'а | Проверить логи внешней системы |
| 1001 | 500 | TIMEOUT | Таймаут вызова внешней системы | Не требуется — сработает retry |

**Формат ответа об ошибке (RFC 7807):**
```json
{
  "type": "https://api.example.com/problems/bad-request",
  "title": "Некорректный формат секретного ключа",
  "detail": "Секретный ключ должен содержать минимум 32 символа",
  "errorCode": "INVALID_WEBHOOK_SECRET",
  "status": 400,
  "instance": "/api/v1/webhook/subscriptions"
}
```

---

## 6. Хедеры и метаданные

### HTTP-хедеры для webhook'ов (исходящие)

| Операция | Транспорт | Имя | Направление | Обязательность | Формат | Пример | Назначение |
|----------|-----------|-----|-------------|---------------|--------|--------|-----------|
| ADDED | HTTP | Content-Type | request | required | string | application/json | Тип содержимого |
| ADDED | HTTP | X-Webhook-Signature | request | required | string | sha256=abc123... | HMAC-SHA256 подпись тела |
| ADDED | HTTP | X-Webhook-ID | request | required | UUID | webhook-uuid-99999 | Уникальный ID webhook'а |
| ADDED | HTTP | X-Webhook-Timestamp | request | required | timestamp (ms) | 1711929605000 | Время отправки |
| ADDED | HTTP | X-Subscription-ID | request | required | UUID | sub-uuid-12345 | ID подписки |
| ADDED | HTTP | User-Agent | request | required | string | notification-service/1.0 | Идентификация отправителя |
| ADDED | HTTP | X-Custom-* | request | optional | string | из конфигурации | Кастомные хедеры из подписки |

### HMAC-подпись webhook'ов

**Алгоритм:**
1. Тело запроса (JSON) берётся в исходном виде (raw bytes)
2. Вычисляется HMAC-SHA256 с использованием `secret` из подписки
3. Результат кодируется в hex (lowercase)
4. Добавляется префикс `sha256=`

**Пример:**
```
X-Webhook-Signature: sha256=6d2c85f3b8e9a1c4d7e0f2a5b8c1d4e7f0a3b6c9d2e5f8a1b4c7d0e3f6a9b2c5
```

**Валидация на стороне получателя:**
1. Получить значение хедера `X-Webhook-Signature`, отбросить префикс `sha256=`
2. Вычислить HMAC-SHA256 от тела запроса (raw bytes) с использованием `secret` из подписки
3. Сравнить вычисленную подпись с полученной (constant-time comparison для защиты от timing-атак)
4. Если подписи совпадают — запрос валиден, иначе — отклонить с HTTP 403

---

## 7. Валидация входящих значений

| Поле (JSON path) | Тип валидации | Правило | Сообщение об ошибке | Код ошибки |
|------------------|--------------|---------|--------------------|-----------|
| $.name | length | 1-128 символов | "Имя подписки должно быть от 1 до 128 символов" | 1003 |
| $.url | format | Валидный HTTPS URL | "URL должен быть валидным HTTPS URL" | 1003 |
| $.url | scheme | Только https:// | "Разрешены только HTTPS URL" | 1003 |
| $.secret | length | Минимум 32 символа | "Секрет должен содержать минимум 32 символа" | 1003 |
| $.secret | format | Префикс "whsec_" рекомендуется | "Секрет должен начинаться с whsec_" | 1003 (WARN) |
| $.subscribedChannels | length | Минимум 1 элемент | "Должен быть указан хотя бы один канал" | 1003 |
| $.subscribedChannels[*] | enum | "email", "push", "sms" | "Недопустимый канал: {value}" | 1003 |
| $.eventTypes | length | Минимум 1 элемент | "Должен быть указан хотя бы один тип события" | 1003 |
| $.eventTypes[*] | enum | "sent", "failed", "delivered", "retrying" | "Недопустимый тип события: {value}" | 1003 |
| $.headers.* | length | Максимум 1024 символа на хедер | "Хедер слишком длинный" | 1003 |
| $.headers.* | format | Валидное имя HTTP-хедера | "Некорректное имя хедера" | 1003 |

---

## 8. Влияние на безопасность

- **Авторизация/аутентификация**: 
  - API управления подписками требует JWT-токен с ролью `webhook-manager`
  - Проверка прав доступа при создании/обновлении/удалении подписок
  
- **Новые роли/права**:
  - `webhook-manager` — право управления webhook-подписками (CRUD)
  - `webhook-viewer` — право только на чтение подписок (GET)

- **Маскирование данных**:
  - Поле `recipient` в событиях webhook ДОЛЖНО маскироваться:
    - Email: `u***@example.com` (первая буква + `***@domain`)
    - Phone: `+7***1234567` (первые 3 символа + `***` + последние 4)
  - Секрет `secret` НЕ ДОЛЖЕН записываться в логи (маскирование 100%)
  - Поля K1/K2 в `metadata` ДОЛЖНЫ маскироваться согласно common-requirements.md

- **HMAC-подпись**:
  - Все webhook'и ДОЛЖНЫ быть подписаны HMAC-SHA256
  - Секрет хранится в зашифрованном виде в Cassandra (AES-256)
  - Передача секрета только при создании подписки (не возвращается в API)

---

## 9. Миграция

**Не требуется** — функциональность является дополнительной и включается конфигурацией.

### План включения (опционально)

1. Развернуть новую версию notification-service с feature flag `webhook.batch.enabled=false`
2. Настроить подписки через API для тестовых сред
3. Протестировать доставку webhook'ов на staging
4. Включить `webhook.batch.enabled=true` на production
5. Мониторить метрики доставки и ошибок

### План отката

1. Установить `webhook.batch.enabled=false`
2. Перезапустить поды notification-service
3. Существующие подписки сохраняются в БД (неактивны)

---

## 10. Логирование (новые/изменённые события)

| Уровень | Код | Событие | Формат сообщения |
|---------|-----|---------|-----------------|
| ERROR | 1400 | Таймаут соединения при отправке webhook | "Webhook connection timeout: subscriptionId={}, url={}, timeout={}ms" |
| ERROR | 1402 | Все retry attempts failed для webhook | "All webhook retry attempts exhausted: batchId={}, subscriptionId={}, attempts={}, lastError={}" |
| ERROR | 1403 | Ошибка от внешнего сервиса | "Webhook delivery failed: subscriptionId={}, httpStatus={}, responseBody={}" |
| ERROR | 1003 | Ошибка валидации подписки | "Webhook subscription validation failed: subscriptionId={}, violations={}" |
| WARN | 2300 | Webhook endpoint вернул 4xx | "Webhook client error: subscriptionId={}, httpStatus={}, errorCode={}" |
| WARN | 2301 | Webhook endpoint вернул 429 | "Webhook rate limited: subscriptionId={}, retryAfter={}s" |
| INFO | 3400 | Создание webhook подписки | "Webhook subscription created: subscriptionId={}, name={}, url={}" |
| INFO | 3401 | Обновление webhook подписки | "Webhook subscription updated: subscriptionId={}, name={}" |
| INFO | 3402 | Удаление webhook подписки | "Webhook subscription deleted: subscriptionId={}, name={}" |
| INFO | 3403 | Отправка webhook батча | "Webhook batch sent: batchId={}, subscriptionId={}, eventCount={}, duration={}ms" |
| INFO | 3404 | Retry попытка отправки webhook | "Webhook retry attempt: batchId={}, attempt={}/{}, nextRetryIn={}ms" |
| DEBUG | — | Валидация HMAC подписи | "Validating webhook signature for subscriptionId={}" |
| DEBUG | — | Батч сформирован | "Webhook batch created: batchId={}, subscriptionId={}, eventCount={}, size={}bytes" |

---

## 11. Мониторинг (новые/изменённые метрики)

### Новые метрики

#### webhook_subscriptions_total
- **Тип**: Gauge
- **Описание**: Общее количество webhook-подписок

| Тег | Обязательность | Описание | Пример значения |
|-----|---------------|----------|----------------|
| active | Обязательно | Статус подписки | "true" / "false" |

#### webhook_batch_sent_total
- **Тип**: Counter
- **Описание**: Счётчик отправленных webhook-батчей

| Тег | Обязательность | Описание | Пример значения |
|-----|---------------|----------|----------------|
| subscriptionId | Условно | ID подписки | "sub-uuid-12345" |
| status | Обязательно | Статус доставки | "success" / "failed" / "retrying" |

#### webhook_events_delivered_total
- **Тип**: Counter
- **Описание**: Счётчик доставленных событий через webhook

| Тег | Обязательность | Описание | Пример значения |
|-----|---------------|----------|----------------|
| channel | Обязательно | Канал уведомления | "email" / "push" / "sms" |
| eventType | Обязательно | Тип события | "sent" / "failed" / "delivered" |
| subscriptionId | Условно | ID подписки | "sub-uuid-12345" |

#### webhook_delivery_duration
- **Тип**: Timer (Histogram)
- **Описание**: Время доставки webhook-батча

| Тег | Обязательность | Описание | Пример значения |
|-----|---------------|----------|----------------|
| subscriptionId | Условно | ID подписки | "sub-uuid-12345" |
| status | Обязательно | Статус доставки | "success" / "failed" |

**Перцентили:** 0.5, 0.75, 0.9, 0.95, 0.99

#### webhook_dlt_count
- **Тип**: Counter
- **Описание**: Счётчик webhook'ов, попавших в DLT

| Тег | Обязательность | Описание | Пример значения |
|-----|---------------|----------|----------------|
| subscriptionId | Условно | ID подписки | "sub-uuid-12345" |
| reason | Обязательно | Причина DLT | "retries_exhausted" / "client_error" |

### Новые вычисляемые метрики

| Метрика | Описание | Формула (PromQL) |
|---------|----------|-----------------|
| webhook_delivery_rate | % успешных доставок за 5 мин | `(sum(rate(webhook_batch_sent_total{status="success"}[5m])) / sum(rate(webhook_batch_sent_total[5m]))) * 100` |
| webhook_avg_delivery_time | Среднее время доставки (p95) | `histogram_quantile(0.95, rate(webhook_delivery_duration_bucket[5m]))` |
| webhook_dlt_rate | % webhook'ов в DLT за 5 мин | `(sum(rate(webhook_dlt_count[5m])) / sum(rate(webhook_events_delivered_total[5m]))) * 100` |

---

## 12. Изменения конфигурации

### ADDED

| Параметр | Обяз. | Тип | Значение по умолчанию | Описание |
|----------|-------|-----|----------------------|----------|
| webhook.batch.enabled | нет | bool | false | Feature flag: включение пакетной отправки webhook'ов |
| webhook.batch.size | нет | int | 100 | Максимальное количество событий в одном батче |
| webhook.batch.interval-ms | нет | int | 5000 | Интервал отправки батча (мс), даже если не заполнен |
| webhook.batch.scheduler.cron | нет | string | */5 * * * * * | Расписание планировщика отправки батчей |
| webhook.http.timeout-ms | нет | int | 10000 | Таймаут HTTP-вызова при отправке webhook (мс) |
| webhook.retry.max-attempts | нет | int | 5 | Максимальное количество retry-попыток |
| webhook.retry.backoff-ms | нет | int | 2000 | Начальная задержка между retry (мс) |
| webhook.dlt.topic | нет | string | WEBHOOK.DLT | Топик для failed webhook'ов |
| webhook.dlt.reprocess.enabled | нет | bool | false | Автоматический репроцессинг DLT |
| webhook.dlt.reprocess.cron | нет | string | 0 0 * * * * | Расписание репроцессинга DLT |
| webhook.security.signature.enabled | нет | bool | true | Включение HMAC-SHA256 подписи webhook'ов |
| webhook.security.header.prefix | нет | string | whsec_ | Рекомендуемый префикс для секрета |
| cassandra.tables.webhook_subscriptions.ttl | нет | int | 0 | TTL для таблицы webhook_subscriptions (0 = бессрочно) |
| cassandra.tables.webhook_batches.ttl | нет | int | 259200 | TTL для таблицы webhook_batches (3 дня в секундах) |

---

## 13. Критерии приёмки

| # | КОГДА | ТОГДА |
|---|-------|-------|
| 1 | Создана webhook-подписка через POST /api/v1/webhook/subscriptions с валидными данными | Подписка сохранена в Cassandra, возвращён subscriptionId, метрика webhook_subscriptions_total инкрементирована |
| 2 | При создании подписки указан некорректный URL (не HTTPS) | Возвращается HTTP 400 с errorCode INVALID_INPUT, запись в лог WARN, метрика validation ошибок инкрементирована |
| 3 | При создании подписки секрет короче 32 символов | Возвращается HTTP 400 с errorCode INVALID_WEBHOOK_SECRET |
| 4 | Накоплено 100 событий в батче (batch.size=100) | Батч немедленно отправлен подписчику, метрика webhook_batch_sent_total инкрементирована |
| 5 | Прошло 5000 мс с момента создания батча (batch.interval-ms=5000) | Батч отправлен подписчику (даже если содержит <100 событий) |
| 6 | Webhook endpoint вернул HTTP 200 | Статус батча: SENT, метрика webhook_events_delivered_total инкрементирована |
| 7 | Webhook endpoint вернул HTTP 500 (таймаут) | Выполняется retry до 5 попыток с экспоненциальной задержкой, статус: RETRYING |
| 8 | Все retry попытки исчерпаны (5 раз ошибка) | Статус батча: FAILED, запись в WEBHOOK.DLT, метрика webhook_dlt_count инкрементирована, алерт в мониторинг |
| 9 | Webhook endpoint вернул HTTP 400 | Retry НЕ выполняются, статус: FAILED (client error), запись в лог WARN |
| 10 | Webhook endpoint вернул HTTP 429 с хедером Retry-After: 60 | Следующая попытка через 60 секунд, запись в лог WARN |
| 11 | Событие имеет priority=high | Отправлено немедленно без пакетирования, вне очереди батча |
| 12 | Получатель webhook'а проверяет HMAC-подпись | Подпись валидна при использовании правильного secret из подписки |
| 13 | В батче содержится событие с каналом "email", а подписчик подписан только на "push" | Событие НЕ включается в батч для этого подписчика |
| 14 | Подписка деактивирована (active=false) | События НЕ отправляются этому подписчику, батч не формируется |
| 15 | Feature flag webhook.batch.enabled=false | Webhook'и не отправляются ни при каких условиях, API управления подписками недоступно (404) |
| 16 | Cassandra недоступна при старте сервиса | Readiness Probe возвращает false, health check failed |
| 17 | Отправка webhook'а с кастомными хедерами из конфигурации | Хедеры X-Custom-* корректно добавлены в HTTP-запрос |
| 18 | Получатель webhook'а возвращает дублирующийся webhookId | Возвращается HTTP 200 без повторной обработки (идемпотентность) |
