package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.tournament.domain.UploadPaifuCommand
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.AssignTournamentAdminRequest.given
import upickle.default.*

final case class TournamentTableUploadPaifuAPIMessage(tableId: String, request: UploadPaifuRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- IO(resolveActor(context))
      module = context.support.tournamentModule
      command = UploadPaifuCommand(
        tableId = TableId(tableId),
        actor = actor,
        paifu = request.paifu
      )
      archivedTable <- IO {
        module.transactionManager.inTransaction {
          module.paifuArchiveService.archivePaifu(context.connection, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentTableView.fromDomain(archivedTable)

  private def resolveActor(context: ApiPlanContext): AccessPrincipal =
    request.operator.map(context.principal).getOrElse(AccessPrincipal.system)
