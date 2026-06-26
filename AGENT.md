AWFL workflow-utils — Agent Notes

Scope
- This repository includes a DSL in us.awfl.dsl and utilities in us.awfl.utils.

JSON Schema Derivation (us.awfl.utils.JsonSchema)
- Minimal, dependency-light JSON Schema generator (draft 2020-12 style) using Scala 3 Mirrors.
- Supports primitives, Option, collections (List/Seq/Set/Vector/Array), Map[String, V].
- Case classes -> object with properties/required; sealed traits -> oneOf.
- AWFL DSL wrappers:
  - Value[T] and BaseValue[T] are unwrapped to T’s schema.
  - ListValue[T] -> array with items = schema(T).
  - OptBase[T] and OptList[T] are treated as optional when in a case class (not included in required[]).
- Optionality rules (inside products):
  - Option[T], OptBase[T], OptList[T] fields are NOT listed in required.
  - Property schema is unwrapped:
    - Option[T] -> schema(T)
    - OptBase[T] -> schema(T)
    - OptList[T] -> array of schema(T)
- Top-level handling:
  - Option[T] -> oneOf(schema(T), null)
  - OptBase[T] -> schema(T)
  - OptList[T] -> array of schema(T)

Files
- src/main/scala/us/awfl/utils/JsonSchema.scala — main implementation.
- src/main/scala/us/awfl/workflows/utils/JsonSchema.scala — placeholder only; slated for removal in a future cleanup.

Follow-ups (optional)
- Remove the placeholder file under us.awfl.workflows.utils.
- Add minimal unit tests/examples for representative types.
