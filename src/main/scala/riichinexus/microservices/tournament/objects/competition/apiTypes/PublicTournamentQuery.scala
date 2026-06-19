package riichinexus.microservices.tournament.objects.competition.apiTypes

import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** PublicTournamentQuery 表示公开赛事查询 的列表或详情查询条件。 */

final case class PublicTournamentQuery(
    status: Option[TournamentStatus] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
)

object PublicTournamentQuery:
  given ReadWriter[PublicTournamentQuery] = macroRW
