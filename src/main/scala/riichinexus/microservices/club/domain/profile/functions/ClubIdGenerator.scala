package riichinexus.microservices.club.domain.profile.functions

import riichinexus.microservices.club.domain.profile.model.ClubIdPrefix
import riichinexus.microservices.club.objects.profile.ClubId
import riichinexus.microservices.club.objects.membership.MembershipApplicationId

import java.util.UUID

/** ClubIdGenerator 负责生成俱乐部标识符生成器 相关的领域标识符。 */

private[club] object ClubIdGenerator:
  private def nextId(prefix: ClubIdPrefix): String =
    s"${ClubIdPrefix.toString(prefix)}-${UUID.randomUUID().toString.take(8)}"

  def clubId(): ClubId = ClubId(nextId(ClubIdPrefix.Club))
  def membershipApplicationId(): MembershipApplicationId =
    MembershipApplicationId(nextId(ClubIdPrefix.MembershipApplication))
