package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubApplicationReviewer
import riichinexus.microservices.club.objects.ClubView
import upickle.default.*

final case class RejectClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(context.principal(PlayerId(operatorId)))
      rejectedAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = RejectClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        actor = actor,
        note = note,
        rejectedAt = rejectedAt
      )
      club <- IO.blocking(
        rejectApplication(context.connection, module, command)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
    yield ClubView.fromDomain(club)

  private def rejectApplication(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.ClubModuleContext,
      command: RejectClubApplicationCommand
  ): Option[Club] =
    ClubApplicationReviewer.reject(
      connection = connection,
      module = module,
      parsedClubId = command.clubId,
      parsedMembershipId = command.membershipId,
      actor = command.actor,
      note = command.note,
      rejectedAt = command.rejectedAt
    )

  private final case class RejectClubApplicationCommand(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      actor: AccessPrincipal,
      note: Option[String],
      rejectedAt: Instant
  )
