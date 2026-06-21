package riichinexus.microservices.tournament.objects.competition.apiTypes

import java.time.Instant

import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.objects.competition.TournamentStatus
import riichinexus.system.json.TournamentJsonCodecs.given
import upickle.default.{ReadWriter, macroRW}

/** 公共大厅赛事列表中的公开摘要。
  *
  * 它只暴露赛事基本时间、状态、阶段数量和参赛规模，适合未进入运营后台的访客快速浏览赛事。
  */
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
