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
