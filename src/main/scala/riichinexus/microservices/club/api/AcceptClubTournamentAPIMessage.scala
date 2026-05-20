package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.domain.service.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given
import upickle.default.*

final case class AcceptClubTournamentAPIMessage(
    clubId: String,
    tournamentId: String,
    operatorId: Option[String] = None
) extends APIMessage[TournamentMutationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMutationView] =
    IO {
      val tournamentModule = context.support.clubModule.tournamentModule
      val tournamentIdValue = TournamentId(tournamentId)
      val clubIdValue = ClubId(clubId)
      val actor = operatorId.filter(_.nonEmpty).map(id => context.support.principal(PlayerId(id)))
        .getOrElse(throw IllegalArgumentException("operatorId is required"))

      tournamentModule.transactionManager.inTransaction {
        val club = tournamentModule.clubRepository
          .findById(clubIdValue)
          .map { club =>
            ensureClubActive(club)
            club
          }
          .getOrElse(throw NoSuchElementException(s"Club ${clubIdValue.value} was not found"))
        requireClubLineupCapability(tournamentModule, actor, club)

        tournamentModule.tournamentRepository.findById(tournamentIdValue).foreach { tournament =>
          val alreadyParticipating = tournament.participatingClubs.contains(clubIdValue)
          val isWhitelisted = tournament.whitelist.exists(_.clubId.contains(clubIdValue))
          if !alreadyParticipating && !isWhitelisted then
            throw IllegalArgumentException(
              s"Club ${clubIdValue.value} is not invited to tournament ${tournamentIdValue.value}"
            )
          tournamentModule.tournamentRepository.save(tournament.registerClub(clubIdValue))
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
