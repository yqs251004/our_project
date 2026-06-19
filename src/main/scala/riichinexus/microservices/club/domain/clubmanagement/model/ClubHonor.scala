package riichinexus.microservices.club.domain.clubmanagement.model

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
/** ClubHonor 表示后端领域中的俱乐部荣誉状态或规则，包含标题、achievedAt、note。 */
final case class ClubHonor(
    title: String,
    achievedAt: Instant,
    note: Option[String] = None
)