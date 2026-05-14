package riichinexus.microservices.auth.api

import java.time.{Duration, Instant}

import riichinexus.domain.model.*
import riichinexus.microservices.auth.api.requests.{CreateGuestSessionRequest, RevokeGuestSessionRequest, UpgradeGuestSessionRequest}
import riichinexus.microservices.auth.objects.GuestSessionListQuery
import riichinexus.microservices.auth.tables.AuthTables

object GuestSessionApi:

  def listSessions(
      tables: AuthTables,
      query: GuestSessionListQuery
  ): Vector[GuestAccessSession] =
    tables.listGuestSessions(query)

  def createSession(
      service: GuestSessionApplicationService,
      request: Option[CreateGuestSessionRequest]
  ): GuestAccessSession =
    service.createSession(
      displayName = request.flatMap(_.displayName).getOrElse("guest"),
      ttl = Duration.ofHours(request.flatMap(_.ttlHours).getOrElse(24 * 30).toLong),
      deviceFingerprint = request.flatMap(_.deviceFingerprint)
    )

  def findSession(
      tables: AuthTables,
      sessionId: GuestSessionId
  ): Option[GuestAccessSession] =
    tables.findGuestSession(sessionId)

  def revokeSession(
      service: GuestSessionApplicationService,
      sessionId: GuestSessionId,
      request: Option[RevokeGuestSessionRequest]
  ): Option[GuestAccessSession] =
    service.revokeSession(
      sessionId,
      request.flatMap(_.reason).filter(_.trim.nonEmpty).getOrElse("revoked-by-operator")
    )

  def upgradeSession(
      service: GuestSessionApplicationService,
      sessionId: GuestSessionId,
      request: UpgradeGuestSessionRequest
  ): Option[GuestAccessSession] =
    service.upgradeSession(sessionId, request.player)
