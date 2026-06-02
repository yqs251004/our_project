package riichinexus.microservices.club.domain.functions

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.membershipmanagement.MembershipApplicationId

import java.util.UUID

object ClubIdGenerator:
  private def nextId(prefix: String): String =
    s"$prefix-${UUID.randomUUID().toString.take(8)}"

  def clubId(): ClubId = ClubId(nextId("club"))
  def membershipApplicationId(): MembershipApplicationId =
    MembershipApplicationId(nextId("membership"))
