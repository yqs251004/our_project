package riichinexus.api.docs

import ujson.Obj

import OpenApiContractModel.*

object AuthOpenApiPathSpecs:
  def paths: Vector[PathSpec] = Vector(
    PathSpec(
      "/api/registerauthapi",
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
      "/api/loginauthapi",
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
      "/api/restoreauthsessionapi",
      "post",
      OperationSpec(
        summary = "Restore authenticated session",
        description = "Resolves the current credential-backed session from an Authorization bearer token.",
        tags = Vector("auth"),
        responseRef = Some("AuthSessionView")
      )
    ),
    PathSpec(
      "/api/logoutauthapi",
      "post",
      OperationSpec(
        summary = "Logout current session",
        description = "Invalidates the current credential-backed session token.",
        tags = Vector("auth")
      )
    ),
    PathSpec(
      "/api/currentsessionauthapi",
      "post",
      OperationSpec(
        summary = "Resolve current session",
        description = "Returns the current authenticated registered player, guest session, or anonymous session view.",
        tags = Vector("session"),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string", "nullable" -> true),
            "guestSessionId" -> Obj("type" -> "string", "nullable" -> true)
          )
        ),
        responseRef = Some("CurrentSessionView")
      )
    ),
    PathSpec(
      "/api/getcurrentplayerapi",
      "post",
      OperationSpec(
        summary = "Resolve current player",
        description = "Returns the canonical player profile for the current registered player.",
        tags = Vector("players"),
        requestBody = Some(
          objectSchema(
            "operatorId" -> Obj("type" -> "string")
          )
        ),
        responseRef = Some("PlayerProfileView")
      )
    )
  )
