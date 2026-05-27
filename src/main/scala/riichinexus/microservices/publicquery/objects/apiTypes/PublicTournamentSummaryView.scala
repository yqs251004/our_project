package riichinexus.microservices.publicquery.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.TournamentId
import riichinexus.microservices.tournament.domain.model.TournamentStatus
import upickle.default.*

final case class PublicTournamentSummaryView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: String,
    startsAt: String,
    endsAt: String,
    stageCount: Int,
    activeStageCount: Int,
    participantCount: Int,
    clubCount: Int,
    playerCount: Int
) derives CanEqual

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
      status = status.toString,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      stageCount = stageCount,
      activeStageCount = activeStageCount,
      participantCount = participantCount,
      clubCount = clubCount,
      playerCount = playerCount
    )
