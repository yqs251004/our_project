package riichinexus.microservices.club.objects.clubmanagement.apiTypes

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** AwardClubHonorRequest 表示Award俱乐部荣誉请求 的前端请求参数。 */

final case class AwardClubHonorRequest(
    operatorId: String,
    title: String,
    note: Option[String] = None,
    achievedAt: Option[Instant] = None
)

object AwardClubHonorRequest:
  given ReadWriter[AwardClubHonorRequest] = macroRW
