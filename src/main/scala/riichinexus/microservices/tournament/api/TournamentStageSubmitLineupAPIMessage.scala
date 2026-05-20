package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import upickle.default.*

final case class TournamentStageSubmitLineupAPIMessage(tournamentId: String, stageId: String, request: SubmitStageLineupRequest) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    IO {
      val module = context.support.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val stageIdValue = TournamentStageId(stageId)
      val submission = request.toSubmission
      val actor = context.support.principal(request.operator)

      module.transactionManager.inTransaction {
        module.tournamentRepository.findById(tournamentIdValue).map { tournament =>
          val stage = tournament.stages
            .find(_.id == stageIdValue)
            .getOrElse(throw NoSuchElementException(s"Stage ${stageIdValue.value} was not found"))
          val submissionPlayerIds = submission.seats.map(_.playerId).distinct
          val conflictingPlayers = stage.lineupSubmissions
            .filterNot(_.clubId == submission.clubId)
            .flatMap(existing => existing.seats.map(_.playerId -> existing.clubId))
            .groupBy(_._1)
            .collect {
              case (playerId, assignments)
                  if submissionPlayerIds.contains(playerId) &&
                    assignments.map(_._2).distinct.nonEmpty =>
                playerId.value
            }
            .toVector

          if conflictingPlayers.nonEmpty then
            throw IllegalArgumentException(
              s"Stage ${stageIdValue.value} already has player(s) assigned by another club: ${conflictingPlayers.mkString(", ")}"
            )

          val club = module.clubRepository
            .findById(submission.clubId)
            .getOrElse(throw NoSuchElementException(s"Club ${submission.clubId.value} was not found"))
          ensureClubActive(club)
          requireClubLineupCapability(module, actor, club)

          if !actor.isSuperAdmin && actor.playerId.exists(_ != submission.submittedBy) then
            throw AuthorizationFailure("Lineup submitter must match the acting principal")

          val isClubRegistered =
            tournament.participatingClubs.contains(submission.clubId) ||
              tournament.whitelist.exists(_.clubId.contains(submission.clubId))

          if !isClubRegistered then
            throw IllegalArgumentException(
              s"Club ${submission.clubId.value} is not whitelisted for tournament ${tournamentIdValue.value}"
            )

          submission.seats.foreach { seat =>
            val playerId = seat.playerId
            if !club.members.contains(playerId) then
              throw IllegalArgumentException(
                s"Player ${playerId.value} is not a member of club ${submission.clubId.value}"
              )

            val player = module.playerRepository
              .findById(playerId)
              .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))
            if player.status != PlayerStatus.Active then
              throw IllegalArgumentException(s"Player ${playerId.value} cannot be submitted to tournament lineups")
          }

          module.tournamentRepository.save(
            tournament.updateStage(stageIdValue, _.submitLineup(submission))
          )
        }
      }

      buildTournamentMutationView(context, tournamentIdValue, Vector.empty)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }

  private def buildTournamentMutationView(
      context: ApiPlanContext,
      tournamentId: TournamentId,
      scheduledTables: Vector[Table]
  ): Option[TournamentMutationView] =
    buildTournamentDetailView(context, tournamentId).map(detail =>
      TournamentMutationView(
        tournament = detail,
        scheduledTables = scheduledTables.sortBy(table => (table.stageRoundNumber, table.tableNo, table.id.value)).map(TournamentTableView.fromDomain)
      )
    )

  private def buildTournamentDetailView(
      context: ApiPlanContext,
      tournamentId: TournamentId
  ): Option[TournamentDetailView] =
    context.support.tournamentModule.tables.findTournament(tournamentId).map(tournament =>
      buildTournamentDetailView(context, tournament)
    )

  private def buildTournamentDetailView(
      context: ApiPlanContext,
      tournament: Tournament
  ): TournamentDetailView =
    val module = context.support.tournamentModule
    val tournamentClubIds = relatedClubIds(tournament)
    val clubsById = module.tables.findClubs(tournamentClubIds)
      .map(club => club.id -> club)
      .toMap
    val participantIds = tournamentParticipantIds(tournament, clubsById)
    val playerIdsForLookup = (
      tournament.participatingClubs.distinct.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members)) ++
        participantIds ++
        tournament.stages.flatMap(_.lineupSubmissions.map(_.submittedBy))
    ).distinct
    val playersById = module.tables.findPlayers(playerIdsForLookup)
      .map(player => player.id -> player)
      .toMap

    val participatingClubs = tournament.participatingClubs.distinct.flatMap { clubId =>
      clubsById.get(clubId).map { club =>
        TournamentParticipantClubView(
          clubId = club.id,
          memberCount = club.members.size
        )
      }
    }.sortBy(_.clubId.value)

    val participatingPlayers = participantIds.flatMap { playerId =>
      playersById.get(playerId).map { player =>
        TournamentParticipantPlayerView(
          playerId = player.id,
          nickname = player.nickname,
          status = player.status,
          elo = player.elo,
          currentRank = player.currentRank,
          clubIds = player.boundClubIds
        )
      }
    }.sortBy(player => (player.nickname, player.playerId.value))

    val whitelistedClubIds = tournament.whitelist.flatMap(_.clubId).distinct.sortBy(_.value)
    val whitelistedPlayerIds = tournament.whitelist.flatMap(_.playerId).distinct.sortBy(_.value)

    TournamentDetailView(
      tournamentId = tournament.id,
      name = tournament.name,
      organizer = tournament.organizer,
      status = tournament.status,
      startsAt = tournament.startsAt,
      endsAt = tournament.endsAt,
      participatingClubs = participatingClubs,
      participatingPlayers = participatingPlayers,
      whitelistSummary = TournamentWhitelistSummaryView(
        totalEntries = tournament.whitelist.size,
        clubCount = whitelistedClubIds.size,
        playerCount = whitelistedPlayerIds.size,
        clubIds = whitelistedClubIds,
        playerIds = whitelistedPlayerIds
      ),
      stages = tournament.stages.sortBy(_.order).map(stage =>
        buildTournamentOperationsStageView(stage, clubsById, playersById)
      )
    )

  private def buildTournamentOperationsStageView(
      stage: TournamentStage,
      clubsById: Map[ClubId, Club],
      playersById: Map[PlayerId, Player]
  ): TournamentOperationsStageView =
    TournamentOperationsStageView(
      stageId = stage.id,
      name = stage.name,
      format = stage.format,
      order = stage.order,
      status = stage.status,
      currentRound = stage.currentRound,
      roundCount = stage.roundCount,
      schedulingPoolSize = stage.schedulingPoolSize,
      pendingTablePlanCount = stage.pendingTablePlans.size,
      scheduledTableCount = stage.scheduledTableIds.size,
      lineupSubmissions = stage.lineupSubmissions
        .sortBy(_.submittedAt)
        .map(submission => buildTournamentLineupSubmissionView(submission, clubsById, playersById))
    )

  private def buildTournamentLineupSubmissionView(
      submission: StageLineupSubmission,
      clubsById: Map[ClubId, Club],
      playersById: Map[PlayerId, Player]
  ): TournamentLineupSubmissionView =
    TournamentLineupSubmissionView(
      submissionId = submission.id,
      clubId = submission.clubId,
      submittedBy = submission.submittedBy,
      submittedAt = submission.submittedAt,
      activePlayerIds = submission.seats.filterNot(_.reserve).map(_.playerId),
      reservePlayerIds = submission.seats.filter(_.reserve).map(_.playerId),
      note = submission.note
    )

  private def tournamentParticipantIds(
      tournament: Tournament,
      clubsById: Map[ClubId, Club]
  ): Vector[PlayerId] =
    val clubMembers = tournament.participatingClubs.flatMap(clubId =>
      clubsById.get(clubId).toVector.flatMap(_.members)
    )
    val whitelistedClubMembers = tournament.whitelist.flatMap(entry =>
      entry.clubId.toVector.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members))
    )

    (tournament.participatingPlayers ++ tournament.whitelist.flatMap(_.playerId) ++ clubMembers ++ whitelistedClubMembers)
      .distinct

  private def relatedClubIds(tournament: Tournament): Vector[ClubId] =
    (tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct

  private def ensureClubActive(club: Club): Unit =
    if club.dissolvedAt.nonEmpty then
      throw IllegalArgumentException(s"Club ${club.id.value} has already been dissolved")

  private def requireClubLineupCapability(
      module: riichinexus.bootstrap.TournamentModuleContext,
      actor: AccessPrincipal,
      club: Club
  ): Unit =
    val hasBasePermission =
      module.authorizationService.can(
        actor,
        Permission.SubmitTournamentLineup,
        clubId = Some(club.id)
      )
    val hasDelegatedPrivilege = actor.playerId.exists { playerId =>
      club.members.contains(playerId) && club.hasPrivilege(playerId, ClubPrivilege.PriorityLineup)
    }

    if !hasBasePermission && !hasDelegatedPrivilege then
      throw AuthorizationFailure(
        s"${actor.displayName} is not allowed to perform ${Permission.SubmitTournamentLineup} for club ${club.id.value}"
      )
