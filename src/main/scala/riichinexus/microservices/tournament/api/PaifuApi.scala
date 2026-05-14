package riichinexus.microservices.tournament.api

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.PaifuListQuery
import riichinexus.microservices.tournament.tables.TournamentTables

object PaifuApi:

  def listPaifus(
      tables: TournamentTables,
      query: PaifuListQuery
  ): Vector[Paifu] =
    tables.listPaifus()
      .filter(paifu => query.playerId.forall(paifu.playerIds.contains))
      .filter(paifu => query.tournamentId.forall(_ == paifu.metadata.tournamentId))
      .filter(paifu => query.stageId.forall(_ == paifu.metadata.stageId))
      .filter(paifu => query.tableId.forall(_ == paifu.metadata.tableId))
      .sortBy(paifu => (paifu.metadata.recordedAt, paifu.id.value))

  def findPaifu(
      tables: TournamentTables,
      paifuId: PaifuId
  ): Option[Paifu] =
    tables.findPaifu(paifuId)
