package riichinexus.microservices.shared.api.docs

import ujson.Obj

import OpenApiContractModel.*

private[docs] object AuthOpenApiPathSpecs:
  def paths: Vector[PathSpec] = Vector(
    PathSpec(
      "/auth/register",
      "post",
      OperationSpec(
        summary = "Register account",
        description = "Registers a credential-backed account and creates or binds a player profile.",
        tags = Vector("auth"),
        requestBody = Some(
          objectSchema(
            "username" -> Obj("type" -> "string"),
            "password" -> Obj("type" -> "string"),
            "displayName" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("AuthSuccessView"),
        responseDescription = "Account registered"
      )
    ),
    PathSpec(
      "/auth/login",
      "post",
      OperationSpec(
        summary = "Login with account password",
        description = "Authenticates a credential-backed account and returns a bearer token.",
        tags = Vector("auth"),
        requestBody = Some(
          objectSchema(
            "username" -> Obj("type" -> "string"),
            "password" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("AuthSuccessView")
      )
    ),
    PathSpec(
      "/auth/session",
      "get",
      OperationSpec(
        summary = "Restore authenticated session",
        description = "Resolves the current credential-backed session from an Authorization bearer token.",
        tags = Vector("auth"),
        responseRef = Some("AuthSessionView")
      )
    ),
    PathSpec(
      "/auth/logout",
      "post",
      OperationSpec(
        summary = "Logout current session",
        description = "Invalidates the current credential-backed session token.",
        tags = Vector("auth")
      )
    ),
    PathSpec(
      "/session",
      "get",
      OperationSpec(
        summary = "Resolve current session",
        description = "Returns the current authenticated registered player, guest session, or anonymous session view.",
        tags = Vector("session"),
        parameters = Vector(
          ParameterSpec("operatorId", "query", required = false, "Registered player id"),
          ParameterSpec("guestSessionId", "query", required = false, "Guest session id")
        ),
        responseRef = Some("CurrentSessionView")
      )
    ),
    PathSpec(
      "/players/me",
      "get",
      OperationSpec(
        summary = "Resolve current player",
        description = "Returns the canonical Player aggregate for the current registered player.",
        tags = Vector("players"),
        parameters = Vector(
          ParameterSpec("operatorId", "query", required = true, "Current player id")
        ),
        responseRef = Some("Player")
      )
    )
  )
