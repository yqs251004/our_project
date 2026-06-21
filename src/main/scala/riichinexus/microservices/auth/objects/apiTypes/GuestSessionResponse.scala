package riichinexus.microservices.auth.objects.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 创建或查询游客会话后返回给前端的基本信息。
  *
  * 前端用 `id` 恢复临时身份，用 `displayName` 和 `createdAt` 呈现游客状态，不包含撤销原因或升级目标等管理字段。
  */
final case class GuestSessionResponse(
    id: String,
    displayName: String,
    createdAt: String
)

object GuestSessionResponse:
  given ReadWriter[GuestSessionResponse] = macroRW
