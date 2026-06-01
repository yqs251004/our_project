package riichinexus.api.docs

import ujson.{Obj, Value}

import riichinexus.api.docs.{OperationSpec, ParameterSpec, PathSpec}
import riichinexus.api.docs.OpenApiContractModel.*

object AnalyticsOpenApiSchemas:
  def schemas: Map[String, Value] = Map(
    "AdvancedStatsBoard" -> objectSchema(
      "owner" -> Obj("type" -> "object"),
      "sampleSize" -> Obj("type" -> "integer"),
      "defenseStability" -> Obj("type" -> "number"),
      "ukeireExpectation" -> Obj("type" -> "number"),
      "lastUpdatedAt" -> Obj("type" -> "string", "format" -> "date-time")
    )
  )
