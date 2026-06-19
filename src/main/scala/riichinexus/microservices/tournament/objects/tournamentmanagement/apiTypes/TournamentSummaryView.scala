package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

import java.time.Instant

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentStatus

/** TournamentSummaryView 表示赛事摘要视图 的前端展示视图，包含赛事 ID、名称、organizer、startsAt、endsAt、状态等。 */

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
)

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
