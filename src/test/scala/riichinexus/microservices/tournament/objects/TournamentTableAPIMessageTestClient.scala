package riichinexus.microservices.tournament.objects

import java.time.Instant

import cats.effect.unsafe.implicits.global
import riichinexus.api.ApiPlanContext
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.microservices.tournament.api.*
import riichinexus.microservices.tournament.objects.apiTypes.*

final class TournamentTableAPIMessageTestClient(
    app: ApplicationContext,
    apiContext: ApiPlanContext
):
  def updateSeatState(
      tableId: TableId,
      seat: SeatWind,
      actor: AccessPrincipal,
      ready: Option[Boolean] = None,
      disconnected: Option[Boolean] = None,
      note: Option[String] = None
  ): Option[Table] =
    val response = TournamentTableUpdateSeatStateAPIMessage(
      tableId.value,
      seat.toString,
      UpdateTableSeatStateRequest(
        operatorId = requiredOperator(actor).value,
        ready = ready,
        disconnected = disconnected,
        note = note
      )
    ).plan(apiContext).unsafeRunSync()
    app.tournamentModule.tableRepository.findById(response.tableId)

  def startTable(
      tableId: TableId,
      startedAt: Instant = Instant.now(),
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Table] =
    val response = TournamentTableStartAPIMessage(tableId.value, actor.playerId.map(_.value))
      .plan(apiContext)
      .unsafeRunSync()
    app.tournamentModule.tableRepository.findById(response.tableId)

  def recordCompletedTable(
      tableId: TableId,
      paifu: Paifu,
      actor: AccessPrincipal = AccessPrincipal.system
  ): Option[Table] =
    val response = TournamentTableUploadPaifuAPIMessage(
      tableId.value,
      UploadPaifuRequest(
        operatorId = actor.playerId.map(_.value),
        paifu = paifu
      )
    ).plan(apiContext).unsafeRunSync()
    app.tournamentModule.tableRepository.findById(response.tableId)

  private def requiredOperator(actor: AccessPrincipal): PlayerId =
    actor.playerId.getOrElse(throw IllegalArgumentException("Test API client requires a player principal"))
