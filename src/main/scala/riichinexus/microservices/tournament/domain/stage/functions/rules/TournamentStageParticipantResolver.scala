package riichinexus.microservices.tournament.domain.stage.functions.rules

import cats.effect.IO
import riichinexus.microservices.club.api.`private`.{ListClubReadModelsPrivateAPIMessage, ResolveClubReadModelsPrivateAPIMessage}
import riichinexus.microservices.club.objects.`private`.ClubPrivateView
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind
import riichinexus.microservices.player.api.`private`.ResolvePlayerReadModelsPrivateAPIMessage
import riichinexus.microservices.player.objects.`private`.PlayerPrivateView
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.domain.stage.functions.lineup.StageLineupResolver
import riichinexus.microservices.tournament.domain.competition.model.Tournament
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage
import riichinexus.system.api.ApiPlanContext

/** TournamentStageParticipantResolver 负责赛事阶段参赛方解析器 相关的领域编排、构建或投影计算。 */

private[tournament] object TournamentStageParticipantResolver:
  def resolveParticipants(
      context: ApiPlanContext,
      tournament: Tournament,
      stage: TournamentStage
  ): IO[Vector[PlayerPrivateView]] =
    for
      clubsById <- resolveRelatedClubsById(context, tournament)
      fallbackPlayerIds = resolveFallbackPlayerIds(tournament, clubsById)
      playersById <- resolvePlayersById(context, (stage.lineupSubmissions.flatMap(_.seats.map(_.playerId)) ++ fallbackPlayerIds).distinct)
      stagePlayerIds = StageLineupResolver.resolveEligiblePlayers(stage, playersById.get)
      targetPlayerIds = StageLineupResolver.resolveTargetPlayerIds(tournament, stagePlayerIds, fallbackPlayerIds)
    yield targetPlayerIds.flatMap(playerId => playersById.get(playerId).filter(_.active))

  def resolveClubRelationIndex(context: ApiPlanContext): IO[Map[(ClubId, ClubId), ClubRelationKind]] =
    ListClubReadModelsPrivateAPIMessage(activeOnly = true)
      .plan(context)
      .map(buildClubRelationIndex)

  private def resolveRelatedClubsById(
      context: ApiPlanContext,
      tournament: Tournament
  ): IO[Map[ClubId, ClubPrivateView]] =
    ResolveClubReadModelsPrivateAPIMessage((tournament.participatingClubs ++ tournament.whitelist.flatMap(_.clubId)).distinct)
      .plan(context)
      .map(_.map(club => club.id -> club).toMap)

  private def resolvePlayersById(
      context: ApiPlanContext,
      playerIds: Vector[PlayerId]
  ): IO[Map[PlayerId, PlayerPrivateView]] =
    ResolvePlayerReadModelsPrivateAPIMessage(playerIds.distinct)
      .plan(context)
      .map(_.map(player => player.id -> player).toMap)

  private def resolveFallbackPlayerIds(
      tournament: Tournament,
      clubsById: Map[ClubId, ClubPrivateView]
  ): Vector[PlayerId] =
    val registeredClubMembers = tournament.participatingClubs.flatMap { clubId =>
      clubsById.get(clubId).toVector.flatMap(_.members)
    }
    val whitelistedPlayers = tournament.whitelist.flatMap(_.playerId)
    val whitelistedClubMembers = tournament.whitelist.flatMap { entry =>
      entry.clubId.toVector.flatMap(clubId => clubsById.get(clubId).toVector.flatMap(_.members))
    }

    (tournament.participatingPlayers ++ whitelistedPlayers ++ registeredClubMembers ++ whitelistedClubMembers).distinct

  private def buildClubRelationIndex(
      clubs: Vector[ClubPrivateView]
  ): Map[(ClubId, ClubId), ClubRelationKind] =
    clubs.flatMap { club =>
      club.relations.collect {
        case relation if relation.relation != ClubRelationKind.Neutral && relation.targetClubId != club.id =>
          val pair =
            if club.id.value <= relation.targetClubId.value then (club.id, relation.targetClubId)
            else (relation.targetClubId, club.id)
          pair -> relation.relation
      }
    }
      .groupBy(_._1)
      .view
      .mapValues(_.map(_._2).minBy {
        case ClubRelationKind.Alliance => 0
        case ClubRelationKind.Rivalry  => 1
        case ClubRelationKind.Neutral  => 2
      })
      .toMap
