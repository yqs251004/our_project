package riichinexus.microservices.tournament.domain.model

import java.time.Instant

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.{StageStatus, TournamentParticipantKind, TournamentStatus}

final case class Tournament(
    id: TournamentId,
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    participatingClubs: Vector[ClubId] = Vector.empty,
    participatingPlayers: Vector[PlayerId] = Vector.empty,
    admins: Vector[PlayerId] = Vector.empty,
    whitelist: Vector[TournamentWhitelistEntry] = Vector.empty,
    stages: Vector[TournamentStage] = Vector.empty,
    status: TournamentStatus = TournamentStatus.Draft,
    version: Int = 0
) derives CanEqual:
  require(startsAt.isBefore(endsAt), "Tournament start time must be earlier than end time")
  require(stages.map(_.id).distinct.size == stages.size, "Tournament stages must have unique ids")
  require(
    stages.map(_.order).distinct.size == stages.size,
    "Tournament stages must have unique ordering"
  )

  def registerClub(clubId: ClubId): Tournament =
    copy(
      participatingClubs = (participatingClubs :+ clubId).distinct,
      whitelist = (whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Club, clubId = Some(clubId))).distinct
    )

  def removeClub(clubId: ClubId): Tournament =
    copy(
      participatingClubs = participatingClubs.filterNot(_ == clubId),
      whitelist = whitelist.filterNot(_.clubId.contains(clubId))
    )

  def registerPlayer(playerId: PlayerId): Tournament =
    copy(
      participatingPlayers = (participatingPlayers :+ playerId).distinct,
      whitelist = (whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Player, playerId = Some(playerId))).distinct
    )

  def whitelistClub(clubId: ClubId): Tournament =
    copy(
      whitelist = (whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Club, clubId = Some(clubId))).distinct
    )

  def whitelistPlayer(playerId: PlayerId): Tournament =
    copy(
      whitelist = (whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Player, playerId = Some(playerId))).distinct
    )

  def assignAdmin(playerId: PlayerId): Tournament =
    copy(admins = (admins :+ playerId).distinct)

  def addStage(stage: TournamentStage): Tournament =
    val updatedStages = (stages.filterNot(_.id == stage.id) :+ stage).sortBy(_.order)
    require(
      updatedStages.map(_.order).distinct.size == updatedStages.size,
      "Tournament stages must have unique ordering"
    )
    copy(stages = updatedStages)

  def updateStage(
      stageId: TournamentStageId,
      update: TournamentStage => TournamentStage
  ): Tournament =
    val updatedStages = stages.map(stage => if stage.id == stageId then update(stage) else stage)
    require(
      updatedStages.map(_.order).distinct.size == updatedStages.size,
      "Tournament stages must have unique ordering"
    )
    copy(stages = updatedStages)

  def publish: Tournament =
    require(status == TournamentStatus.Draft, "Only draft tournaments can be published")
    copy(status = TournamentStatus.RegistrationOpen)

  def markScheduled: Tournament =
    require(
      status == TournamentStatus.RegistrationOpen || status == TournamentStatus.InProgress,
      "Only published or active tournaments can be marked as scheduled"
    )
    copy(status = TournamentStatus.Scheduled)

  def start: Tournament =
    require(
      status == TournamentStatus.RegistrationOpen || status == TournamentStatus.Scheduled,
      "Only published or scheduled tournaments can start"
    )
    copy(status = TournamentStatus.InProgress)

  def complete: Tournament =
    copy(
      status = TournamentStatus.Completed,
      stages = stages.map(_.complete)
    )

  def cancel: Tournament =
    require(status != TournamentStatus.Archived, "Archived tournaments cannot be cancelled")
    copy(status = TournamentStatus.Cancelled)

  def archive: Tournament =
    require(status == TournamentStatus.Completed, "Only completed tournaments can be archived")
    copy(status = TournamentStatus.Archived)

  def activateStage(stageId: TournamentStageId): Tournament =
    require(stages.exists(_.id == stageId), s"Stage ${stageId.value} was not found")
    copy(
      status = TournamentStatus.InProgress,
      stages = stages.map { stage =>
        if stage.id == stageId then stage.copy(status = StageStatus.Active)
        else if stage.status == StageStatus.Active then stage.copy(status = StageStatus.Ready)
        else stage
      }
    )
