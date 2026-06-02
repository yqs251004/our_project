package riichinexus.microservices.player.domain.functions

import java.sql.Connection
import java.time.Instant

import cats.effect.unsafe.implicits.global
import riichinexus.system.api.ApiPlanContext
import riichinexus.microservices.auth.domain.functions.RoleGrantFunctions
import riichinexus.microservices.player.domain.Player
import riichinexus.microservices.player.objects.RankSnapshot
import riichinexus.microservices.player.objects.apiTypes.PlayerListQuery
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.opsanalytics.api.`private`.EnsurePlayerDashboardAPIMessage
import riichinexus.microservices.player.tables.players.PlayerTable

object PlayerPersistenceFunctions:
  def createPlayer(
      connection: Connection,
      userId: String,
      nickname: String,
      rank: RankSnapshot,
      registeredAt: Instant,
      initialElo: Int
  ): Player =
    val player = PlayerTable.findByUserId(connection, userId) match
      case Some(existing) =>
        existing.copy(
          nickname = nickname,
          currentRank = rank
        )
      case None =>
        Player(
          id = PlayerIdGenerator.playerId(),
          userId = userId,
          nickname = nickname,
          registeredAt = registeredAt,
          currentRank = rank,
          elo = initialElo,
          roleGrants = Vector(RoleGrantFunctions.registered(registeredAt))
        )

    val savedPlayer = savePlayer(connection, player)
    EnsurePlayerDashboardAPIMessage(savedPlayer.id, registeredAt)
      .plan(apiContext(connection))
      .unsafeRunSync()
    savedPlayer

  def savePlayer(connection: Connection, player: Player): Player =
    PlayerTable.save(connection, player)

  def findPlayer(connection: Connection, playerId: PlayerId): Option[Player] =
    PlayerTable.findById(connection, playerId)

  def findPlayerByUserId(connection: Connection, userId: String): Option[Player] =
    PlayerTable.findByUserId(connection, userId)

  def listPlayers(connection: Connection, query: PlayerListQuery): Vector[Player] =
    PlayerTable.list(connection, query)

  def findPlayersByIds(connection: Connection, playerIds: Vector[PlayerId]): Vector[Player] =
    PlayerTable.findByIds(connection, playerIds)

  def findPlayersByClub(connection: Connection, clubId: ClubId): Vector[Player] =
    PlayerTable.findByClub(connection, clubId)

  def findAllPlayers(connection: Connection): Vector[Player] =
    PlayerTable.findAll(connection)

  private def apiContext(connection: Connection): ApiPlanContext =
    ApiPlanContext(bearerToken = None, connection = connection)
