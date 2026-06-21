package riichinexus.system.objects

/** API 失败时返回给客户端的标准错误结构。 */
final case class ErrorResponse(
    message: String,
    code: String = "internal_error",
    details: Map[String, String] = Map.empty
)

object ErrorResponse:
  export riichinexus.system.json.SharedResponseCodecs.given
