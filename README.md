# 🤖 Kai - AI Task Manager (Spring AI Tool Calling Demo)

[![Java](https://img.shields.io/badge/Java-25-orange?logo=openjdk)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen?logo=spring)](https://spring.io/projects/spring-boot)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M6-blue)](https://spring.io/projects/spring-ai)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue?logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Kai** is a natural-language task assistant built on Spring Boot + Spring AI. Kai doesn't just talk - it **acts**, calling real Java methods against a live PostgreSQL database through Spring AI's tool calling (`@Tool`) support.

---

## 🎯 What This Demonstrates

Most "AI chatbot" demos stop at conversation. This project shows an LLM **taking real, tightly-scoped actions on persisted state**:

- *"Add a task to buy groceries"* → Kai calls `createTask()` → row inserted in Postgres
- *"What's still pending?"* → Kai calls `listPendingTasks()` → reads DB → replies naturally
- *"Mark the groceries task as done"* → Kai calls **only** `completeTask()` — nothing else fires, even though three other tools exist and could technically chain

That last point is the real engineering story of this project - see [Scoped Tool Calling](#-scoped-tool-calling--a-real-bug-i-hit) below.

The second core decision: **Kai is never given direct authority over destructive operations.** It can propose a deletion; only a human-confirmed API call executes it.

---

## 🧠 Core Concept: Tool Calling

An LLM can't touch a database or run code, it only generates text. Spring AI bridges that gap:

1. Java methods are annotated `@Tool` with a description of what they do
2. Spring AI auto-generates a schema from the method signature and hands it to the LLM
3. The LLM decides *if* and *which* tool to call based on user intent, and with what arguments
4. Spring AI intercepts that decision, **actually executes the Java method**, and feeds the result back
5. The LLM turns the raw result into a natural-language reply

```
User message → LLM (decides tool + args) → Spring AI executes Java method
             → result → LLM (formats reply) → User
```

This is **tool-using agentic behavior**. The LLM decides which action to take, not the developer. It is *not* a fully autonomous agent; see [Agent Scope](#-agent-scope--honest-framing).

---

## 🛠️ Tech Stack

| Component | Technology |
|---|---|
| Language | Java 25 |
| Framework | Spring Boot 4.1.0 |
| AI Framework | Spring AI 2.0.0-M6 |
| LLM Provider | Groq (OpenAI-compatible API, free tier) |
| Model | `llama-3.3-70b-versatile` |
| Persistence | Spring Data JPA + PostgreSQL 16 |
| Build Tool | Maven |
| Containerization | Docker + Docker Compose |

---

## 🏗️ Architecture

```
┌─────────────┐     ┌──────────────┐     ┌─────────────────────┐     ┌────────────┐
│   Client    │────▶│ ChatController│────▶│    ChatClient (Kai)   │────▶│  Groq LLM  │
│ (REST call) │     │ /api/v1/chat │     │  + ToolCallingAdvisor │     │            │
└─────────────┘     └──────────────┘     └──────────┬──────────┘     └────────────┘
                                                       │
                                        LLM decides: call ONE scoped tool?
                                                       │
                                                       ▼
                                          ┌─────────────────────┐
                                          │   TaskToolService     │
                                          │  @Tool createTask()   │
                                          │  @Tool listPending()  │
                                          │  @Tool completeTask() │
                                          │  @Tool searchByKeyword│
                                          │  @Tool proposeDelete()│
                                          └──────────┬──────────┘
                                                       │
                                                       ▼
                                          ┌─────────────────────┐
                                          │  TaskRepository (JPA) │
                                          └──────────┬──────────┘
                                                       │
                                                       ▼
                                          ┌─────────────────────┐
                                          │      PostgreSQL        │
                                          └─────────────────────┘

Destructive deletes bypass the LLM entirely:
Client ──▶ DELETE /api/v1/tasks/confirm-delete ──▶ TaskController ──▶ TaskRepository ──▶ Postgres
(no @Tool exposure — human must call this endpoint directly)
```

---

## 🎭 Meet Kai

The assistant has a name and a defined persona, loaded from an external prompt template (`src/main/resources/prompts/system.st`) rather than hardcoded inline, keeps behavior rules editable without touching Java code.

Kai's rules, enforced via the system prompt:
- Calls **exactly one** tool per user request - never chains unrelated actions
- Never exposes internal tool/function names in replies (speaks naturally, not like a system log)
- Can never delete tasks directly, under any circumstance

---

## 🔍 Scoped Tool Calling - a real bug I hit

Early versions of this project had a subtle but important failure mode: asking Kai to *"create a task"* would also result in it **completing that task and proposing its deletion**, all in the same turn, completely unprompted.

**Why it happened:** the system prompt described Kai as broadly "managing the task lifecycle." Given access to `createTask`, `listPendingTasks`, `completeTask`, and `proposeDeleteCompleted` in one request, the LLM interpreted a single creation request as license to proactively walk through the entire lifecycle.

**The fix - two layers:**

1. **Explicit scoping rules in the system prompt**, with worked examples mapping each request type to exactly one tool call, plus a hard instruction: *"Do not call more than one tool per user message, no matter what."*
2. **Tightened tool descriptions** - e.g. `completeTask`'s description now explicitly states it should only be called when the user *explicitly* asks to complete a task, closing the ambiguity at the schema level too, not just the prompt level.

This is a real, reproducible example of a failure mode specific to agentic/tool-calling systems: **over-eager tool chaining**. Constraining LLM autonomy to exactly what's requested is as much a design skill as wiring the tools themselves and it's the main thing this project is actually testing.

---

## 🔐 Design Decision: Human-in-the-Loop Safety

The LLM is deliberately **not** given a `@Tool`-annotated delete method. Instead:

1. `proposeDeleteCompleted()` is a tool - Kai can call it, and it returns a list of what *would* be deleted
2. The actual delete lives behind `DELETE /api/v1/tasks/confirm-delete`, a plain REST endpoint with **no LLM access**
3. A human (or a UI button wired to that endpoint) must explicitly trigger it

This mirrors production practice: LLMs are given the narrowest possible set of destructive capabilities, with irreversible actions gated behind explicit confirmation regardless of how confidently the model "decided" to act.

---

## 📡 API Endpoints

| Method | Path | Description                                                                       |
|---|---|-----------------------------------------------------------------------------------|
| `POST` | `/api/v1/chat` | Natural language interface - Kai interprets intent, calls at most one scoped tool |
| `DELETE` | `/api/v1/tasks/confirm-delete` | Human-confirmed deletion of all completed tasks - plain REST, no LLM involved     |

### Available tools (called internally by Kai, not directly exposed as HTTP endpoints)

| Tool | Triggered by |
|---|---|
| `createTask` | "add/create a task to..." |
| `listPendingTasks` | "what's pending / what do I have to do" |
| `completeTask` | "mark X as done / complete X" |
| `searchByKeyword` | "find/search tasks about X" |
| `proposeDeleteCompleted` | "delete/clean up completed tasks" |

### Example: chat-driven task creation

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "1", "message": "Create me a task to browse for 2027 Summer Internships"}'
```

```json
{
  "reply": "Task created: Browse for 2027 Summer Internships.",
  "sessionId": "1"
}
```

### Example: scoped completion (only completeTask fires — nothing else)

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "1", "message": "mark the internship task as done"}'
```

### Example: keyword search

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "1", "message": "find tasks about internship"}'
```

### Example: propose → confirm delete flow

```bash
curl -X POST http://localhost:8080/api/v1/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "1", "message": "delete completed tasks"}'
```
```json
{
  "reply": "Please confirm the deletion of the completed tasks via the /api/v1/tasks/confirm-delete endpoint.",
  "sessionId": "1"
}
```
```bash
curl -X DELETE http://localhost:8080/api/v1/tasks/confirm-delete
```

---

## 🐳 Running the Project

Two supported workflows, kept intentionally separate - don't run both at once (they conflict on port `8080`).

### Workflow A - Full container run (recommended, no local Java/Maven needed)

Everything, app and Postgres runs as Docker containers. The Maven build happens *inside* the container.

**1. Create `.env` at project root** (same folder as `docker-compose.full.yml`):
```env
GROQ_API_KEY=your_actual_groq_key_here
```
Never commit this.

**2. Run:**
```bash
docker compose -f docker-compose.full.yml up --build
```

App available at `http://localhost:8080`.

**Stop:**
```bash
docker compose -f docker-compose.full.yml down
```
**Stop and wipe DB data:**
```bash
docker compose -f docker-compose.full.yml down -v
```

### Workflow B - Local dev via IntelliJ (Postgres in Docker, app runs locally)

Requires local JDK 25. Postgres is auto-managed by Spring Boot's Docker Compose support via a lightweight `compose.yml` (Postgres only, no app service).

1. Open project in IntelliJ, confirm Project SDK = Java 25
2. Set `GROQ_API_KEY` as an environment variable in the Run Configuration
3. Hit Run ▶ - Boot auto-starts Postgres via `compose.yml`, waits for it healthy, then boots the app

**Why two separate compose files?** `compose.yml` is auto-detected and managed by Spring Boot itself on startup and must contain *only* Postgres. `docker-compose.full.yml` contains both `app` (with a `build:` step) and `postgres`, and is only ever run manually via `-f`. If Boot's auto-detection ever picked up the full file, it would try to build-and-run the app from inside its own startup process, a real gotcha hit during development, documented here deliberately since it's a common trap with this Spring Boot feature.

---

## 🧩 Spring AI 2.0.0-M6 Notes (Breaking Changes Handled Here)

- `ChatMemory.CONVERSATION_ID` passed via `.advisors(a -> a.param(...))`, not on the builder
- `ChatOptions` passed as an unbuilt builder to `.options()`, not a built instance
- `PromptChatMemoryAdvisor` removed - use `MessageChatMemoryAdvisor.builder(memory).build()`
- `ToolCallingAdvisor` auto-registered - tools exposed via `.defaultTools(toolService)`, no manual advisor wiring
- `.defaultSystem(Resource)` loads a prompt file's raw content and does **not** render `{placeholder}` syntax - the system prompt is loaded via `PromptTemplate` and rendered manually at bean-creation time to populate `{date}`
- `spring.ai.openai.chat.max-tokens` placeholders need a YAML default fallback (`${VAR:500}`) or the app fails to start if the env var is undefined

---

## 🤖 Agent Scope - Honest Framing

This project implements **tool-using agentic behavior**, not a fully autonomous agent. Important distinction for anyone evaluating this code:

| Has | Doesn't have |
|---|---|
| LLM decides which tool to call | Autonomous multi-turn planning loop |
| LLM calls exactly one scoped tool per request | Self-correction/retry on tool failure |
| Goal inferred from natural language | Goal-directed execution across many steps without user input |

Full autonomous agent patterns (ReAct-style think→act→observe loops, multi-step planning) are intentionally out of scope here.

---

## 📂 Project Structure

```
src/main/java/com/nagarjuna/toolcalling/
├── config/
│   └── ChatClientConfig.java       # ChatClient bean, tool + memory wiring, prompt rendering
├── controller/
│   ├── ChatController.java         # AI-driven path, /api/v1/chat
│   └── TaskController.java         # plain REST, no LLM, /api/v1/tasks
├── entity/
│   └── Task.java
├── repository/
│   └── TaskRepository.java
├── service/
│   ├── TaskToolService.java        # @Tool-annotated methods
│   └── ChatService.java
├── dto/
│   ├── ChatRequest.java            # record
│   └── ChatReply.java              # record
└── exception/
    └── GlobalExceptionHandler.java

src/main/resources/
├── application.yaml
├── compose.yml                     # Postgres only — auto-managed by Boot (Workflow B)
└── prompts/
    └── system.st                   # Kai's persona + scoping rules

docker-compose.full.yml             # app + Postgres — manual full-container run (Workflow A)
Dockerfile                          # multi-stage build
.env                                # GROQ_API_KEY — gitignored
```

---

## 📄 License

MIT