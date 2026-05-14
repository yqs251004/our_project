package riichinexus.microservices.shared.api.docs

import ujson.Obj

import OpenApiContractModel.*

private[docs] object TableOpenApiPathSpecs:
  def paths: Vector[PathSpec] = Vector(
    PathSpec(
      "/tables/{tableId}/seats/{seat}/state",
      "post",
      OperationSpec(
        summary = "Update seat readiness or disconnect state",
        description = "Updates a table seat's ready/disconnected state.",
        tags = Vector("tables"),
        parameters = Vector(
          ParameterSpec("tableId", "path", required = true, "Table id"),
          ParameterSpec("seat", "path", required = true, "Seat wind")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string"),
            "ready" -> Obj("type" -> "boolean"),
            "disconnected" -> Obj("type" -> "boolean"),
            "note" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("Table")
      )
    ),
    PathSpec(
      "/tables/{tableId}/ready",
      "post",
      OperationSpec(
        summary = "Update the requesting player's ready state",
        description = "Marks the requesting player's own seat ready or not ready before the table starts.",
        tags = Vector("tables"),
        parameters = Vector(
          ParameterSpec("tableId", "path", required = true, "Table id")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string"),
            "ready" -> Obj("type" -> "boolean"),
            "note" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("Table")
      )
    )
  )
