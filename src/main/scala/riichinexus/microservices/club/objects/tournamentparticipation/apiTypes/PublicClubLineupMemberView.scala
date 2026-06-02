package riichinexus.microservices.club.objects.tournamentparticipation.apiTypes

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.club.objects.rankprivilegemanagement.ClubPrivilegeCode
import riichinexus.microservices.player.objects.RankSnapshot
import upickle.default.*

final case class PublicClubLineupMemberView(
    playerId: String,
    nickname: String,
    elo: Int,
    currentRank: RankSnapshot,
    status: String,
    isAdmin: Boolean,
    internalTitle: Option[String],
    privileges: Vector[ClubPrivilegeCode]
)

object PublicClubLineupMemberView:
  given ReadWriter[PublicClubLineupMemberView] = macroRW
