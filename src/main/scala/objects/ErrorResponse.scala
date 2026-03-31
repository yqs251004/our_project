package objects

import upickle.default.*

final case class ErrorResponse(
    message: String,
    code: String = "internal_error",
    details: Map[String, String] = Map.empty
)

object ErrorResponse:
  given ReadWriter[ErrorResponse] = macroRW
