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

final case class ApproveClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    playerId: String,
    operatorId: String,
    note: Option[String] = None
) extends APIMessage[ClubView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubView] =
    for
      actor <- IO.blocking(context.principal(PlayerId(operatorId)))
      approvedAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = ApproveClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        playerId = PlayerId(playerId),
        actor = actor,
        note = note,
        approvedAt = approvedAt
      )
      club <- IO.blocking(
        approveApplication(context.connection, module, command)
          .getOrElse(throw NoSuchElementException("Resource not found"))
      )
    yield ClubView.fromDomain(club)

  private def approveApplication(
      connection: java.sql.Connection,
      module: riichinexus.bootstrap.ClubModuleContext,
      command: ApproveClubApplicationCommand
  ): Option[Club] =
    ClubApplicationReviewer.approve(
      connection = connection,
      module = module,
      parsedClubId = command.clubId,
      parsedMembershipId = command.membershipId,
      parsedPlayerId = command.playerId,
      actor = command.actor,
      note = command.note,
      approvedAt = command.approvedAt
    )

  private final case class ApproveClubApplicationCommand(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      playerId: PlayerId,
      actor: AccessPrincipal,
      note: Option[String],
      approvedAt: Instant
  )
