package riichinexus.microservices.club.objects.profile.apiTypes

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 给俱乐部追加公开荣誉称号的管理请求。
  *
  * 称号会进入公开详情页，`achievedAt` 可回填实际达成时间，`operatorId` 与备注用于说明本次授予来源。
  */
final case class AwardClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None,
    achievedAt: Option[Instant] = None
)

object AwardClubHonorRequest:
  given ReadWriter[AwardClubHonorRequest] = macroRW
