package riichinexus.microservices.publicquery.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.{ClubId, PlayerId, TournamentId}
import riichinexus.microservices.tournament.domain.model.TournamentStatus
import upickle.default.*

final case class PublicTournamentDetailView(
    tournamentId: String,
    name: String,
    organizer: String,
    status: String,
    startsAt: String,
    endsAt: String,
    clubIds: Vector[String],
    playerIds: Vector[String],
    whitelistCount: Int,
    stages: Vector[PublicTournamentStageView]
) derives CanEqual

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
      status = status.toString,
      startsAt = startsAt.toString,
      endsAt = endsAt.toString,
      clubIds = clubIds.map(_.value),
      playerIds = playerIds.map(_.value),
      whitelistCount = whitelistCount,
      stages = stages
    )
