package riichinexus.microservices.club.domain.functions

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId

import java.util.UUID

/** ClubIdGenerator 负责生成俱乐部标识符生成器 相关的领域标识符。 */

private[club] object ClubIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def clubId(): ClubId = ClubId(nextId("club"))
  def membershipApplicationId(): MembershipApplicationId =
    MembershipApplicationId(nextId("membership"))
