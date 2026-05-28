package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import java.time.Instant

import riichinexus.domain.model.TournamentId
import riichinexus.microservices.tournament.domain.model.{TournamentStatus as DomainTournamentStatus}
import riichinexus.microservices.tournament.objects.TournamentStatus

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
) derives CanEqual

object TournamentDetailView:
  given ReadWriter[TournamentDetailView] = macroRW

  def apply(
      tournamentId: TournamentId,
      name: String,
      organizer: String,
      status: DomainTournamentStatus,
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
      status = TournamentStatus.fromDomain(status),
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      participatingClubs = participatingClubs,
      participatingPlayers = participatingPlayers,
      whitelistSummary = whitelistSummary,
      stages = stages
    )
