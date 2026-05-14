package riichinexus.microservices.shared.api.docs

import ujson.{Obj, Value}

private[docs] final case class ParameterSpec(
    name: String,
    in: String,
    required: Boolean,
    description: String,
    schemaType: String = "string"
)

private[docs] final case class OperationSpec(
    summary: String,
    description: String,
    tags: Vector[String],
    parameters: Vector[ParameterSpec] = Vector.empty,
    requestBody: Option[Value] = None,
    responseRef: Option[String] = None,
    responseDescription: String = "Successful response"
)

private[docs] final case class PathSpec(
    path: String,
    method: String,
    operation: OperationSpec
)

private[docs] object OpenApiContractModel:
  def parameterJson(parameter: ParameterSpec): Value =
    Obj(
      "name" -> parameter.name,
      "in" -> parameter.in,
      "required" -> parameter.required,
      "description" -> parameter.description,
      "schema" -> Obj("type" -> parameter.schemaType)
    )

  def requestBodyJson(body: Value): Value =
    Obj(
      "required" -> true,
      "content" -> Obj(
        "application/json" -> Obj(
          "schema" -> body
        )
      )
    )

  def objectSchema(properties: (String, Value)*): Value =
    Obj(
      "type" -> "object",
      "properties" -> Obj.from(properties),
      "additionalProperties" -> false
    )

  def pageSchema(itemRef: String): Value =
    objectSchema(
      "items" -> Obj("type" -> "array", "items" -> Obj("$ref" -> itemRef)),
      "total" -> Obj("type" -> "integer"),
      "limit" -> Obj("type" -> "integer"),
      "offset" -> Obj("type" -> "integer"),
      "hasMore" -> Obj("type" -> "boolean"),
      "appliedFilters" -> Obj("type" -> "object", "additionalProperties" -> Obj("type" -> "string"))
    )
