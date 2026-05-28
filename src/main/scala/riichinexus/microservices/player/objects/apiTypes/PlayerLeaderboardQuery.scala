package riichinexus.microservices.player.objects.apiTypes

import riichinexus.domain.model.ClubId
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.player.objects.PlayerStatus
import upickle.default.*

final case class PlayerLeaderboardQuery(
    clubId: Option[ClubId] = None,
    status: Option[PlayerStatus] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) derives CanEqual

object PlayerLeaderboardQuery:
  given ReadWriter[PlayerLeaderboardQuery] = macroRW
