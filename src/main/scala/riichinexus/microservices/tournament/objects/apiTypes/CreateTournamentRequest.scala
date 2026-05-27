package riichinexus.microservices.tournament.objects.apiTypes

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class CreateTournamentRequest(
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    stages: Vector[CreateTournamentStageRequest],
    adminId: Option[String] = None
):
  def toStages: Vector[TournamentStage] =
    stages.map(_.toStage)

  def admin: Option[PlayerId] =
    adminId.map(PlayerId(_))

object CreateTournamentRequest:
  given ReadWriter[CreateTournamentRequest] = macroRW

