package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.club.api.`private`.ResolveClubReadModelsPrivateAPIMessage
import riichinexus.microservices.club.objects.`private`.ClubPrivateView
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.api.`private`.{ResolvePlayerBoundClubIdsPrivateAPIMessage, ResolvePlayersPrivateAPIMessage}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.tournament.objects.identity.TournamentId
import riichinexus.microservices.tournament.domain.stage.model.{StageLineupSubmission, TournamentStage}
import riichinexus.microservices.tournament.domain.competition.model.Tournament

import riichinexus.microservices.tournament.objects.stage.lineup.apiTypes.TournamentLineupSubmissionView
import riichinexus.microservices.tournament.objects.competition.apiTypes.{TournamentDetailView, TournamentParticipantClubView, TournamentParticipantPlayerView, TournamentWhitelistSummaryView}
import riichinexus.microservices.tournament.objects.stage.apiTypes.TournamentOperationsStageView

import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
/** 获取管理视角的赛事详情。 */
final case class TournamentGetAPIMessage(tournamentId: String) extends APIMessage[TournamentDetailView]:

  override def plan(context: ApiPlanContext): IO[TournamentDetailView] =
    for
      id <- IO.blocking(TournamentId(tournamentId))
      tournament <- loadTournament(context, id)
      view <- tournamentDetailView(context, tournament)
    yield view

  private def loadTournament(context: ApiPlanContext, tournamentId: TournamentId): IO[Tournament] =
    IO.blocking {
      TournamentTable
        .findById(context.connection, tournamentId)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }

  private def tournamentDetailView(
      context: ApiPlanContext,
      tournament: Tournament
  ): IO[TournamentDetailView] =
    val tournamentClubIds = relatedClubIds(tournament)
    for
      clubs <- ResolveClubReadModelsPrivateAPIMessage(tournamentClubIds).plan(context)
      clubsById = clubs.map(club => club.id -> club).toMap
      participantIds = tournamentParticipantIds(tournament, clubsById)
      playerIdsForLookup = (
        tournament.participatingClubs.distinct.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members)) ++
          participantIds ++
          tournament.stages.flatMap(_.lineupSubmissions.map(_.submittedBy))
      ).distinct
      players <- ResolvePlayersPrivateAPIMessage(playerIdsForLookup.toVector.distinct).plan(context)
      playersById = players.map(player => player.id -> player).toMap
      playerClubIdsById <- resolvePlayerClubIdsById(context, participantIds)
    yield
      val participatingClubs = tournament.participatingClubs.distinct.flatMap { clubId =>
        clubsById.get(clubId).map { club =>
          TournamentParticipantClubView(
            clubId = club.id,
            memberCount = club.members.size
          )
        }
      }.sortBy(_.clubId)

      val participatingPlayers = participantIds.flatMap { playerId =>
        playersById.get(playerId).map { player =>
          TournamentParticipantPlayerView(
            playerId = player.id,
            nickname = player.nickname,
            status = player.status,
            elo = player.elo,
            currentRank = player.currentRank,
            clubIds = playerClubIdsById.getOrElse(player.id, Vector.empty)
          )
        }
      }.sortBy(player => (player.nickname, player.playerId))

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
          clubIds = whitelistedClubIds.map(_.value),
          playerIds = whitelistedPlayerIds.map(_.value)
        ),
        stages = tournament.stages.sortBy(_.order).map(stage =>
          operationsStageView(stage, clubsById, playersById)
        )
      )

  private def resolvePlayerClubIdsById(
      context: ApiPlanContext,
      playerIds: Vector[PlayerId]
  ): IO[Map[PlayerId, Vector[ClubId]]] =
    playerIds.foldLeft(IO.pure(Map.empty[PlayerId, Vector[ClubId]])) { (previous, playerId) =>
      previous.flatMap(resolved =>
        ResolvePlayerBoundClubIdsPrivateAPIMessage(playerId).plan(context).map(clubIds => resolved + (playerId -> clubIds))
      )
    }

  private def operationsStageView(
      stage: TournamentStage,
      clubsById: Map[ClubId, ClubPrivateView],
      playersById: Map[PlayerId, PlayerPrivateView]
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
      advancementRule = stage.advancementRule,
      swissRule = stage.swissRule,
      knockoutRule = stage.knockoutRule,
      mahjongRuleset = stage.mahjongRuleset,
      lineupSubmissions = stage.lineupSubmissions
        .sortBy(_.submittedAt)
        .map(submission => lineupSubmissionView(submission))
    )

  private def lineupSubmissionView(
      submission: StageLineupSubmission
  ): TournamentLineupSubmissionView =
    TournamentLineupSubmissionView(
      submissionId = submission.id.value,
      clubId = submission.clubId.value,
      submittedBy = submission.submittedBy.value,
      submittedAt = submission.submittedAt.toString,
      activePlayerIds = submission.seats.filterNot(_.reserve).map(_.playerId.value),
      reservePlayerIds = submission.seats.filter(_.reserve).map(_.playerId.value),
      note = submission.note
    )

  private def tournamentParticipantIds(
      tournament: Tournament,
      clubsById: Map[ClubId, ClubPrivateView]
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
