package riichinexus.microservices.tournament.api
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.auth.api.`private`.{RequirePermissionPrivateAPIMessage, ResolveAccessPrincipalPrivateAPIMessage}
import riichinexus.microservices.auth.api.`private`.ResolveSystemAccessPrincipalPrivateAPIMessage

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.system.api.{APIMessage, ApiPlanContext}
import riichinexus.microservices.player.objects.playerprofile.PlayerId
import riichinexus.microservices.tournament.objects.tablemanagement.TableId
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.auth.objects.`private`.AccessPrincipalPrivateView
import riichinexus.microservices.tournament.domain.paifu.functions.TournamentPaifuArchiveService

import riichinexus.microservices.tournament.objects.paifumanagement.apiTypes.UploadPaifuRequest
import riichinexus.microservices.tournament.objects.tablemanagement.apiTypes.TournamentTableView

import upickle.default.ReadWriter

/** 上传赛事牌桌牌谱并生成比赛记录。 */
final case class TournamentTableUploadPaifuAPIMessage(tableId: String, request: UploadPaifuRequest) extends APIMessage[TournamentTableView] derives ReadWriter:

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
    yield TournamentTableView.fromDomain(table)

  private def resolveActor(context: ApiPlanContext): IO[AccessPrincipalPrivateView] =
    request.operatorId.filter(_.nonEmpty).map(PlayerId(_)).map(ResolveAccessPrincipalPrivateAPIMessage(_).plan(context)).getOrElse(ResolveSystemAccessPrincipalPrivateAPIMessage().plan(context))
