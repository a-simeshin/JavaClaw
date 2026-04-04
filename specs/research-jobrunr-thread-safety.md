# Research: JobRunr Thread-Safety for ChannelRegistry

## 1. JobRunr Threading Model

JobRunr's `BackgroundJobServer` maintains a **worker thread pool** that processes enqueued jobs concurrently.

- **Default worker count**: `availableProcessors * 16` (e.g., on an 8-core machine, 128 worker threads).
- **This project's configuration** (`app/src/main/resources/application.yaml`): `worker-count: ${JOBRUNR_WORKER_COUNT:10}` -- defaults to 10 worker threads.
- JobRunr supports both **PlatformThreads** (traditional) and **VirtualThreads** (lightweight, Java 21+).
- Each worker thread independently picks up and executes a job. The `@Job`-annotated method (`TaskHandler.executeTask()`) runs on one of these worker threads, **not** on the main thread or any Spring-managed request thread.
- JobRunr resolves Spring beans via a `JobActivator` (backed by the Spring `ApplicationContext`). This means the **same singleton** `TaskHandler` (and its injected `ChannelRegistry`) is shared across all worker threads.

## 2. How ChannelRegistry Is Used from Worker Threads

In `TaskHandler.executeTask()` (line 32), annotated with `@Job`, the method calls `channelRegistry.getLatestChannel()` (line 56) from the `notifyUser()` helper. This call:

1. Reads `lastChannelMessage` (an `AtomicReference` -- thread-safe).
2. Calls `channels.get(key)` on the internal `HashMap`.

Since `TaskHandler` is a Spring singleton and JobRunr worker threads all share it, **multiple worker threads can concurrently call `channels.get()`** on the same `HashMap` instance.

## 3. Can Channels Be Registered After Workers Start?

**Yes.** Channel registration happens in constructors of `@Component` classes:

|      Channel      |                            Registration point                             |
|-------------------|---------------------------------------------------------------------------|
| `ChatChannel`     | Constructor (line 48) -- `@Component`, created during Spring context init |
| `DiscordChannel`  | Constructor (line 34) -- created by a `@Configuration` class              |
| `TelegramChannel` | Constructor (line 42) -- created by a `@Configuration` class              |

While currently all channels are registered during Spring context initialization (before JobRunr workers start processing), the API allows dynamic registration:

- `registerChannel()` and `unregisterChannel()` are **public methods** with no guards.
- `ChannelRegistry` is a `@Service` -- any component can inject it and call `registerChannel()` at any time.
- A future plugin or hot-reload mechanism could register channels after startup.

There is also `unregisterChannel()` which performs `channels.remove()`, meaning the map is **not read-only** by design.

## 4. Is HashMap Safe for Read-Only Access After Initialization?

From the Java Memory Model (JMM) perspective:

- A `HashMap` that is **fully constructed and never modified** is safe for concurrent reads, **provided** there is a proper happens-before relationship between the writing thread (that populated the map) and all reading threads.
- In Spring, the application context initialization establishes a happens-before with the first use of beans. So if all `registerChannel()` calls complete during `@Component` construction, and JobRunr workers start after context initialization, there **is** a happens-before edge.
- **However**, the `HashMap` in `ChannelRegistry` is not effectively immutable:
  - `registerChannel()` and `unregisterChannel()` can be called at any time.
  - `defaultChannelName` (a non-volatile `String` field) is written during `registerChannel()` and read during `getLatestChannel()` -- this is a **visibility bug** even in the "read-only after init" scenario if the field write is not safely published.

**Verdict**: The current code is **not thread-safe** in the general case, and even in the restricted "write at init, read later" case, the `defaultChannelName` field lacks a visibility guarantee (it is not `volatile` and not in a `final` field).

## 5. Specific Thread-Safety Issues

|              Issue              |  Severity  |                                                                                                  Description                                                                                                   |
|---------------------------------|------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `HashMap` concurrent read/write | **High**   | If `registerChannel()` or `unregisterChannel()` is ever called while workers read, `HashMap.get()` can throw `ConcurrentModificationException` or return corrupt data (infinite loop on resize in older JDKs). |
| `defaultChannelName` visibility | **Medium** | Non-volatile field written in `registerChannel()`, read in `getLatestChannel()`. A worker thread may see a stale `null` value.                                                                                 |
| `unregisterChannel()` existence | **Medium** | The presence of `unregisterChannel()` means the map is mutable by API contract, even if not currently called at runtime.                                                                                       |

## 6. Recommendation: Use ConcurrentHashMap

**Switch from `HashMap` to `ConcurrentHashMap`** and make `defaultChannelName` volatile.

Rationale:
- `ConcurrentHashMap` is safe for concurrent reads and writes with no external synchronization.
- The performance overhead vs `HashMap` for reads is negligible (a few nanoseconds per lookup).
- It future-proofs the code for dynamic channel registration/unregistration.
- It eliminates the subtle JMM visibility concern with `defaultChannelName`.

### Recommended Fix

```java
package ai.javaclaw.channels;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ChannelRegistry {

    private final Map<String, Channel> channels;
    private final AtomicReference<ChannelMessageReceivedEvent> lastChannelMessage;
    private volatile String defaultChannelName;

    public ChannelRegistry() {
        this.channels = new ConcurrentHashMap<>();
        this.lastChannelMessage = new AtomicReference<>();
    }

    public void registerChannel(Channel channel) {
        channels.put(channel.getName(), channel);
        if (channels.size() == 1) {
            this.defaultChannelName = channel.getName();
        }
    }

    public void unregisterChannel(Channel channel) {
        channels.remove(channel.getName());
    }

    public Channel getLatestChannel() {
        if (lastChannelMessage.get() != null) {
            return channels.get(lastChannelMessage.get().getChannel());
        }
        return channels.get(defaultChannelName);
    }

    public void publishMessageReceivedEvent(ChannelMessageReceivedEvent event) {
        lastChannelMessage.set(event);
    }
}
```

**Changes**:
1. `HashMap` -> `ConcurrentHashMap` (line 14)
2. `defaultChannelName` -> `volatile` (line 16)

This is a minimal, low-risk change: two words changed, no API modification, no behavioral change.
