package riichinexus.microservices.auth.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
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
    for
      input <- IO.blocking(resolveInput)
      operator <- IO.blocking(AuthAccessPrincipalResolver.principal(context, input.operatorId))
      allowed <- IO.blocking(checkPermission(context, CheckPermissionCommand(operator, input)))
    yield allowed

  private def resolveInput: ResolvedCheckPermissionInput =
    ResolvedCheckPermissionInput(
      operatorId = PlayerId(operatorId),
      permission = permission,
      clubId = parseOptionalId(clubId)(ClubId(_)),
      tournamentId = parseOptionalId(tournamentId)(TournamentId(_)),
      subjectPlayerId = parseOptionalId(subjectPlayerId)(PlayerId(_))
    )

  private def checkPermission(context: ApiPlanContext, command: CheckPermissionCommand): Boolean =
    Try {
      riichinexus.microservices.auth.domain.functions.AuthorizationPolicyFunctions.requirePermission(context.support.authorizationService, 
        principal = command.operator,
        permission = command.input.permission,
        clubId = command.input.clubId,
        tournamentId = command.input.tournamentId,
        subjectPlayerId = command.input.subjectPlayerId
      )
      true
    }.getOrElse(false)

  private def parseOptionalId[A](value: Option[String])(parse: String => A): Option[A] =
    value.filter(_.nonEmpty).map(parse)

  private final case class ResolvedCheckPermissionInput(
      operatorId: PlayerId,
      permission: Permission,
      clubId: Option[ClubId],
      tournamentId: Option[TournamentId],
      subjectPlayerId: Option[PlayerId]
  )

  private final case class CheckPermissionCommand(
      operator: AccessPrincipal,
      input: ResolvedCheckPermissionInput
  )
