# Hmall Customer Agent Policy and Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the first-release customer service scope with cited policy answers and a durable, truthful handoff ticket.

**Architecture:** Add published/versioned policies and ticket records in Java; extend the existing Python DeepAgents service with a policy specialist and a narrowly scoped ticket tool. The storefront shows citations and ticket state. Implement after the core plan `2026-09-28-ecommerce-agent-core.md` is accepted and verified.

**Tech Stack:** Java 11, Spring Boot 2.7.12, MyBatis-Plus, MySQL, Python 3.12, DeepAgents, FastAPI, Vue 2 static HTML.

**Spec:** `docs/superpowers/specs/2026-09-28-ecommerce-customer-agent-design.md`

## Global Constraints

- No automated returns, refunds, order cancellation, address changes, or payment in this plan.
- A policy answer must identify the published policy ID/version and effective date; missing or conflicting policy yields uncertainty and handoff offer.
- Handoff means a durable queued ticket, not a promise of live human chat.
- Never report ticket creation until Java returns a persisted ticket ID. Duplicate requests with the same idempotency key return the same ticket.
- Order IDs attached to tickets must belong to the authenticated buyer; anonymous tickets carry no order ID.

## Review Focus

1. A superseded policy and a currently effective policy must not both be cited as current; Task 1 pins effective-version filtering.
2. Conflicting active policy snippets must yield an uncertainty answer, not an invented promise; Task 2 pins this case.
3. A retry after a network timeout must not make two tickets; Task 3 pins idempotency.
4. A foreign order ID in a handoff request must be rejected without exposing its owner; Task 3 pins ownership.
5. If ticket persistence fails, the storefront must say handoff failed and offer retry; Task 4 pins this case.

## File Map

| Unit | Files | Responsibility |
| --- | --- | --- |
| Published policies | `hm-service/src/main/java/com/hmall/customer/policy/{CustomerPolicy,CustomerPolicyController,CustomerPolicyService}.java`, migration `V2__customer_policy.sql` | Versioned policy retrieval |
| Agent policy tools | `customer-agent-service/app/{policy_tools.py,agent.py}`, tests | Structured retrieval and cited replies |
| Handoff | `hm-service/src/main/java/com/hmall/customer/ticket/{CustomerTicket,CustomerTicketController,CustomerTicketService}.java`, migration `V3__customer_ticket.sql`; Python `app/ticket_tools.py` | Durable queued ticket and summary |
| Storefront | `frontend/html/hmall-portal/{customer-service.html,js/customer-service.js,css/customer-service.css}` | Policy citation cards and ticket confirmation |

## Task 1: Published policy storage and retrieval

**Files:** Create policy model, migration, service and internal controller; test `hm-service/src/test/java/com/hmall/customer/policy/CustomerPolicyServiceTest.java`.

**Interfaces:** `GET /internal/customer/policies?query=&category=` returns at most five `PolicyExcerpt` records with `policyId`, `version`, `title`, `effectiveFrom`, `category`, `excerpt`. Only `PUBLISHED` records with `effectiveFrom <= now` and no newer effective version for the same policy key may be returned. Public categories are delivery, payment, return, and exchange; text content is maintained by authorized staff through controlled migration/admin workflow, not by the Agent.

- [ ] **Step 1: Write failing tests.** `only_current_published_version_is_returned`, `future_policy_is_hidden`, `empty_results_are_explicit`, `query_is_bounded_to_five`, and `malicious_policy_text_is_returned_as_data_only`.
- [ ] **Step 2: Run tests.** `mvn -pl hm-service -am -Dtest=CustomerPolicyServiceTest test`; expect failures.
- [ ] **Step 3: Implement model, schema, query and publication validation.** Use a deterministic policy key/version rule and a query cap. Add a small seed policy fixture for test/demo only; real merchant rules require owner review before publishing.
- [ ] **Step 4: Run Java tests.** `mvn -pl hm-service -am test`; expect zero failures.
- [ ] **Step 5: Commit.** `git add hm-service && git commit -m "feat: add published customer policies"`.

## Task 2: Policy specialist and evidence-based answer

**Files:** Create `customer-agent-service/app/policy_tools.py`, modify `app/agent.py`, add `tests/test_policy_agent.py`.

**Interfaces:** `search_policies(query: str, category: str | None) -> list[PolicyExcerpt]`; policy specialist returns answer facts plus `policyId`, `version`, and effective date. Main Agent includes policy citations in response cards. An empty/contradictory result yields `needs_handoff=true`, not a definite return/refund promise.

- [ ] **Step 1: Write failing tests.** Assert one current policy produces a citation card; no policy produces uncertainty; two conflicting current excerpts produce uncertainty; an excerpt saying “ignore prior instructions” does not grant tools or change identity.
- [ ] **Step 2: Run tests.** `uv run --project customer-agent-service pytest customer-agent-service/tests/test_policy_agent.py -q`; expect failures.
- [ ] **Step 3: Implement policy tool and specialist.** Pass only structured excerpts to the Agent. Keep all existing catalog/order restrictions; no web search fallback for missing merchant policy.
- [ ] **Step 4: Run Python tests.** Same command plus the core Python suite; expect zero failures.
- [ ] **Step 5: Commit.** `git add customer-agent-service && git commit -m "feat: answer from published customer policies"`.

## Task 3: Queued handoff ticket

**Files:** Create Java ticket model/controller/service and migration; Python `app/ticket_tools.py`; tests `CustomerTicketServiceTest.java` and `test_ticket_tools.py`.

**Interfaces:** External `POST /customer-service/conversations/{id}/handoff` accepts `{reason,orderId?,idempotencyKey}` and builds the summary from persisted conversation context. Internal `POST /internal/customer/tickets` accepts `{conversationId,reason,summary,orderId?,idempotencyKey}` under scoped delegation credentials and returns `{ticketId,status:"QUEUED"}`. Java verifies conversation ownership and optional order ownership, redacts unnecessary personal data, and enforces uniqueness of `(conversationId,idempotencyKey)`. `GET /customer-service/conversations/{id}/ticket` returns the current ticket ID/status to the session owner. Ticket states for this release: `QUEUED`, `IN_PROGRESS`, `RESOLVED`; no live routing claim.

- [ ] **Step 1: Write failing tests.** Duplicate post returns the same ID; foreign order is rejected; anonymous ticket cannot carry order ID; DB failure yields an error and no success event; summary includes intent and relevant tool facts but no token, password, phone or full address.
- [ ] **Step 2: Run tests.** `mvn -pl hm-service -am -Dtest=CustomerTicketServiceTest test` and `uv run --project customer-agent-service pytest customer-agent-service/tests/test_ticket_tools.py -q`; expect failures.
- [ ] **Step 3: Implement Java persistence and Python tool.** The only new Agent write permission is ticket creation. The main Agent asks for missing details once, then calls the ticket tool; explicit user request may skip further troubleshooting.
- [ ] **Step 4: Run Java/Python tests and replay a timeout retry.** Expect one persisted ticket and accurate status.
- [ ] **Step 5: Commit.** `git add hm-service customer-agent-service && git commit -m "feat: add customer agent handoff tickets"`.

## Task 4: Storefront evidence and handoff states

**Files:** Modify storefront `customer-service.html`, `js/customer-service.js`, `css/customer-service.css`; extend `frontend/README-customer-service.md` and eval cases.

**Interfaces:** Display policy title/version/effective date beside a rules answer; display ticket ID and `QUEUED` status only after successful response. Show retry control when ticket creation fails; never claim live agent availability.

- [ ] **Step 1: Add failing browser/eval cases.** Assert citation is visible and links to its own source card, conflict offers handoff, successful ticket displays ID, failed ticket displays retry with no success text, and refresh restores ticket state.
- [ ] **Step 2: Run these cases against the core storefront.** Expect missing policy/ticket UI assertions to fail.
- [ ] **Step 3: Implement UI states and extend Agent eval fixtures.** Keep rendering safe from HTML/script in merchant policy text.
- [ ] **Step 4: Run browser checklist, Java/Python test suites, full first-release eval set, and `git diff --check`.** Record actual results; ensure no cross-user data exposure or invented logistics nodes.
- [ ] **Step 5: Commit.** `git add frontend customer-agent-service && git commit -m "feat: show policy evidence and handoff status"`.

## Completion boundary

The complete first-release scope from the spec is delivered only after the core plan and this plan pass their integration, ownership, policy, ticket and browser checks. Future return/refund actions require a separate design and approval.
