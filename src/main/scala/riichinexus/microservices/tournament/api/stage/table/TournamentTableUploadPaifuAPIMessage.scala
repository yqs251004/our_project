package riichinexus.microservices.tournament.api.stage.table
import riichinexus.microservices.tournament.domain.competition.functions.TournamentViewFunctions
import riichinexus.microservices.auth.objects.authorization.Permission
import riichinexus.microservices.auth.api.authorization.`private`.RequirePermissionPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveAccessPrincipalPrivateAPIMessage
import riichinexus.microservices.auth.api.authorization.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.microservices.tournament.objects.stage.table.TableId
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.authorization.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.paifu.functions.TournamentPaifuArchiveService

import riichinexus.microservices.tournament.objects.paifu.apiTypes.UploadPaifuRequest
import riichinexus.microservices.tournament.objects.stage.table.TournamentTableView

/** 上传赛事牌桌牌谱并生成比赛记录。 */
final case class TournamentTableUploadPaifuAPIMessage(tableId: String, request: UploadPaifuRequest) extends APIMessage[TournamentTableView]:

  override def plan(context: ApiPlanContext): IO[TournamentTableView] =
    for
      actor <- resolveActor(context)
      _ <- RequirePermissionPrivateAPIMessage(
        actor,
        Permission.ManageTournamentStages,
        tournamentId = Some(request.paifu.metadata.tournamentId)
      ).plan(context)
      actorView = actor
      table <- IO.blocking {
        {
          TournamentPaifuArchiveService.archivePaifu(
            connection = context.connection,
            tableId = TableId(tableId),
            actor = actorView,
            paifu = request.paifu
          )
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield TournamentViewFunctions.tableView(table)

  private def resolveActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    request.operatorId.filter(_.nonEmpty).map(PlayerId(_)).map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context)).getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))
