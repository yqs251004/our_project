package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStatus
import riichinexus.system.json.JsonCodecs.given
import upickle.default.ReadWriter

/** TournamentListQuery 表示赛事列表查询 的列表或详情查询条件，包含状态、adminId、organizer、数量限制、分页偏移。 */

final case class TournamentListQuery(
    status: Option[TournamentStatus] = None,
    adminId: Option[PlayerId] = None,
    organizer: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives ReadWriter
