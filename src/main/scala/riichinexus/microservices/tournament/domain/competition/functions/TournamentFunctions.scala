package riichinexus.microservices.tournament.domain.competition.functions

import riichinexus.microservices.tournament.domain.stage.model.TournamentStage
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.domain.stage.functions.TournamentStageFunctions

import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentStageId
import riichinexus.microservices.tournament.objects.competition.TournamentWhitelistEntry
import riichinexus.microservices.tournament.objects.stage.StageStatus
import riichinexus.microservices.tournament.objects.competition.{TournamentParticipantKind, TournamentStatus}

/** TournamentFunctions 提供赛事相关的领域计算、校验和转换函数。 */

private[tournament] object TournamentFunctions:
  def validate(tournament: Tournament): Unit =
    require(tournament.startsAt.isBefore(tournament.endsAt), "Tournament start time must be earlier than end time")
    require(tournament.stages.map(_.id).distinct.size == tournament.stages.size, "Tournament stages must have unique ids")
    require(
      tournament.stages.map(_.order).distinct.size == tournament.stages.size,
      "Tournament stages must have unique ordering"
    )

  def registerClub(tournament: Tournament, clubId: ClubId): Tournament =
    tournament.copy(
      participatingClubs = (tournament.participatingClubs :+ clubId).distinct,
      whitelist =
        (tournament.whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Club, clubId = Some(clubId))).distinct
    )

  def removeClub(tournament: Tournament, clubId: ClubId): Tournament =
    tournament.copy(
      participatingClubs = tournament.participatingClubs.filterNot(_ == clubId),
      whitelist = tournament.whitelist.filterNot(_.clubId.contains(clubId))
    )

  def registerPlayer(tournament: Tournament, playerId: PlayerId): Tournament =
    tournament.copy(
      participatingPlayers = (tournament.participatingPlayers :+ playerId).distinct,
      whitelist =
        (tournament.whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Player, playerId = Some(playerId))).distinct
    )

  def whitelistClub(tournament: Tournament, clubId: ClubId): Tournament =
    tournament.copy(
      whitelist =
        (tournament.whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Club, clubId = Some(clubId))).distinct
    )

  def whitelistPlayer(tournament: Tournament, playerId: PlayerId): Tournament =
    tournament.copy(
      whitelist =
        (tournament.whitelist :+ TournamentWhitelistEntry(TournamentParticipantKind.Player, playerId = Some(playerId))).distinct
    )

  def assignAdmin(tournament: Tournament, playerId: PlayerId): Tournament =
    tournament.copy(admins = (tournament.admins :+ playerId).distinct)

  def addStage(tournament: Tournament, stage: TournamentStage): Tournament =
    val updatedStages = (tournament.stages.filterNot(_.id == stage.id) :+ stage).sortBy(_.order)
    require(
      updatedStages.map(_.order).distinct.size == updatedStages.size,
      "Tournament stages must have unique ordering"
    )
    tournament.copy(stages = updatedStages)

  def updateStage(
      tournament: Tournament,
      stageId: TournamentStageId,
      update: TournamentStage => TournamentStage
  ): Tournament =
    val updatedStages = tournament.stages.map(stage => if stage.id == stageId then update(stage) else stage)
    require(
      updatedStages.map(_.order).distinct.size == updatedStages.size,
      "Tournament stages must have unique ordering"
    )
    tournament.copy(stages = updatedStages)

  def publish(tournament: Tournament): Tournament =
    require(tournament.status == TournamentStatus.Draft, "Only draft tournaments can be published")
    tournament.copy(status = TournamentStatus.RegistrationOpen)

  def markScheduled(tournament: Tournament): Tournament =
    require(
      tournament.status == TournamentStatus.RegistrationOpen || tournament.status == TournamentStatus.InProgress,
      "Only published or active tournaments can be marked as scheduled"
    )
    tournament.copy(status = TournamentStatus.Scheduled)

  def start(tournament: Tournament): Tournament =
    require(
      tournament.status == TournamentStatus.RegistrationOpen || tournament.status == TournamentStatus.Scheduled,
      "Only published or scheduled tournaments can start"
    )
    tournament.copy(status = TournamentStatus.InProgress)

  def complete(tournament: Tournament): Tournament =
    tournament.copy(
      status = TournamentStatus.Completed,
      stages = tournament.stages.map(TournamentStageFunctions.complete)
    )

  def cancel(tournament: Tournament): Tournament =
    require(tournament.status != TournamentStatus.Archived, "Archived tournaments cannot be cancelled")
    tournament.copy(status = TournamentStatus.Cancelled)

  def archive(tournament: Tournament): Tournament =
    require(tournament.status == TournamentStatus.Completed, "Only completed tournaments can be archived")
    tournament.copy(status = TournamentStatus.Archived)

  def activateStage(tournament: Tournament, stageId: TournamentStageId): Tournament =
    require(tournament.stages.exists(_.id == stageId), s"Stage ${stageId.value} was not found")
    tournament.copy(
      status = TournamentStatus.InProgress,
      stages = tournament.stages.map { stage =>
        if stage.id == stageId then stage.copy(status = StageStatus.Active)
        else if stage.status == StageStatus.Active then stage.copy(status = StageStatus.Ready)
        else stage
      }
    )
