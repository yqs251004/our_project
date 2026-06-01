package riichinexus.microservices.auth.api.`private`

import java.sql.Connection
import java.util.NoSuchElementException

import cats.effect.unsafe.implicits.global
import riichinexus.api.ApiPlanContext
import riichinexus.domain.model.{GuestSessionId, PlayerId}
import riichinexus.microservices.auth.domain.functions.AccessPrincipalFunctions
import riichinexus.microservices.auth.domain.model.{AccessPrincipal, GuestAccessSession}
import riichinexus.microservices.player.api.GetPlayerAPIMessage
import riichinexus.microservices.player.domain.functions.PlayerPrincipalFunctions

object AuthAccessPrincipalResolver:

  def principal(context: ApiPlanContext, playerId: PlayerId): AccessPrincipal =
    registered(context.connection, playerId)

  def registered(connection: Connection, playerId: PlayerId): AccessPrincipal =
    GetPlayerAPIMessage.findPlayer(connection, playerId)
      .map(PlayerPrincipalFunctions.asPrincipal)
      .getOrElse(throw NoSuchElementException(s"Player ${playerId.value} was not found"))

  def guest(context: ApiPlanContext, sessionId: GuestSessionId): AccessPrincipal =
    AccessPrincipalFunctions.guest(resolveGuestSession(context, sessionId))

  def requestActor(
      context: ApiPlanContext,
      guestSessionId: Option[GuestSessionId],
      operatorId: Option[PlayerId]
  ): AccessPrincipal =
    if guestSessionId.nonEmpty && operatorId.nonEmpty then
      throw IllegalArgumentException("guestSessionId and operatorId cannot be provided together")

    guestSessionId.map(guest(context, _))
      .orElse(operatorId.map(principal(context, _)))
      .getOrElse(AccessPrincipalFunctions.guest())

  def resolveGuestSession(context: ApiPlanContext, sessionId: GuestSessionId): GuestAccessSession =
    ResolveGuestSessionAuthPrivateAPIMessage(sessionId)
      .plan(context)
      .unsafeRunSync()
