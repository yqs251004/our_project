package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId, Tournament, TournamentId, TournamentStatus}

final case class TournamentSummaryView(
    tournamentId: String,
    name: String,
    organizer: String,
    startsAt: String,
    endsAt: String,
    status: String,
    participatingClubIds: Vector[String],
    participatingPlayerIds: Vector[String],
    adminIds: Vector[String],
    whitelistCount: Int,
    stages: Vector[TournamentStageSummaryView]
) derives CanEqual

object TournamentSummaryView:
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
      status = status.toString,
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
      status = tournament.status.toString,
      participatingClubIds = tournament.participatingClubs.map(_.value),
      participatingPlayerIds = tournament.participatingPlayers.map(_.value),
      adminIds = tournament.admins.map(_.value),
      whitelistCount = tournament.whitelist.size,
      stages = tournament.stages.sortBy(_.order).map(TournamentStageSummaryView.fromDomain)
    )
