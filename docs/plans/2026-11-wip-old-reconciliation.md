# `workflow-utils` `wip-old` reconciliation (2026-11-19)

Source `wip-old` e73cafd (three commits past 28f30e47); target `main` a7116e5. **Selective redesign, not cherry-pick.** Depends on current DSL types, not on resurrecting the historical compiler's schema-file writer.

## Decisions by component

| Location | Decision | Intended result |
|---|---|---|
| `utils/JsonSchema.scala` (+ tests) | **Keep concept; adapt** | Derive *in-memory* JSON/tool schemas from Scala types and Kala-based `Ista[T]` descriptions; no generated `functions/workflows/types/*.json` requirement. |
| `utils/Prakriya.scala` `Ista[T]` + `Sadhaka`, `Yoj` | **Keep typed-tool idea, redesign runtime boundary** | Preserve `Ista` read/write and contextual tool descriptions; make typed invocation use native `execute` / callbacks and existing service APIs, not Google WorkflowExecutions calls. `Sadhaka` on old branch is a draft. |
| `services/Llm.scala` `ChatResponse[T]`, `ToolWithWorkflow` | **Selective compatibility adapter** | Do not wholesale replace existing `ChatToolResponse` or break native compiler/result decoder; introduce generic response only when needed, behind compatibility tests. Avoid multiple incompatible `ToolWithWorkflow` definitions. |
| `utils/TopicContextYoj.scala`, `utils/YojComposer.scala` explicit Kala | **Port only if necessary** | Keep model-aware budgeting, chronological summaries, stable prompt prefix and current TopicContext contract. |
| `strider/*`, `ista/*`, `Convo.scala` assorted changes | **Defer** | Existing current summarization, callback, and context logic is known to run; isolate specific proven fixes. |
| `Spec` compatibility / formatting and build version bumps | **Discard unrelated changes** | Preserve the toolchain and core type assumptions on main. |

## Typed schema contract before implementation

- Define one canonical schema representation and one deterministic converter to OpenAI-compatible tool parameters. Verify object `required`, `additionalProperties`, nullable and optional keys, array/map types, union variants, defaults, and descriptions; `oneOf` for sum types lacks a discriminator in `wip-old`.
- Use `JsonSchemaSpec` as seed only: expand with nested case classes, `Option`, `OptValue`/`OptBase`, `OptList`, `Value`, `ListValue`, maps, sealed ADTs, empty objects, escaping, and recursive types (must fail clearly or use bounded references).
- Ensure schemas used as tool definitions match exactly the dispatcher argument decoder; no `Ista[T]` implicit recursion when describing `T` by its own schema. Validate against a real OpenAI request with a throwaway no-side-effect tool once unit tests pass.
- Keep the tool's **workflow identity** server-side in the service-facing wrapper; never put `workflowName` into the model-visible function schema. Confirm dispatcher picks the intended workflow and preserves authentication/project scope.

## Native typed-completion design and tests

1. Provide a typed utility that constructs a tool schema from `Out`, asks the agent to call that function, validates returned arguments against `Out`, then converts to a typed value. Missing/empty/multiple tool calls must return explicit typed errors; do not index `.tool_calls(0)` without checking.
2. **No hardcoded $0.10 fund** (present in old `Sadhaka`); inherit caller's budget, model, session, credentials, and native execution context. Preserve usage accounting and callbacks. Honor read-only and `sideCall` behavior.
3. Test native `Callback`/`execute` in current Rust compiler and server, including asynchronous completion, failure, cancellation, budget exhaustion, duplicate callback delivery and effect-journal replay. No `googleapis.workflowexecutions...` connector.
4. Verify `SegKala`, `SessionKala`, `WeekKala`, `TermKala` encode/decode and lock scoping; add Scala compile tests for typed nested `OptBase` and ADT cases.
5. Compile dependent existing workflows before publishing a new library version. Keep explicit dependency version alignment and test cross-repo compatibility with current `dsl`.

## Exit criteria

Independent schema tests green; typed completion operates through native Rust with no new implicit runtime failure; existing workflows compile and Project Manager behavior, tool routing, cost, and cache metrics do not regress. Only then update consumers to opt into typed tools.
