# workflow-utils

Utilities and service clients for building AWFL workflows in Scala 3. Compact, dependency-light, and designed to be composed with the AWFL DSL.

![Scala 3.3.1](https://img.shields.io/badge/Scala-3.3.1-red?logo=scala)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
![Status: snapshot](https://img.shields.io/badge/status-snapshot-orange)


Contents

- Features
- Installation
- Requirements
- Quick start
- Package overview
- Building locally
- Contributing
- License


Features

- DSL-friendly service clients
  - us.awfl.services.Llm — chat, JSON-mode chat, and tool-enabled chat via the AWFL LLM service
  - us.awfl.services.Context — read topic context and ista documents
  - us.awfl.services.Firebase, GoogleStorage — primitives used by utilities (locks, storage)
- Workflow utilities
  - us.awfl.utils.Events — enqueue status and assistant messages via the awfl-relay ingest endpoint
  - us.awfl.utils.Locks — Firestore-backed distributed locks with TTL for workflow concurrency control
  - us.awfl.utils.YojComposer — compose TopicContextYoj component trees into ChatMessage lists
  - us.awfl.utils.Http, Exec, Env, Cache, Zip, Segments, Prakriya, Timestamped
  - us.awfl.utils.strider — helpers for backfills and stream processing
- Data models used by clients/utilities
  - us.awfl.ista.ChatMessage, ToolCall, and related helpers

Compatibility

- Scala: 3.3.1
- Organization: us.awfl
- Module: workflow-utils
- Depends on: us.awfl %% dsl % 0.1.0-SNAPSHOT


Installation

sbt

- Add the dependency:

```scala
libraryDependencies += "us.awfl" %% "workflow-utils" % "0.1.0-SNAPSHOT"
```

- Working with a local checkout: publish to your Ivy cache, then depend on it from the consuming project.

```bash
sbt +publishLocal
```

```scala
// in your consuming build
libraryDependencies += "us.awfl" %% "workflow-utils" % "0.1.0-SNAPSHOT"
```

Note: This module is currently published as a SNAPSHOT. APIs may change before the first stable release.


Requirements

This library issues HTTP calls to AWFL backend services (e.g., llm/chat, context/*, relay ingest). Ensure those endpoints are reachable from your environment. Without them, HTTP client steps will error at runtime.

Many helpers rely on Env (sessionId, projectId, userId, background). Configure these in your host application.


Quick start

All examples use the AWFL DSL. Import the DSL and givens first.

```scala
import us.awfl.dsl._
import us.awfl.dsl.CelOps._
import us.awfl.dsl.auto.given
import us.awfl.ista.ChatMessage
```

LLM chat (text)

```scala
import us.awfl.services.Llm

val msgs = buildList("msgs", List(ChatMessage("user", str("Hello!"))))
val chat = Llm.chat(
  name = "demo_chat",
  messages = msgs.resultValue
)
// chat is a Step (HTTP POST to llm/chat)
```

LLM chat to JSON

```scala
import us.awfl.services.Llm

case class Person(name: Field, age: Field)
// Provide/derive an implicit Spec[Person] as required by the DSL in your codebase
val msgs = buildList("msgs", List(ChatMessage("user", str("Return a JSON person"))))
val toJson = Llm.chatJson[Person](
  name = "person_json",
  messages = msgs.resultValue
)
```

Context reads (yoj/ista)

```scala
import us.awfl.services.Context
import us.awfl.utils.Env

val kala = Context.segKala(Env.sessionId, obj(1720000000.0), obj(3600.0))
val readNotes = Context.yojRead[ChatMessage](
  name = "read_notes",
  yojName = str("messages"),
  kala = kala
)
```

Distributed locks

```scala
import us.awfl.utils.Locks
import us.awfl.utils.{SegKala, KalaVibhaga}
import us.awfl.utils.Env

given KalaVibhaga = SegKala(Env.sessionId, obj(1720000000.0), obj(300.0))

val lockKey = Locks.key("reports")
val acquired = Locks.acquireBool(
  stepName = "reports_lock",
  key = lockKey,
  owner = str("worker-1"),
  ttlSeconds = 60
)
// acquired.resultValue is a BaseValue[Boolean]
```

Enqueue events to relay

```scala
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
```

Compose TopicContextYoj into chat messages

```scala
import us.awfl.utils.YojComposer
import us.awfl.utils.{SegKala, KalaVibhaga}
import us.awfl.utils.Env

given KalaVibhaga = SegKala(Env.sessionId, obj(1720000000.0), obj(3600.0))

val msgsFromContext = YojComposer.composed(
  name = "demo_composer",
  childComponents = Nil,
  intro = Some("Context messages below"),
  promoteUpstream = true
)
```


Package overview

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


Contributing

Issues and pull requests are welcome. If you plan a larger change, please open an issue first to discuss scope and approach.


License

MIT License — see LICENSE
