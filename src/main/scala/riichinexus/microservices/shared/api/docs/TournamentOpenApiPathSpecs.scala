package riichinexus.microservices.shared.api.docs

import ujson.Obj

import OpenApiContractModel.*

private[docs] object TournamentOpenApiPathSpecs:
  def paths: Vector[PathSpec] = Vector(
    PathSpec(
      "/tournaments/{id}",
      "get",
      OperationSpec(
        summary = "Tournament operations detail",
        description = "Returns the dedicated tournament detail view used by the operations workbench.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id")
        ),
        responseRef = Some("TournamentDetailView")
      )
    ),
    PathSpec(
      "/tournaments/{id}/publish",
      "post",
      OperationSpec(
        summary = "Publish tournament",
        description = "Publishes a tournament and returns the updated tournament mutation view.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("TournamentMutationView")
      )
    ),
    PathSpec(
      "/tournaments/{id}/clubs/{clubId}",
      "post",
      OperationSpec(
        summary = "Register club in tournament",
        description = "Registers a club into a tournament and returns the updated tournament mutation view.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("clubId", "path", required = true, "Club id")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("TournamentMutationView")
      )
    ),
    PathSpec(
      "/tournaments/{id}/clubs/{clubId}/remove",
      "post",
      OperationSpec(
        summary = "Remove club from tournament",
        description = "Removes a club from tournament participation or invitation state.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("clubId", "path", required = true, "Club id")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("TournamentMutationView")
      )
    ),
    PathSpec(
      "/tournaments/{id}/stages",
      "get",
      OperationSpec(
        summary = "List tournament stages",
        description = "Returns the stable stage directory used by tournament operations pages.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id")
        ),
        responseRef = Some("TournamentStageDirectoryEntryList")
      )
    ),
    PathSpec(
      "/tournaments/{id}/stages/{stageId}/lineups",
      "post",
      OperationSpec(
        summary = "Submit stage lineup",
        description = "Submits or replaces a club lineup for a tournament stage and returns the updated tournament mutation view.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("stageId", "path", required = true, "Stage id")
        ),
        requestBody = Some(
          objectSchema(
            "clubId" -> Obj("type" -> "string"),
            "operatorId" -> Obj("type" -> "string"),
            "seats" -> Obj("type" -> "array", "items" -> Obj("type" -> "object")),
            "note" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("TournamentMutationView")
      )
    ),
    PathSpec(
      "/tournaments/{id}/stages/{stageId}/schedule",
      "post",
      OperationSpec(
        summary = "Schedule stage tables",
        description = "Schedules stage tables and returns the updated tournament mutation view together with scheduled tables.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("stageId", "path", required = true, "Stage id")
        ),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("TournamentMutationView")
      )
    ),
    PathSpec(
      "/tournaments/{id}/stages/{stageId}/standings",
      "get",
      OperationSpec(
        summary = "Stage standings",
        description = "Returns standings for a tournament stage.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("stageId", "path", required = true, "Stage id")
        ),
        responseRef = Some("StageRankingSnapshot")
      )
    ),
    PathSpec(
      "/tournaments/{id}/stages/{stageId}/bracket",
      "get",
      OperationSpec(
        summary = "Stage knockout bracket",
        description = "Returns the finals/knockout bracket snapshot.",
        tags = Vector("tournaments"),
        parameters = Vector(
          ParameterSpec("id", "path", required = true, "Tournament id"),
          ParameterSpec("stageId", "path", required = true, "Stage id")
        ),
        responseRef = Some("KnockoutBracketSnapshot")
      )
    ),
    PathSpec(
      "/appeals",
      "get",
      OperationSpec(
        summary = "List appeals",
        description = "Returns the appeal work queue with triage filters.",
        tags = Vector("appeals"),
        responseRef = Some("AppealTicketPage")
      )
    )
  )
