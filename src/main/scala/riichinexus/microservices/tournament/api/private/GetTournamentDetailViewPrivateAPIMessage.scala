package riichinexus.microservices.tournament.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.api.`private`.ResolveClubsPrivateAPIMessage
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.api.`private`.ResolvePlayersPrivateAPIMessage
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.lineupmanagement.model.StageLineupSubmission
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.{Tournament, TournamentStage}
import riichinexus.microservices.tournament.objects.lineupmanagement.apiTypes.TournamentLineupSubmissionView
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentId
import riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes.*
import riichinexus.microservices.tournament.tables.tournaments.TournamentTable
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.system.json.JsonCodecs.given
import upickle.default.*

final case class GetTournamentDetailViewPrivateAPIMessage(
    tournamentId: TournamentId
) extends APIMessage[Option[TournamentDetailView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Option[TournamentDetailView]] =
    for
      tournament <- IO.blocking(TournamentTable.findById(context.connection, tournamentId))
      view <- tournament match
        case Some(value) => detailView(context, value).map(Some(_))
        case None        => IO.pure(None)
    yield view

  private def detailView(
      context: ApiPlanContext,
      tournament: Tournament
  ): IO[TournamentDetailView] =
    val tournamentClubIds = relatedClubIds(tournament)
    for
      clubs <- ResolveClubsPrivateAPIMessage(tournamentClubIds).plan(context)
      clubsById = clubs.map(club => club.id -> club).toMap
      participantIds = tournamentParticipantIds(tournament, clubsById)
      playerIdsForLookup = (
        tournament.participatingClubs.distinct.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members)) ++
          participantIds ++
          tournament.stages.flatMap(_.lineupSubmissions.map(_.submittedBy))
      ).distinct
      players <- ResolvePlayersPrivateAPIMessage(playerIdsForLookup.toVector.distinct).plan(context)
      playersById = players.map(player => player.id -> player).toMap
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
            clubIds = playerClubIds(player)
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
        stages = tournament.stages.sortBy(_.order).map(operationsStageView)
      )

  private def operationsStageView(stage: TournamentStage): TournamentOperationsStageView =
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
        .map(lineupSubmissionView)
    )

  private def lineupSubmissionView(submission: StageLineupSubmission): TournamentLineupSubmissionView =
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

  private def playerClubIds(player: Player): Vector[ClubId] =
    (player.clubId.toVector ++ player.affiliatedClubIds).distinct
