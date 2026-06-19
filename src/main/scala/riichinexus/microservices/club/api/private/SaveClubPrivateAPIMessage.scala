package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.ReadWriter

/** 供后端服务持久化已完成校验的俱乐部 domain 对象。 */
final case class SaveClubPrivateAPIMessage(
    club: Club
) extends APIMessage[Club] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Club] =
    for
      saved <- IO.blocking(ClubTable.save(context.connection, club))
    yield saved
