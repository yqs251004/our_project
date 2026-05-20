package riichinexus.system.api.docs

import ujson.{Obj, Value}

import OpenApiContractModel.*

object AuthOpenApiSchemas:
  def schemas: Map[String, Value] = Map(
    "AuthSuccessView" -> objectSchema(
      "userId" -> Obj("type" -> "string"),
      "username" -> Obj("type" -> "string"),
      "displayName" -> Obj("type" -> "string"),
      "token" -> Obj("type" -> "string"),
      "roles" -> Obj("type" -> "object")
    ),
    "AuthSessionView" -> objectSchema(
      "userId" -> Obj("type" -> "string"),
      "username" -> Obj("type" -> "string"),
      "displayName" -> Obj("type" -> "string"),
      "authenticated" -> Obj("type" -> "boolean"),
      "roles" -> Obj("type" -> "object")
    ),
    "CurrentSessionView" -> objectSchema(
      "principalKind" -> Obj("type" -> "string"),
      "principalId" -> Obj("type" -> "string"),
      "displayName" -> Obj("type" -> "string"),
      "authenticated" -> Obj("type" -> "boolean"),
      "roles" -> Obj("type" -> "object"),
      "player" -> Obj("type" -> "object"),
      "guestSession" -> Obj("type" -> "object")
    )
  )
