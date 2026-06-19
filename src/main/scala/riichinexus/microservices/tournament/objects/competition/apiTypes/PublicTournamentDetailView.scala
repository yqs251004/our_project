package riichinexus.microservices.tournament.objects.competition.apiTypes

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.tournament.objects.stage.apiTypes.PublicTournamentStageView
import riichinexus.system.json.TournamentJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** PublicTournamentDetailView 表示公开赛事详情视图 的前端展示视图，包含赛事 ID、名称、organizer、状态、startsAt、endsAt等。 */

final case class PublicTournamentDetailView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: String,
    endsAt: String,
    clubIds: Vector[String],
    playerIds: Vector[String],
    whitelistCount: Int,
    stages: Vector[PublicTournamentStageView]
)

object PublicTournamentDetailView:
  given ReadWriter[PublicTournamentDetailView] = macroRW

  def apply(
      tournamentId: TournamentId,
      name: String,
      organizer: String,
      status: TournamentStatus,
      startsAt: Instant,
      endsAt: Instant,
      clubIds: Vector[ClubId],
      playerIds: Vector[PlayerId],
      whitelistCount: Int,
      stages: Vector[PublicTournamentStageView]
  ): PublicTournamentDetailView =
    PublicTournamentDetailView(
      tournamentId = tournamentId.value,
      name = name,
      organizer = organizer,
      status = status,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      clubIds = clubIds.map(_.value),
      playerIds = playerIds.map(_.value),
      whitelistCount = whitelistCount,
      stages = stages
    )
