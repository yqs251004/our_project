package riichinexus.microservices.auth.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.auth.objects.{Permission as ApiPermission}
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

import scala.util.Try

final case class AuthCheckPermissionAPIMessage(
    operatorId: String,
    permission: ApiPermission,
    clubId: Option[String] = None,
    tournamentId: Option[String] = None,
    subjectPlayerId: Option[String] = None
) extends APIMessage[Boolean] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[Boolean] =
    for
      input <- IO(resolveInput)
      operator <- IO(context.principal(input.operatorId))
      allowed <- IO(checkPermission(context, CheckPermissionCommand(operator, input)))
    yield allowed

  private def resolveInput: ResolvedCheckPermissionInput =
    ResolvedCheckPermissionInput(
      operatorId = PlayerId(operatorId),
      permission = permission.toDomain,
      clubId = parseOptionalId(clubId)(ClubId(_)),
      tournamentId = parseOptionalId(tournamentId)(TournamentId(_)),
      subjectPlayerId = parseOptionalId(subjectPlayerId)(PlayerId(_))
    )

  private def checkPermission(context: ApiPlanContext, command: CheckPermissionCommand): Boolean =
    Try {
      context.support.requirePermission(
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
