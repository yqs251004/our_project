package riichinexus.system.objects

final case class ErrorResponse(
    message: String,
    code: String = "internal_error",
    details: Map[String, String] = Map.empty
)

object ErrorResponse:
  export SharedResponseCodecs.given
