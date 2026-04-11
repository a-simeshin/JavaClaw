# Plan: Port upstream commit 2dbb061 — Telegram Markdown→HTML

## Task Description

Port upstream commit `2dbb061` ("Add Markdown to HTML conversion for Telegram messages", jobrunr/JavaClaw#32) into this fork manually. The commit cannot be cherry-picked because the upstream uses Gradle + package `org.jobrunr.channels.telegram` while the fork uses Maven + `ai.javaclaw.channels.telegram` and has diverged significantly (RoutingContext + ChannelContextService instead of stateful `chatId`). After the port, merge `upstream/main` into `develop` so that the "1 commit behind jobrunr/JavaClaw:main" banner disappears on GitHub.

## Objective

- The fork's `TelegramChannel` converts agent responses from Markdown to Telegram-flavored HTML before sending, with a fallback to raw text on `TelegramApiException`.
- Two new unit tests pass (HTML conversion + fallback).
- All existing `javaclaw-channel-telegram` unit tests stay green.
- `develop` is no longer behind `upstream/main` (GitHub banner shows "N commits ahead, 0 commits behind").

## Problem Statement

The fork's Telegram plugin sends raw agent output, so Markdown produced by LLMs (`**bold**`, `[link](url)`, lists) appears literally in Telegram instead of being rendered. Upstream shipped a fix in #32 that parses Markdown via `commonmark` + `commonmark-ext-gfm-strikethrough`, renders Telegram-compatible HTML (only `<b>`, `<i>`, `<a>`, `<code>`, `<pre>`, `<s>` are supported), and gracefully falls back to plain text if Telegram rejects the HTML. We want that behavior without adopting the upstream's stateful `chatId` field — the fork already uses `RoutingContext` and must preserve that model.

## Solution Approach

1. Add `commonmark` + `commonmark-ext-gfm-strikethrough` dependencies. Version property goes into the parent `pom.xml` `<properties>` and into `<dependencyManagement>`; the module `pom.xml` only declares the dependency, matching how `telegrambots.version` is managed (parent `pom.xml:45,96-155`).
2. In `javaclaw-channel-telegram/.../TelegramChannel.java`:
   - Add static `Parser MARKDOWN_PARSER` and `HtmlRenderer HTML_RENDERER` with `escapeHtml(true)` and the Strikethrough extension, mirroring the upstream snippet.
   - Add `private static String convertMarkdownToTelegramHtml(String)` — identical body to upstream (null/blank → `""`, parse, render, then the same String replacements for `<p>`, `<h1..6>`, `<li>`, `<ul>`, `<ol>`, `<hr />`, `.trim()`).
   - Modify the existing instance method `sendTelegram(long chatId, Integer messageThreadId, String message)` (`TelegramChannel.java:117-128`) to:
     1. Build `SendMessage` with `.text(convertMarkdownToTelegramHtml(message)).parseMode(ParseMode.HTML)`.
     2. On `TelegramApiException`, log a warning and retry with a second `SendMessage` that has no `parseMode` and uses the original raw `message`. Only if the fallback also fails do we throw `RuntimeException`.
   - Keep the existing signature, visibility (package-private), and callers (`consume`, `sendMessage(RoutingContext,...)`, the "I'm sorry" branch) untouched — they all already go through `sendTelegram`, so both live paths inherit the new behavior for free.
3. In `TelegramChannelTest.java`, add two new tests in a new "Message formatting (Markdown → HTML)" section (mirroring upstream), adapted to the fork's helper `updateFrom(...)` and the channel factory `channel(...)`. Tests must verify:
   - `sendMessageConvertsMarkdownToTelegramHtml` — agent returns `"Here is **bold** text and a [link](https://example.com)"`, the `SendMessage` sent has `ParseMode.HTML` and text `"Here is <strong>bold</strong> text and a <a href=\"https://example.com\">link</a>"`.
   - `sendMessageFallbacksToSendingRawTextWhenFailingToSendHtml` — first `execute(...)` call with `ParseMode.HTML` throws `TelegramApiException`, second call (no `parseMode`, raw text) succeeds and is verified.
   - Existing `ignoresMessagesFromNullUsername` / `ignoresMessagesFromUnauthorizedUser` tests assert `msg.getText().contains("I'm sorry")` — this still holds because plain-ASCII "I'm sorry, I don't accept instructions from you." is rendered unchanged by commonmark (escapeHtml preserves the apostrophe as `&#39;`? — verify in the test run; if the HTML renderer escapes `'` into `&#39;`, update those two tests to assert on the fallback-compatible substring, e.g. `msg.getText().contains("sorry")`).
4. Run `./mvnw -pl javaclaw-channel/javaclaw-channel-telegram -am test` until green.
5. Merge `upstream/main` into `develop` with `--no-ff`. Because the port is a manual reimplementation of the same change, there will be a textual conflict in `plugins/telegram/...` paths (which do not exist in the fork) — resolve by deleting the upstream paths (`git rm`). After the merge, the banner flips from "128 ahead / 1 behind" to "129 ahead / 0 behind".

## Relevant Files

Use these files to complete the task:

- `pom.xml` — parent. Add `<commonmark.version>0.21.0</commonmark.version>` near line 45 and two `<dependency>` entries in `<dependencyManagement>` near lines 96-155 (mirror the `telegrambots` pattern).
- `javaclaw-channel/javaclaw-channel-telegram/pom.xml` — add the two `commonmark` dependencies (no version, managed by parent).
- `javaclaw-channel/javaclaw-channel-telegram/src/main/java/ai/javaclaw/channels/telegram/TelegramChannel.java` — core edit target. `sendTelegram(...)` at line 117 gets the HTML + fallback logic. New static constants (`MARKDOWN_PARSER`, `HTML_RENDERER`) and private method `convertMarkdownToTelegramHtml(...)` added.
- `javaclaw-channel/javaclaw-channel-telegram/src/test/java/ai/javaclaw/channels/telegram/TelegramChannelTest.java` — add two new tests; possibly adjust the two `"I'm sorry"` assertions if HTML renderer escapes the apostrophe.
- Upstream reference: commit `2dbb061` in remote `upstream` — inspect with `git show 2dbb061 -- 'plugins/telegram/src/**'`. Use it as the canonical source for the `convertMarkdownToTelegramHtml` body and test expectations.
- `.claude/projects/.../memory/project_enterprise_vision.md` — confirms the fork keeps Telegram as a live channel, so the port is in scope.

### New Files

None. All changes are edits to existing files.

## Team Orchestration

- You operate as the team lead and orchestrate the team to execute the plan.
- You're responsible for deploying the right team members with the right context to execute the plan.
- IMPORTANT: You NEVER operate directly on the codebase. You use `Task` and `Task*` tools to deploy team members to the building, validating, testing, deploying, and other tasks.
  - This is critical. Your job is to act as a high level director of the team, not a builder.
  - Your role is to validate all work is going well and make sure the team is on track to complete the plan.
  - You'll orchestrate this by using the Task* Tools to manage coordination between the team members.
  - Communication is paramount. You'll use the Task* Tools to communicate with the team members and ensure they're on track to complete the plan.
- Take note of the session id of each team member. This is how you'll reference them.

### Team Members

- Builder
  - Name: `builder-telegram`
  - Role: Edit `pom.xml`s and `TelegramChannel.java` to add the commonmark dependency and the Markdown→HTML + fallback logic. Does NOT touch tests.
  - Agent Type: `general-purpose`
  - Resume: true
- Builder
  - Name: `test-builder-telegram`
  - Role: Add the two new unit tests, adapt any existing assertions affected by HTML escaping, run `./mvnw -pl javaclaw-channel/javaclaw-channel-telegram -am test` until green.
  - Agent Type: `general-purpose`
  - Resume: true
- Validator
  - Name: `validator-merge`
  - Role: After tests are green, perform the `git merge upstream/main` into `develop`, resolve the expected upstream-path conflict by `git rm`-ing the `plugins/telegram/**` paths that don't exist in the fork, verify `git log upstream/main..develop` is empty of "behind" commits, and confirm the full test suite still passes via `./mvnw -q -pl javaclaw-channel/javaclaw-channel-telegram -am test`. Read-only on code; only merge operations on git.
  - Agent Type: `validator`
  - Resume: false

## Testing Strategy

Test pyramid ratio: **80% unit / 15% integration-API / 5% UI e2e**

This is a channel adapter plugin with no controllers, no DB, and no UI. The natural pyramid collapses to 100% unit tests — documented explicitly so the Stop hook knows integration/e2e are intentionally empty.

### Unit Tests (80% → 100% here)

- `TelegramChannelTest.sendMessageConvertsMarkdownToTelegramHtml` — bold + link Markdown is rendered as `<strong>` + `<a href=...>`, `ParseMode.HTML` is set on the SendMessage.
- `TelegramChannelTest.sendMessageFallbacksToSendingRawTextWhenFailingToSendHtml` — first HTML execute throws `TelegramApiException`, second raw-text execute is issued without `parseMode`, raw Markdown is preserved in `msg.getText()`.
- Keep all 16 existing tests green (ignored updates, username matching, conversation id, routing context save, `sendMessage` via `RoutingContext`). If `escapeHtml(true)` converts `'` to `&#39;`, update the two `"I'm sorry"` assertions to use `contains("sorry")` instead of `contains("I'm sorry")`.

### Integration / API Tests (15%)

None. The Telegram bot is not wired into a `@SpringBootTest` harness in this fork — upstream didn't add one either, and there is no existing integration-test baseline for this module.

### UI E2E Tests (5%)

None. No UI surface changes.

## Step by Step Tasks

- IMPORTANT: Execute every step in order, top to bottom. Each task maps directly to a `TaskCreate` call.
- Before you start, run `TaskCreate` to create the initial task list that all team members can see and execute.

### 1. Add commonmark dependencies

- **Task ID**: `add-commonmark-deps`
- **Depends On**: none
- **Assigned To**: `builder-telegram`
- **Agent Type**: `general-purpose`
- **Stack**: `java maven spring`
- **Parallel**: false
- **Tests**: No new tests. Existing `TelegramChannelTest` must still compile and pass (dependency addition only).
- Edit parent `pom.xml`: add `<commonmark.version>0.21.0</commonmark.version>` inside `<properties>` alongside `telegrambots.version`; add two `<dependency>` entries inside `<dependencyManagement>` — `org.commonmark:commonmark` and `org.commonmark:commonmark-ext-gfm-strikethrough`, both with `<version>${commonmark.version}</version>`.
- Edit `javaclaw-channel/javaclaw-channel-telegram/pom.xml`: add the two `<dependency>` entries (no `<version>`), under the existing `telegrambots-client` block.
- Run `./mvnw -pl javaclaw-channel/javaclaw-channel-telegram -am -DskipTests compile` to prove the module still compiles with the new deps on the classpath.

### 2. Implement Markdown→HTML conversion + fallback

- **Task ID**: `impl-markdown-html`
- **Depends On**: `add-commonmark-deps`
- **Assigned To**: `builder-telegram`
- **Agent Type**: `general-purpose`
- **Stack**: `java spring`
- **Parallel**: false
- **Tests**: Requires the two new tests from `write-tests` to prove correctness. Must not break the 16 existing tests.
- Open `TelegramChannel.java`. Add imports: `org.commonmark.node.Node`, `org.commonmark.parser.Parser`, `org.commonmark.renderer.html.HtmlRenderer`, `org.commonmark.ext.gfm.strikethrough.StrikethroughExtension`, `org.telegram.telegrambots.meta.api.methods.ParseMode`, `java.util.List`.
- Add static constants above the existing `private static final Logger log = ...`:

  ```java
  private static final Parser MARKDOWN_PARSER = Parser.builder().build();
  private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder()
          .escapeHtml(true)
          .extensions(List.of(StrikethroughExtension.create()))
          .build();
  ```
- Replace the body of `sendTelegram(long chatId, Integer messageThreadId, String message)` (currently lines 117-128) with:
  1. `final String formattedHtmlMessage = convertMarkdownToTelegramHtml(message);`
  2. Build `SendMessage htmlMessage` with `.text(formattedHtmlMessage).parseMode(ParseMode.HTML)`.
  3. `try { telegramClient.execute(htmlMessage); } catch (TelegramApiException e) { log.warn("Failed to send HTML parsed message, falling back to raw text.", e); SendMessage fallback = SendMessage.builder().chatId(chatId).messageThreadId(messageThreadId).text(message).build(); try { telegramClient.execute(fallback); } catch (TelegramApiException fx) { throw new RuntimeException("Failed to send both HTML and fallback messages", fx); } }`.
- Add `private static String convertMarkdownToTelegramHtml(String markdown)` with exactly the upstream body: null/blank guard returning `""`, `Node document = MARKDOWN_PARSER.parse(markdown);`, `String html = HTML_RENDERER.render(document);`, then the chain of `.replace(...)` / `.replaceAll(...)` calls for `<p>`, `<h1-6>`, `<li>`, `<ul>`, `<ol>`, `<hr />`, finishing with `.trim()`.
- Do NOT touch `consume`, `sendMessage(RoutingContext,...)`, `isAllowedUser`, `normalizeUsername`, or `getConversationId`. Do NOT reintroduce upstream's stateful `chatId` field — routing stays via `RoutingContext`.
- Run `./mvnw -pl javaclaw-channel/javaclaw-channel-telegram -am -DskipTests compile`.

### 3. Write tests

- **Task ID**: `write-tests`
- **Depends On**: `impl-markdown-html`
- **Assigned To**: `test-builder-telegram`
- **Agent Type**: `general-purpose`
- **Stack**: `java mockito assertj test structure`
- **Parallel**: false
- Add new section `// Message formatting (Markdown → HTML)` to `TelegramChannelTest.java` just above the `helpers` section.
- Add `sendMessageConvertsMarkdownToTelegramHtml`: stub `agent.respondTo(...)` to return `"Here is **bold** text and a [link](https://example.com)"`; call `channel.consume(updateFrom("allowed_user", "hello", 42L, 567));`; `verify(telegramClient).execute(argThat((SendMessage msg) -> ParseMode.HTML.equals(msg.getParseMode()) && "Here is <strong>bold</strong> text and a <a href=\"https://example.com\">link</a>".equals(msg.getText())));`.
- Add `sendMessageFallbacksToSendingRawTextWhenFailingToSendHtml`: stub `agent.respondTo(...)` to return `"Here is **bold** text and an image: ![An example image](/assets/images/clawrunr.png)"`; `when(telegramClient.execute(argThat((SendMessage m) -> ParseMode.HTML.equals(m.getParseMode())))).thenThrow(new TelegramApiException("Invalid HTML"));`; call `consume(...)`; `verify(telegramClient).execute(argThat((SendMessage msg) -> msg.getParseMode() == null && msg.getText().equals("Here is **bold** text and an image: ![An example image](/assets/images/clawrunr.png)")));`.
- Import `org.telegram.telegrambots.meta.api.methods.ParseMode`.
- Run `./mvnw -pl javaclaw-channel/javaclaw-channel-telegram -am test`. If the two `ignoresMessagesFromNullUsername` / `ignoresMessagesFromUnauthorizedUser` tests now fail because `"I'm sorry"` became `"I&#39;m sorry"` in the HTML render, relax those assertions to `msg.getText().contains("sorry")`. Do NOT change production code to "keep the apostrophe" — HTML escaping is the correct behavior.
- Iterate until `BUILD SUCCESS` with `Tests run: 18, Failures: 0, Errors: 0, Skipped: 0`.

### 4. Merge upstream/main into develop

- **Task ID**: `merge-upstream`
- **Depends On**: `write-tests`
- **Assigned To**: `validator-merge`
- **Agent Type**: `validator`
- **Stack**: `java maven`
- **Parallel**: false
- **Tests**: Full `javaclaw-channel-telegram` suite must stay green after merge.
- Before merging, create a safety branch: `git branch backup/pre-telegram-merge develop`.
- `git merge --no-ff upstream/main -m "Merge upstream jobrunr/JavaClaw main (telegram markdown→html ported manually)"`.
- Expected conflict set: upstream touched `plugins/telegram/build.gradle`, `plugins/telegram/src/main/java/ai/javaclaw/channels/telegram/TelegramChannel.java`, `plugins/telegram/src/test/java/ai/javaclaw/channels/telegram/TelegramChannelTest.java`. None of these paths exist in the fork (the fork is Maven + `javaclaw-channel/javaclaw-channel-telegram`). Resolve with `git rm` on each conflicted `plugins/telegram/**` path — they must NOT be added to the fork. Do NOT copy any upstream file into the fork tree.
- `git status` must show a clean merge; `git diff HEAD~1` must not reintroduce a `plugins/telegram/` directory.
- `git log upstream/main..develop --oneline` — verify the upstream commit `2dbb061` is absorbed (the merge commit makes upstream an ancestor).
- `git log develop..upstream/main --oneline` — must be empty (proves the "1 commit behind" banner will clear on push).
- Re-run `./mvnw -pl javaclaw-channel/javaclaw-channel-telegram -am test`. Must remain `BUILD SUCCESS`.
- Do NOT push. Stop and report back to the user — pushing `develop` is the user's decision.

### 5. Final validation

- **Task ID**: `validate-all`
- **Depends On**: `add-commonmark-deps`, `impl-markdown-html`, `write-tests`, `merge-upstream`
- **Assigned To**: `validator-merge`
- **Agent Type**: `validator`
- **Stack**: `java maven surefire mockito`
- **Parallel**: false
- Run `./mvnw -q -pl javaclaw-channel/javaclaw-channel-telegram -am test` — expect `BUILD SUCCESS` and 18 tests run.
- Run `git log --oneline -1 develop` — expect the merge commit as HEAD.
- Run `git rev-list --count develop ^upstream/main` — expect `>= 129` (one more than before).
- Run `git rev-list --count upstream/main ^develop` — expect `0`.
- Run `git status` — expect clean working tree.
- Post-merge sanity on the rest of the build: `./mvnw -q -DskipTests install` (or at minimum `./mvnw -q -pl javaclaw-channel -am compile`) — ensures no transitive classpath regression from the new `commonmark` jars.
- Report all five results to the user.

## Acceptance Criteria

- [ ] `pom.xml` declares `commonmark.version=0.21.0` and both commonmark artifacts in `<dependencyManagement>`.
- [ ] `javaclaw-channel-telegram/pom.xml` depends on `org.commonmark:commonmark` and `org.commonmark:commonmark-ext-gfm-strikethrough` (versions inherited).
- [ ] `TelegramChannel.sendTelegram` sends `ParseMode.HTML` with converted text and falls back to raw text on `TelegramApiException`, only throwing if the fallback also fails.
- [ ] `TelegramChannel` has no stateful `chatId` field — routing is still done via `RoutingContext`.
- [ ] `TelegramChannelTest` has two new passing tests; all previously passing tests still pass (18 total, 0 failures, 0 errors).
- [ ] `git log develop..upstream/main` is empty.
- [ ] `git log upstream/main..develop` contains the merge commit.
- [ ] No `plugins/telegram/` directory exists in the fork tree after the merge.
- [ ] Full test run for the module is `BUILD SUCCESS`.
- [ ] Working tree is clean; nothing pushed.

## Validation Commands

Execute these commands to validate the task is complete:

- `./mvnw -pl javaclaw-channel/javaclaw-channel-telegram -am -DskipTests compile` — module compiles with commonmark on the classpath.
- `./mvnw -pl javaclaw-channel/javaclaw-channel-telegram -am test` — all 18 unit tests pass (16 existing + 2 new).
- `git log develop..upstream/main --oneline` — must print nothing.
- `git log upstream/main..develop --oneline | head -5` — first line must be the merge commit.
- `git status` — must print `nothing to commit, working tree clean`.
- `git ls-files plugins/telegram` — must print nothing (upstream path not present in fork).
- `./mvnw -q -DskipTests install` — full build still assembles (optional but recommended smoke).

## Notes

- Upstream commit reference: `2dbb061352a7b9ac82070a8a9d074ffdf03e1e27` in remote `upstream` (GitHub: jobrunr/JavaClaw#32).
- The `commonmark` libraries (`0.21.0`) are MIT-licensed, small (~200 KB total), and already battle-tested — no supply-chain concern.
- `escapeHtml(true)` in `HtmlRenderer` will turn `'` into `&#39;`. The two existing "I'm sorry" tests assert via `contains("I'm sorry")` on plain text — after the port, the outbound HTML message will read `I&#39;m sorry, I don&#39;t accept instructions from you.`. The fix is to assert on `contains("sorry")` (or `contains("don") && contains("accept")`). Do NOT turn off HTML escaping — that would open an injection vector if a user's name ever leaked into the refusal message in the future.
- Upstream hit a minor snag: the fallback in #32 logs via `LOGGER` instead of `log`. The fork keeps the existing lowercase `log` naming convention (see `TelegramChannel.java:25`). Keep `log`, do not rename — it's stylistic churn unrelated to the bug fix.
- Do not add integration or e2e tests — the module has never had them and this port is not the time to introduce a new test tier.
- `TaskCreate` / `Task` tools are to be used by the executing (smart_build) session; this planning session must not deploy agents.
- After the user pushes `develop`, the GitHub banner should switch from "128 ahead / 1 behind" to "≥129 ahead / 0 behind".

