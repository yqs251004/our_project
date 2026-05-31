package riichinexus.microservices.club.api

import java.time.Instant
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.model.*
import riichinexus.microservices.auth.domain.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubAuthorization
import riichinexus.microservices.club.objects.apiTypes.ClubMembershipApplicationResponse
import riichinexus.microservices.player.api.{CreatePlayerAPIMessage, GetPlayerAPIMessage, ListPlayersAPIMessage}
import upickle.default.*

final case class WithdrawClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    guestSessionId: Option[String] = None,
    operatorId: Option[String] = None,
    note: Option[String] = None
) extends APIMessage[ClubMembershipApplicationResponse] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationResponse] =
    for
      actor <- IO.blocking(resolveActor(context))
      withdrawnAt <- IO.realTimeInstant
      module = context.support.clubModule
      command = WithdrawClubApplicationCommand(
        clubId = ClubId(clubId),
        membershipId = MembershipApplicationId(membershipId),
        actor = actor,
        note = note,
        withdrawnAt = withdrawnAt
      )
      application <- IO.blocking {
        module.transactionManager.inTransaction {
          withdrawApplication(context.connection, module, command)
        }.getOrElse(throw NoSuchElementException("Resource not found"))
      }
    yield ClubMembershipApplicationResponse.fromDomain(application)

  private def resolveActor(context: ApiPlanContext): AccessPrincipal =
    context.requestActor(
      guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_)),
      operatorId.filter(_.nonEmpty).map(PlayerId(_))
    )

  private def withdrawApplication(
      connection: java.sql.Connection,
      module: ClubModuleContext,
      command: WithdrawClubApplicationCommand
  ): Option[ClubMembershipApplication] =
    module.authorizationService.requirePermission(command.actor, Permission.WithdrawClubApplication)
    riichinexus.microservices.club.tables.clubs.ClubTable.findById(connection, command.clubId).map { club =>
      ClubAuthorization.ensureClubActive(club)
      val application = resolveApplication(club, command)
      ensureApplicationPending(application, command.membershipId)
      requireApplicationOwnership(connection, application, command.actor)
      val updatedApplication = application.withdraw(command.actor.principalId, command.withdrawnAt, command.note)
      riichinexus.microservices.club.tables.clubs.ClubTable.save(connection, club.reviewApplication(command.membershipId, _ => updatedApplication))
      updatedApplication
    }

  private def resolveApplication(
      club: Club,
      command: WithdrawClubApplicationCommand
  ): ClubMembershipApplication =
    club
      .findApplication(command.membershipId)
      .getOrElse(
        throw NoSuchElementException(
          s"Membership application ${command.membershipId.value} was not found in club ${command.clubId.value}"
        )
      )

  private def ensureApplicationPending(
      application: ClubMembershipApplication,
      membershipId: MembershipApplicationId
  ): Unit =
    if !application.isPending then
      throw IllegalArgumentException(
        s"Membership application ${membershipId.value} has already been reviewed"
      )

  private def requireApplicationOwnership(
      connection: java.sql.Connection,
      application: ClubMembershipApplication,
      actor: AccessPrincipal
  ): Unit =
    val ownedByGuest =
      actor.isGuest && application.applicantUserId.contains(s"guest:${actor.principalId}")

    val ownedByRegisteredPlayer =
      actor.playerId.flatMap(playerId =>
        GetPlayerAPIMessage.findPlayer(connection, playerId)
      ).exists(player =>
        application.applicantUserId.contains(player.userId)
      )

    if !ownedByGuest && !ownedByRegisteredPlayer && !actor.isSuperAdmin then
      throw AuthorizationFailure(
        s"${actor.displayName} cannot withdraw membership application ${application.id.value}"
      )

  private final case class WithdrawClubApplicationCommand(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      actor: AccessPrincipal,
      note: Option[String],
      withdrawnAt: Instant
  )
