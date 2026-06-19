package riichinexus.microservices.tournament.objects.competition.apiTypes

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.stage.apiTypes.CreateTournamentStageRequest
import upickle.default.{ReadWriter, macroRW}

/** CreateTournamentRequest 表示创建赛事请求 的前端请求参数。 */

final case class CreateTournamentRequest(
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    stages: Vector[CreateTournamentStageRequest],
    adminId: Option[String] = None
)

object CreateTournamentRequest:
  given ReadWriter[CreateTournamentRequest] = macroRW
