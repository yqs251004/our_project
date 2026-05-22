package riichinexus.microservices.auth.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

import scala.util.Try

final case class AuthCheckPermissionAPIMessage(
    operatorId: String,
    permission: Permission,
    clubId: Option[String] = None,
    tournamentId: Option[String] = None,
    subjectPlayerId: Option[String] = None
) extends APIMessage[Boolean] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Boolean] =
    IO {
      Try {
        val operator = context.support.principal(PlayerId(operatorId))
        context.support.requirePermission(
          principal = operator,
          permission = permission,
          clubId = clubId.filter(_.nonEmpty).map(ClubId(_)),
          tournamentId = tournamentId.filter(_.nonEmpty).map(TournamentId(_)),
          subjectPlayerId = subjectPlayerId.filter(_.nonEmpty).map(PlayerId(_))
        )
        true
      }.getOrElse(false)
    }
