package riichinexus.microservices.tournament.objects.competition.apiTypes

import upickle.default.{ReadWriter, macroRW}

import java.time.Instant

import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.microservices.tournament.objects.stage.apiTypes.TournamentOperationsStageView
import riichinexus.system.json.TournamentJsonCodecs.given

/** TournamentDetailView 表示赛事详情视图 的前端展示视图，包含赛事 ID、名称、organizer、状态、startsAt、endsAt等。 */

final case class TournamentDetailView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: String,
    endsAt: String,
    participatingClubs: Vector[TournamentParticipantClubView],
    participatingPlayers: Vector[TournamentParticipantPlayerView],
    whitelistSummary: TournamentWhitelistSummaryView,
    stages: Vector[TournamentOperationsStageView]
)

object TournamentDetailView:
  given ReadWriter[TournamentDetailView] = macroRW

  def apply(
      tournamentId: TournamentId,
      name: String,
      organizer: String,
      status: TournamentStatus,
      startsAt: Instant,
      endsAt: Instant,
      participatingClubs: Vector[TournamentParticipantClubView],
      participatingPlayers: Vector[TournamentParticipantPlayerView],
      whitelistSummary: TournamentWhitelistSummaryView,
      stages: Vector[TournamentOperationsStageView]
  ): TournamentDetailView =
    TournamentDetailView(
      tournamentId = tournamentId.value,
      name = name,
      organizer = organizer,
      status = status,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      participatingClubs = participatingClubs,
      participatingPlayers = participatingPlayers,
      whitelistSummary = whitelistSummary,
      stages = stages
    )
