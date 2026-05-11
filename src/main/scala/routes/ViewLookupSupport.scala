package routes

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

trait ViewLookupSupport:
  protected def app: ApplicationContext

  protected def loadPlayersById(playerIds: Iterable[PlayerId]): Map[PlayerId, Player] =
    app.playerRepository.findByIds(playerIds.toVector.distinct)
      .map(player => player.id -> player)
      .toMap

  protected def loadClubsById(clubIds: Iterable[ClubId]): Map[ClubId, Club] =
    app.clubRepository.findByIds(clubIds.toVector.distinct)
      .map(club => club.id -> club)
      .toMap
