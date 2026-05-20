package riichinexus.api.docs

import ujson.{Obj, Value}

import OpenApiContractModel.*

object SharedOpenApiSchemas:
  def schemas: Map[String, Value] = Map(
    "Player" -> objectSchema(
      "id" -> Obj("type" -> "string"),
      "userId" -> Obj("type" -> "string"),
      "nickname" -> Obj("type" -> "string"),
      "registeredAt" -> Obj("type" -> "string", "format" -> "date-time"),
      "elo" -> Obj("type" -> "integer"),
      "status" -> Obj("type" -> "string"),
      "boundClubIds" -> Obj("type" -> "array", "items" -> Obj("type" -> "string")),
      "roleGrants" -> Obj("type" -> "array", "items" -> Obj("type" -> "object"))
    )
  )
