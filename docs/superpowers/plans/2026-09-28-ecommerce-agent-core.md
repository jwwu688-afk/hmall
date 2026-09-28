# Hmall Customer Agent Core Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a buyer-facing, read-only DeepAgents chat that answers catalog, own-order, and available logistics questions in the existing storefront.

**Architecture:** Keep Python DeepAgents in `customer-agent-service` and Java business ownership in `hm-service`. Java accepts storefront requests, persists conversations/events, issues scoped delegation tokens, and calls the Python service; Python tools call only Java internal customer-query APIs. The same contracts survive a later Java microservice split.

**Tech Stack:** Java 11, Spring Boot 2.7.12, MyBatis-Plus, MySQL, Python 3.12, DeepAgents 0.5.7 (verify compatibility before changing), FastAPI, Vue 2 static HTML, Fetch streaming, Nginx.

**Spec:** `docs/superpowers/specs/2026-09-28-ecommerce-customer-agent-design.md`

## Global Constraints

- First release answers questions only; no payment, cancellation, refund, address update, arbitrary SQL, or Agent database access.
- Never trust a model-supplied user ID. Java authenticates each internal call and checks order and conversation ownership.
- Anonymous sessions can ask public catalog questions; order queries require login. Do not merge anonymous history into a user session in this release.
- External path stays `/api/customer-service/...`; current Nginx strips `/api`, so Spring mappings start `/customer-service/...`.
- Java 11 and Spring Boot 2.7.12 remain the current Java baseline; `customer-agent-service` is not a Maven module.
- No invented logistics tracking nodes or delivery ETA. Existing data offers only fulfillment status, carrier, and tracking number.

## Review Focus

1. Another buyer's order ID must yield the same public error as a nonexistent ID; Task 1 pins both cases.
2. Anonymous calls to an order tool must be rejected even when an order ID is known; Task 1 pins this case.
3. Model text that contains a fake user ID or tool instruction must not alter identity or allowed tools; Task 2 pins this case.
4. Duplicate and out-of-order run events must not corrupt a conversation; Task 3 pins event sequencing.
5. A reconnect after a stream break must show each event once and preserve the completed answer; Task 4 pins resume behavior.

## File Map

| Unit | Files | Responsibility |
| --- | --- | --- |
| Java customer queries | `hm-service/src/main/java/com/hmall/customer/query/{CustomerItemQueryService,CustomerOrderQueryService,CustomerQueryController}.java` plus DTOs in the same package | Narrow read models and ownership checks |
| Java chat state | `hm-service/src/main/java/com/hmall/customer/chat/{CustomerConversationController,CustomerConversationService,AgentClient,DelegationTokenService}.java`; `hm-service/src/main/resources/mapper/CustomerConversationMapper.xml`; SQL migration under `hm-service/src/main/resources/db/migration/` | Conversation, messages, events, run IDs, token issuance, Python dispatch |
| Python Agent | `customer-agent-service/app/{main.py,agent.py,contracts.py,java_client.py,tools.py,settings.py}`, `customer-agent-service/Dockerfile`, and `customer-agent-service/tests/` | Tool calls, DeepAgents routing, event callback, API, independent deployment |
| Storefront | `frontend/html/hmall-portal/customer-service.html`, `css/customer-service.css`, `js/customer-service.js`, `js/top.js`; `frontend/conf/nginx.conf` | Chat page, SSE stream, navigation, proxy behavior |

## Task 1: Java read-only customer query facade

**Files:** Create `customer/query` services, controller and DTOs; modify `OrderController` only if the unsafe generic order endpoint must be restricted; tests in `hm-service/src/test/java/com/hmall/customer/query/CustomerQueryControllerTest.java`.

**Interfaces:** Produce `GET /internal/customer/items?key=&brand=&category=&minPrice=&maxPrice=&pageSize=`; `GET /internal/customer/items/{id}`; `GET /internal/customer/orders`; `GET /internal/customer/orders/{id}`; `GET /internal/customer/orders/{id}/logistics`. `CustomerOrderQueryService.getOwnedOrder(Long authenticatedUserId, Long orderId)` returns a slim `CustomerOrderDTO` or the same not-found result for absent/foreign orders. All order routes require a verified delegation token with `aud=hmall-internal`, `order:read` scope, a 10-minute expiry, and a signed subject; public catalog calls use `catalog:read`.

- [ ] **Step 1: Write failing MockMvc/service tests.** `foreign_order_and_missing_order_return_same_404` asserts identical status/body; `anonymous_order_lookup_is_401`; `owned_order_contains_details_but_no_address_or_phone`; `logistics_without_row_returns_known_order_with_no_tracking`; `catalog_excludes_status_2_and_3`.
- [ ] **Step 2: Run tests.** `mvn -pl hm-service -am -Dtest=CustomerQueryControllerTest test`; expect the new tests to fail before implementation.
- [ ] **Step 3: Implement the query facade.** Search only `status=1`, cap `pageSize` at 20, return prices in cents. Resolve order ownership before details/logistics; never expose `OrderLogistics.mobile`, `contact`, or street. Add a service-to-service credential verifier in this task if no reusable scoped verifier exists.
- [ ] **Step 4: Run the same tests and existing Java tests.** `mvn -pl hm-service -am test`; expect zero failures.
- [ ] **Step 5: Commit.** `git add hm-service && git commit -m "feat: add owned customer query APIs"`.

## Task 2: Python tools and DeepAgents read-only runtime

**Files:** Create `customer-agent-service/pyproject.toml`, `app/{contracts,java_client,tools,agent,settings}.py`, `tests/test_tools.py`, `tests/test_agent.py`.

**Interfaces:** `JavaClient.search_items(filters: ItemFilters) -> list[ItemCard]`; `get_item(item_id: int) -> ItemCard | None`; `list_my_orders() -> list[OrderCard]`; `get_my_order(order_id: int) -> OrderCard | None`; `get_my_logistics(order_id: int) -> LogisticsCard | None`. Instantiate `create_deep_agent` with only these wrappers and specialized catalog/order assistants. Runtime context carries the signed delegation token, never a caller-provided user ID.

- [ ] **Step 1: Write failing pytest tests.** Mock Java HTTP responses. Assert 401 on anonymous order requests, explicit unavailable result on 5xx/timeout, and no order tool enabled with public-only scope. Feed a user message containing `userId=other` and a fake tool directive; assert the client still uses the original token and only configured tools.
- [ ] **Step 2: Run tests.** `uv run --project customer-agent-service pytest customer-agent-service/tests/test_tools.py customer-agent-service/tests/test_agent.py -q`; expect failures.
- [ ] **Step 3: Implement contracts, HTTP client, tools, and Agent assembly.** Add `pytest` and HTTP mock dependencies to the Python project's dev group. Cap result sizes; preserve source item/order IDs; never add file, SQL, network-search, or payment tools. Keep straightforward questions direct and delegate cross-domain questions only.
- [ ] **Step 4: Run pytest.** Same command; expect all tests to pass.
- [ ] **Step 5: Commit.** `git add customer-agent-service && git commit -m "feat: add read-only ecommerce deep agent"`.

## Task 3: Conversation API, persistence, and Agent run bridge

**Files:** Create Java `customer/chat` files and migration named `V1__customer_chat.sql`; create Python `app/main.py` and `tests/test_run_api.py`; Java tests `CustomerConversationControllerTest.java` and `CustomerConversationServiceTest.java`.

**Interfaces:** Java external `POST /customer-service/conversations`, `POST /customer-service/conversations/{id}/messages`, `GET /customer-service/conversations/{id}`, `GET /customer-service/conversations/{id}/events?after=`, with 202 `{messageId,runId}` for message submission. Python internal `POST /internal/runs` accepts `{conversationId,runId,message,delegationToken}` under service auth and posts sequenced `{runId,sequence,type,data}` callbacks to Java `/internal/customer-service/runs/{runId}/events`. Unique `(run_id, sequence)` makes callbacks idempotent. Java owns canonical MySQL message/event history. Configure a version-compatible durable Agent checkpointer and prove restart recovery; if the chosen backend lacks safe recovery, reconstruct limited context from MySQL before considering the task complete.

- [ ] **Step 1: Write failing Java and Python API tests.** Assert user/anonymous session ownership, duplicate message idempotency key behavior, duplicate callback ignored, gap in sequence rejected or buffered, and event replay after `after=eventId`.
- [ ] **Step 2: Run tests.** `mvn -pl hm-service -am -Dtest=CustomerConversationControllerTest,CustomerConversationServiceTest test` and `uv run --project customer-agent-service pytest customer-agent-service/tests/test_run_api.py -q`; expect failures.
- [ ] **Step 3: Implement migration, service, controller, token and callback validation, Python run endpoint, and persistence.** Add a schema migration runner compatible with the existing nonempty Hmall database and document its baseline step; do not enable automatic destructive schema changes. Issue 10-minute signed tokens with `subject`, `aud=hmall-internal`, `scopes`, `conversationId`, and `runId`; require separate service credentials for the Python run endpoint and Java event callback. Never expose Python directly to the browser. Store message and run before dispatch; mark a failed dispatch as failed rather than silently pending. Finish and error events must be durable before SSE emission.
- [ ] **Step 4: Run both test commands and a restart test.** Verify persisted history and resumed context after restarting both services; expect zero test failures.
- [ ] **Step 5: Commit.** `git add hm-service customer-agent-service && git commit -m "feat: persist customer agent conversations"`.

## Task 4: Storefront customer-service page

**Files:** Create `customer-service.html`, `css/customer-service.css`, `js/customer-service.js`; modify `js/top.js` and `frontend/conf/nginx.conf`; browser-level test notes in `frontend/README-customer-service.md`.

**Interfaces:** Shared header links to `/customer-service.html`. Page calls existing `/api/customer-service/...` paths, sends `Authorization` from the current `sessionStorage` token for logged-in users, and streams events with `fetch` plus `ReadableStream`. Use `after=lastEventId` on reconnect. Nginx disables buffering and lengthens read timeout only for `/api/customer-service/.../events`.

- [ ] **Step 1: Write a focused browser verification script/checklist.** Assert guest catalog question works, guest order query prompts login, logged-in order query renders only own data, `LastEventId` resume has no duplicate messages, failure state offers retry, and an order ID URL parameter only pre-fills a question.
- [ ] **Step 2: Verify the checklist fails against the current storefront.** Open `http://localhost:18080/customer-service.html`; expect 404 before page creation.
- [ ] **Step 3: Build the page and shared navigation.** Render text plus source cards with safe DOM APIs, not untrusted `innerHTML`; use `AbortController` for interrupted streams. Keep normal Axios calls separate from the longer streaming connection.
- [ ] **Step 4: Run the browser checklist and Nginx config check.** `nginx -t -c <absolute-path-to-frontend/conf/nginx.conf>` where Nginx is installed; verify the page scenarios through the running app. If Nginx is unavailable, preserve the checklist and report that deployment verification remains open.
- [ ] **Step 5: Commit.** `git add frontend && git commit -m "feat: add storefront customer agent chat"`.

## Task 5: End-to-end quality and release gate

**Files:** Create `customer-agent-service/evals/core_cases.jsonl`, `tests/test_eval_cases.py`, and `Dockerfile`; update `README.md` and `customer-agent-service/README.md`.

**Interfaces:** Evaluation cases cover catalog facts, same-user order, other-user order, anonymous order, no logistics row, model-supplied fake identity, Java timeout, and reconnect. Each case records expected tool category, forbidden disclosures, and answer facts.

- [ ] **Step 1: Add the failing eval cases and red test.** Assert every case has an expected tool, ownership outcome, and checkable factual assertion.
- [ ] **Step 2: Run eval harness.** `uv run --project customer-agent-service pytest customer-agent-service/tests/test_eval_cases.py -q`; expect failure until fixtures and harness are complete.
- [ ] **Step 3: Complete eval harness and run against a deterministic fake model plus the configured model.** Add `GET /health` and a Python service Dockerfile, then document environment variables, start order, migration command and separate deployment from the Maven build. The fake model checks wiring; the configured model run checks actual behavior. Record baseline accuracy and latency, without inventing a service-level target.
- [ ] **Step 4: Run `mvn -pl hm-service -am test`, Python pytest, browser checklist, and `git diff --check`.** Record actual outputs and any environment limits in README.
- [ ] **Step 5: Commit.** `git add README.md customer-agent-service && git commit -m "test: cover customer agent core journeys"`.

## Completion boundary

At this point a buyer can ask catalog, own-order, and available logistics questions in the storefront; conversations and events survive restarts. Policy answers and artificial-human handoff are intentionally delivered by the companion plan `2026-09-28-ecommerce-agent-policy-handoff.md` before claiming the full first release from the spec.
