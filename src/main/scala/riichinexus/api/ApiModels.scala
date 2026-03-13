package riichinexus.api

import java.time.Instant

import riichinexus.domain.model.*
import upickle.default.*

final case class ApiError(
    message: String
)

final case class ApiMessage(
    message: String
)

final case class HealthResponse(
    status: String,
    storage: String,
    timestamp: Instant
)

final case class CreatePlayerRequest(
    userId: String,
    nickname: String,
    rankPlatform: String,
    tier: String,
    stars: Option[Int] = None,
    initialElo: Int = 1500
):
  def toRankSnapshot: RankSnapshot =
    RankSnapshot(RankPlatform.valueOf(rankPlatform), tier, stars)

final case class CreateClubRequest(
    name: String,
    creatorId: String
):
  def creator: PlayerId =
    PlayerId(creatorId)

final case class AddClubMemberRequest(
    playerId: String
):
  def player: PlayerId =
    PlayerId(playerId)

final case class CreateTournamentStageRequest(
    id: Option[String],
    name: String,
    format: String,
    order: Int,
    roundCount: Int
):
  def toStage: TournamentStage =
    TournamentStage(
      id = id.map(TournamentStageId(_)).getOrElse(IdGenerator.stageId()),
      name = name,
      format = StageFormat.valueOf(format),
      order = order,
      roundCount = roundCount
    )

final case class CreateTournamentRequest(
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    stages: Vector[CreateTournamentStageRequest]
):
  def toStages: Vector[TournamentStage] =
    stages.map(_.toStage)

object ApiModels:
  given ReadWriter[Instant] =
    readwriter[String].bimap[Instant](_.toString, Instant.parse)

  given ReadWriter[ApiError] = macroRW
  given ReadWriter[ApiMessage] = macroRW
  given ReadWriter[HealthResponse] = macroRW
  given ReadWriter[CreatePlayerRequest] = macroRW
  given ReadWriter[CreateClubRequest] = macroRW
  given ReadWriter[AddClubMemberRequest] = macroRW
  given ReadWriter[CreateTournamentStageRequest] = macroRW
  given ReadWriter[CreateTournamentRequest] = macroRW
