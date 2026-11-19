# Phase 1.1 — in-memory typed JSON Schema

**Status 2026-11-19:** implementation and standalone test verified on branch `feat/reconcile-typed-json-schema` from `main` (`a7116e5`). No production behavior, schema-file generation, runtime routing, publishing or deployment changes.

## Historical review and selection

Historical `wip-old` (`e73cafd`) supplied an `Ista`/`Kala`-coupled `JsonSchema` implementation, a hand-written JSON renderer, `Llm.ToolDefProperty` converter and an unsupported-type fallback using runtime `sys.error`. Those concerns do not need to enter the first integration slice. The new `utils.JsonSchema` derives **in-memory** schema representations using Scala 3 `Mirror` and `compiletime`, supports primitives, case classes, sealed sums, collections/maps, `Option` and AWFL DSL `Value`/`BaseValue`/`ListValue`/`OptBase`/`OptList`. Unsupported types fail **at compile time**. No new dependencies or compiler-generated files.

The returned `Map[String, Any]` is intentionally an intermediate representation, not yet a serialized OpenAI tool or validated decoder. JSON-schema nodes include object `additionalProperties=false` for case classes. Optional fields are absent from `required` and accept null; optional DSL list fields use `oneOf[array,null]`. Maps retain typed `additionalProperties`.

## Verified tests

`/opt/sbt/bin/sbt -batch 'Test/runMain us.awfl.utils.JsonSchemaSpec'` **PASS** with Scala 3.3.1 / current DSL 0.3.5: primitive fields, `Value`, `ListValue`, optional wrappers, nested collections/maps, sealed `Result`, document metadata and compile-time rejection of `java.time.Instant`. No ScalaTest dependency; standalone test entrypoint fits current repo.

## Next slice / acceptance gates

1. Add contextual `Ista[T]` descriptions without making schema derivation require a `KalaVibhaga`. Avoid mixing metadata/routing fields into model-visible JSON.
2. Implement a typed `Llm.ToolDefProperty` conversion with the actual current server contract. Consider rendering JSON only at the appropriate API boundary.
3. Implement argument decoding / validation separately (e.g. reject missing and malformed typed results).
4. Publish a deliberately versioned library only after workflow integration tests and native Rust Project Manager fixtures remain green. No release or merging for this slice alone.

## Phase 1.2 verified — contextual metadata and model-facing tool adapter

On `feat/reconcile-typed-json-schema` (not merged/published), extended the existing `Ista[T]` value with an optional backward-compatible `description: KalaVibhaga => String` parameter. Existing two-argument `Ista(name, build)` callers compile without changes; new typed functions may supply a context-specific description (tested SegKala vs SessionKala). `JsonSchema.functionForIsta[T]` derives `ModelFunction` with exactly `type=function`, `function.name`, `function.description` and lossless `function.parameters`. `RoutedTool` holds the internal workflow name **outside** the model-facing JSON. No routes, model calls or agent workflow behavior were changed.

**Compatibility constraint:** current `Llm.ToolDefProperty` cannot express `oneOf`, nullability, typed `additionalProperties`, or `additionalProperties=false` on normal case-class objects. Historical `wip-old` silently downgraded these. `JsonSchema.legacyProperty` now explicitly throws for unsupported objects/unions/maps; it is usable only for faithfully representable primitive and primitive-array nodes. `JsonSchema.function[T]` is the canonical model-facing path. It preserves nullability and strict object/map constraints in memory, but has **not** yet been sent through the current Jobs LLM API, which may need a schema serialization/strict-mode contract update.

**Tests:** `sbt -batch 'Test/runMain us.awfl.utils.JsonSchemaSpec'` passed under Scala 3.3.1, including contextual Ista text, required/optional/union constraints, absence of routing metadata from model JSON, rejection of non-object tool inputs, and fail-closed legacy conversion. Unchanged current Project Manager baseline revalidated: 26 native Rust ELF workflows, 15/15 real PostgreSQL tests, 22 durable journal passes (1 optional skip), 3/3 strict background agent scenarios; **no new schema is wired into that baseline yet**.

Next: align server OpenAI request DTO to transport full tool schema; add deterministic JSON rendering and typed arguments decoder/validator with missing/extra/nullable fields; then test an actual tool/schema round-trip through native compiled workflow and real Jobs + provider contract. Do not merge the old `wip-old` Sadhaka or compiler schema writer.
