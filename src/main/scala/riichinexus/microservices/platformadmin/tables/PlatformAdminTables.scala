package riichinexus.microservices.platformadmin.tables

import riichinexus.application.ports.{ClubRepository, PlayerRepository}
import riichinexus.domain.model.*

final class PlatformAdminTables(
    playerRepository: PlayerRepository,
    clubRepository: ClubRepository
):
  def findPlayer(playerId: PlayerId): Option[Player] =
    playerRepository.findById(playerId)

  def findClub(clubId: ClubId): Option[Club] =
    clubRepository.findById(clubId)

object PlatformAdminTables:
  val OwnedTables: Vector[String] = Vector.empty

  val ManagedAggregateStores: Vector[String] = Vector(
    "players",
    "clubs",
    "audit_events"
  )
