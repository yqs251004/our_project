package riichinexus.microservices.shared.api.docs

import ujson.{Obj, Value}

import OpenApiContractModel.*

private[docs] object TableOpenApiSchemas:
  def schemas: Map[String, Value] = Map(
    "Table" -> objectSchema(
      "id" -> Obj("type" -> "string"),
      "tableNo" -> Obj("type" -> "integer"),
      "status" -> Obj("type" -> "string"),
      "seats" -> Obj("type" -> "array", "items" -> Obj("type" -> "object"))
    )
  )
