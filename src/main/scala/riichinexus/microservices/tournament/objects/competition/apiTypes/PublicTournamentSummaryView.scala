package riichinexus.microservices.tournament.objects.competition.apiTypes

import java.time.Instant

import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.system.json.TournamentJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** PublicTournamentSummaryView 表示公开赛事摘要视图 的前端展示视图，包含赛事 ID、名称、organizer、状态、startsAt、endsAt等。 */

final case class PublicTournamentSummaryView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: TournamentStatus,
    startsAt: String,
    endsAt: String,
    stageCount: Int,
    activeStageCount: Int,
    participantCount: Int,
    clubCount: Int,
    playerCount: Int
)

object PublicTournamentSummaryView:
  given ReadWriter[PublicTournamentSummaryView] = macroRW

  def apply(
      tournamentId: TournamentId,
      name: String,
      organizer: String,
      status: TournamentStatus,
      startsAt: Instant,
      endsAt: Instant,
      stageCount: Int,
      activeStageCount: Int,
      participantCount: Int,
      clubCount: Int,
      playerCount: Int
  ): PublicTournamentSummaryView =
    PublicTournamentSummaryView(
      tournamentId = tournamentId.value,
      name = name,
      organizer = organizer,
      status = status,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      stageCount = stageCount,
      activeStageCount = activeStageCount,
      participantCount = participantCount,
      clubCount = clubCount,
      playerCount = playerCount
    )
