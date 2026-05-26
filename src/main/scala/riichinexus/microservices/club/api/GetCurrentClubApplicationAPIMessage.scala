package riichinexus.microservices.club.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.bootstrap.ClubModuleContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.club.domain.ClubApplicationViewAssembler
import riichinexus.microservices.club.objects.ClubMembershipApplicationView
import riichinexus.microservices.club.tables.club.ClubTable
import upickle.default.*

final case class GetCurrentClubApplicationAPIMessage(
    clubId: String,
    operatorId: Option[String] = None,
    guestSessionId: Option[String] = None
) extends APIMessage[ClubMembershipApplicationView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[ClubMembershipApplicationView] =
    for
      input <- IO(resolveInput)
      actor <- IO(resolveActor(context, input))
      view <- IO(getCurrentApplicationView(context, input, actor))
    yield view

  private def resolveInput: CurrentClubApplicationInput =
    val parsedGuestSessionId = guestSessionId.filter(_.nonEmpty).map(GuestSessionId(_))
    val parsedOperatorId = operatorId.filter(_.nonEmpty).map(PlayerId(_))
    if parsedGuestSessionId.isEmpty && parsedOperatorId.isEmpty then
      throw IllegalArgumentException("operatorId or guestSessionId is required")
    CurrentClubApplicationInput(
      clubId = ClubId(clubId),
      operatorId = parsedOperatorId,
      guestSessionId = parsedGuestSessionId
    )

  private def resolveActor(
      context: ApiPlanContext,
      input: CurrentClubApplicationInput
  ): AccessPrincipal =
    context.requestActor(input.guestSessionId, input.operatorId)

  private def getCurrentApplicationView(
      context: ApiPlanContext,
      input: CurrentClubApplicationInput,
      actor: AccessPrincipal
  ): ClubMembershipApplicationView =
    val module = context.support.clubModule
    val club = ClubTable
      .findById(context.connection, input.clubId)
      .getOrElse(throw NoSuchElementException(s"Club ${input.clubId.value} was not found"))
    val application = club.membershipApplications
      .filter(application => application.isPending && ClubApplicationViewAssembler.ownsClubApplication(context.connection, module, actor, application))
      .maxByOption(_.submittedAt)
      .getOrElse(throw NoSuchElementException("Resource not found"))
    ClubApplicationViewAssembler.applicationView(context.connection, module, club, application, actor)

  private final case class CurrentClubApplicationInput(
      clubId: ClubId,
      operatorId: Option[PlayerId],
      guestSessionId: Option[GuestSessionId]
  )
