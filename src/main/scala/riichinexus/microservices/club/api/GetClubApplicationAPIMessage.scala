package riichinexus.microservices.club.api
import riichinexus.microservices.auth.api.`private`.AuthAccessPrincipalResolver

import riichinexus.microservices.club.domain.clubmanagement.functions.ClubFunctions
import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.microservices.auth.domain.model.*
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.domain.clubmanagement.model.*
import riichinexus.microservices.club.domain.membershipmanagement.model.*
import riichinexus.microservices.club.domain.rankprivilegemanagement.model.*
import riichinexus.microservices.club.domain.relationmanagement.model.*
import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.api.`private`.ClubApplicationViewAssembler
import riichinexus.microservices.club.objects.membershipmanagement.apiTypes.ClubMembershipApplicationView
import riichinexus.microservices.club.tables.clubs.ClubTable
import upickle.default.*

final case class GetClubApplicationAPIMessage(
    clubId: String,
    membershipId: String,
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      input <- IO.blocking(resolveInput)
      actor <- IO.blocking(resolveActor(context, input))
      view <- IO.blocking(getApplicationView(context, input, actor))
    yield view

  private def resolveInput: GetClubApplicationInput =
    GetClubApplicationInput(
      clubId = ClubId(clubId),
      membershipId = MembershipApplicationId(membershipId),
      operatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_)),
      guestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
    )

  private def resolveActor(
      context: ApiPlanContext,
      input: GetClubApplicationInput
  ): AccessPrincipal =
    AuthAccessPrincipalResolver.requestActor(context, input.guestSessionId, input.operatorId)

  private def getApplicationView(
      context: ApiPlanContext,
      input: GetClubApplicationInput,
      actor: AccessPrincipal
  ): ClubMembershipApplicationView =
    val module = context.support.clubModule
    val club = resolveClub(context.connection, input.clubId)
    val application = resolveApplication(club, input)
    requireClubApplicationViewer(context, actor, club, application)
    ClubApplicationViewAssembler.applicationView(context.connection, module, club, application, actor)

  private def resolveClub(connection: java.sql.Connection, clubId: ClubId): Club =
    ClubTable
      .findById(connection, clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${clubId.value} was not found"))

  private def resolveApplication(
      club: Club,
      input: GetClubApplicationInput
  ): ClubMembershipApplication =
    ClubFunctions.findApplication(club, input.membershipId).getOrElse(
      throw NoSuchElementException(
        s"Membership application ${input.membershipId.value} was not found in club ${input.clubId.value}"
      )
    )

  private def requireClubApplicationViewer(
      context: ApiPlanContext,
      actor: AccessPrincipal,
      club: Club,
      application: ClubMembershipApplication
  ): Unit =
    if !ClubApplicationViewAssembler.canManageClubApplications(actor, club) &&
        !ClubApplicationViewAssembler.canWithdrawClubApplication(context.connection, context.support.clubModule, actor, application)
    then
      throw AuthorizationFailure(s"${actor.displayName} cannot view membership application ${application.id.value}")

  private final case class GetClubApplicationInput(
      clubId: ClubId,
      membershipId: MembershipApplicationId,
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  )
