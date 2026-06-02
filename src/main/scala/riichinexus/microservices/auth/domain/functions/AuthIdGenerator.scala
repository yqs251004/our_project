package riichinexus.microservices.auth.domain.functions

import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId

import java.util.UUID

object AuthIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def guestSessionId(): GuestSessionId = GuestSessionId(nextId("guest"))
