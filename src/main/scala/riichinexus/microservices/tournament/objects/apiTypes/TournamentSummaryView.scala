package riichinexus.microservices.tournament.objects.apiTypes

import upickle.default.*

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId, TournamentId}
import riichinexus.microservices.tournament.domain.model.Tournament
import riichinexus.microservices.tournament.objects.TournamentStatus

final case class TournamentSummaryView(
    tournamentId: String,
    name: String,
    organizer: String,
    startsAt: String,
    endsAt: String,
    status: TournamentStatus,
    participatingClubIds: Vector[String],
    participatingPlayerIds: Vector[String],
    adminIds: Vector[String],
    whitelistCount: Int,
    stages: Vector[TournamentStageSummaryView]
) derives CanEqual

object TournamentSummaryView:
  given ReadWriter[TournamentSummaryView] = macroRW

  def apply(
      tournamentId: TournamentId,
      name: String,
      organizer: String,
      startsAt: Instant,
      endsAt: Instant,
      status: TournamentStatus,
      participatingClubIds: Vector[ClubId],
      participatingPlayerIds: Vector[PlayerId],
      adminIds: Vector[PlayerId],
      whitelistCount: Int,
      stages: Vector[TournamentStageSummaryView]
  ): TournamentSummaryView =
    TournamentSummaryView(
      tournamentId = tournamentId.value,
      name = name,
      organizer = organizer,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      status = status,
      participatingClubIds = participatingClubIds.map(_.value),
      participatingPlayerIds = participatingPlayerIds.map(_.value),
      adminIds = adminIds.map(_.value),
      whitelistCount = whitelistCount,
      stages = stages
    )

  def fromDomain(tournament: Tournament): TournamentSummaryView =
    TournamentSummaryView(
      tournamentId = tournament.id.value,
      name = tournament.name,
      organizer = tournament.organizer,
      startsAt = tournament.startsAt.toString,
      endsAt = tournament.endsAt.toString,
      status = tournament.status,
      participatingClubIds = tournament.participatingClubs.map(_.value),
      participatingPlayerIds = tournament.participatingPlayers.map(_.value),
      adminIds = tournament.admins.map(_.value),
      whitelistCount = tournament.whitelist.size,
      stages = tournament.stages.sortBy(_.order).map(TournamentStageSummaryView.fromDomain)
    )
