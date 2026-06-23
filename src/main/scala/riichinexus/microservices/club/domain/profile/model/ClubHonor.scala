package riichinexus.microservices.club.domain.profile.model

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given

/** 俱乐部公开展示的一项荣誉。
  *
  * 荣誉记录标题、获得时间和可选说明，用于俱乐部主页、排行榜和运营后台呈现历史成绩。
  */
final case class ClubHonor(
    title: String,
    achievedAt: Instant,
    note: Option[String] = None
)
