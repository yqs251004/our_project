package riichinexus.microservices.auth.domain.functions

import riichinexus.microservices.auth.objects.sessionmanagement.GuestSessionId

import java.util.UUID

/** AuthIdGenerator 负责生成认证标识符生成器 相关的领域标识符。 */

private[auth] object AuthIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def guestSessionId(): GuestSessionId = GuestSessionId(nextId("guest"))
