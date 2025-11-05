# workflow-utils (Scala 3)

Lightweight utilities and service clients used to build AWFL workflows in Scala 3. This module was split out of the older monorepo and now provides only the shared helpers that other workflow modules import.

What this module includes

- DSL-friendly service clients
  - us.awfl.services.Llm: chat, JSON-mode chat, and tool-enabled chat against the AWFL LLM service
  - us.awfl.services.Context: read topic context and ista documents from the Context service
  - us.awfl.services.Firebase, GoogleStorage: primitives used by utilities (locks, storage)
- Workflow utilities
  - us.awfl.utils.Events: enqueue status and assistant messages via the awfl-relay ingest endpoint
  - us.awfl.utils.Locks: Firestore-backed distributed locks (with TTL) tailored for workflow concurrency control
  - us.awfl.utils.YojComposer: compose TopicContextYoj component trees into ChatMessage lists
  - us.awfl.utils.Http, Exec, Env, Cache, Zip, Segments, Prakriya, Timestamped
  - us.awfl.utils.strider: Strider helpers for backfills and stream processing
- Data models used by the utilities and services
  - us.awfl.ista.ChatMessage, ToolCall, and helpers used across clients and utilities

This module is published for Scala 3.3.1 and depends on the AWFL DSL library (us.awfl:dsl).

Install

sbt (recommended)

- Add the dependency to your project:

  libraryDependencies += "us.awfl" %% "workflow-utils" % "0.1.0-SNAPSHOT"

- If you are working locally with this repo, publish it to your Ivy cache and then depend on it:

  sbt +publishLocal

  // in your consuming project
  libraryDependencies += "us.awfl" %% "workflow-utils" % "0.1.0-SNAPSHOT"

Note: This library expects the AWFL backend services to be available (e.g., llm/chat, context/*, relay ingest). Without those endpoints, the HTTP clients will return errors at runtime.

Scala and dependencies

- Scala: 3.3.1
- Organization: us.awfl
- Module name: workflow-utils
- Depends on: "us.awfl" %% "dsl" % "0.1.0-SNAPSHOT"

Quick start examples

All examples use the AWFL DSL. Import the DSL and givens first:

- Imports

  import us.awfl.dsl._
  import us.awfl.dsl.CelOps._
  import us.awfl.dsl.auto.given

  // Common data types used below
  import us.awfl.ista.ChatMessage

- LLM chat (text)

  import us.awfl.services.Llm

  val msgs = buildList("msgs", List(ChatMessage("user", str("Hello!"))))
  val chat = Llm.chat(
    name = "demo_chat",
    messages = msgs.resultValue
  )
  // chat is a Step (HTTP POST to llm/chat). You can compose it in a Block with other steps.

- LLM chat to JSON

  import us.awfl.services.Llm

  case class Person(name: Field, age: Field)
  // Provide/derive an implicit Spec[Person] as required by the DSL in your codebase
  val msgs = buildList("msgs", List(ChatMessage("user", str("Return a JSON person"))))
  val toJson = Llm.chatJson[Person](
    name = "person_json",
    messages = msgs.resultValue
  )

- Context reads (yoj/ista)

  import us.awfl.services.Context
  import us.awfl.utils.Env

  // Choose a Kala for the read (segment-scoped example)
  val kala = Context.segKala(Env.sessionId, obj(1720000000.0), obj(3600.0))
  // Read a list of documents of your domain type T (requires a Spec[T])
  val readNotes = Context.yojRead[ChatMessage](
    name = "read_notes",
    yojName = str("messages"),
    kala = kala
  )

- Distributed locks

  import us.awfl.utils.Locks
  import us.awfl.utils.{SegKala, KalaVibhaga}

  given KalaVibhaga = SegKala(Env.sessionId, obj(1720000000.0), obj(300.0))

  val lockKey = Locks.key("reports")
  val acquired = Locks.acquireBool(
    stepName = "reports_lock",
    key = lockKey,
    owner = str("worker-1"),
    ttlSeconds = 60
  )
  // acquired.resultValue is a BaseValue[Boolean] telling you if the lock was obtained

- Enqueue events to relay

  import us.awfl.utils.Events
  import us.awfl.ista.{ToolCall, ToolCallFunction}

  val toolCall = obj(ToolCall(
    id = str("id-1"),
    `type` = "function",
    function = obj(ToolCallFunction(str("noop"), str("{}")))
  ))

  val enqueue = Events.enqueueResponse(
    opName = "demo",
    callback_url = str("https://example.com/callback"),
    content = str("ok"),
    toolCall = toolCall,
    cost = obj(0.0)
  )

- Compose TopicContextYoj into chat messages

  import us.awfl.utils.YojComposer
  import us.awfl.utils.{SegKala, KalaVibhaga}

  given KalaVibhaga = SegKala(Env.sessionId, obj(1720000000.0), obj(3600.0))

  // Build a single-root Yoj model with no child components (supply your children as needed)
  val msgsFromContext = YojComposer.composed(
    name = "demo_composer",
    childComponents = Nil,
    intro = Some("Context messages below"),
    promoteUpstream = true
  )

Package layout (this module only)

- src/main/scala/us/awfl/services
  - Context.scala — client for /context endpoints (yoj/ista read)
  - Llm.scala — client for /llm/chat (text, JSON, tool mode)
  - Firebase.scala, GoogleStorage.scala — internal helpers for locks and storage
- src/main/scala/us/awfl/utils
  - Events.scala — relay event enqueue helpers
  - Locks.scala — distributed lock helpers on Firestore
  - YojComposer.scala — compose TopicContextYoj trees
  - Env.scala, Http.scala, Exec.scala, Cache.scala, Zip.scala, Segments.scala, Prakriya.scala, Timestamped
  - strider/* — backfill/stream utils
- src/main/scala/us/awfl/ista
  - Convo.scala — ChatMessage, ToolCall, and related models

Building locally

- Clone this repo
- sbt compile
- sbt +publishLocal to use in other local projects

Notes

- Examples above show how to construct Steps; integrate them into your own Blocks and workflows per the AWFL DSL
- Many utilities rely on Env (sessionId, projectId, userId, background). Configure these in your host application as appropriate

License

MIT License — see LICENSE
