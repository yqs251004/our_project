package riichinexus.microservices.club.domain.relationmanagement.functions

import java.time.Instant

import munit.FunSuite

import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.microservices.auth.domain.model.{AccessPrincipal, RoleGrant}
import riichinexus.microservices.auth.objects.Role
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.player.objects.playerprofile.PlayerId

class ClubRelationAuthorizationFunctionsSuite extends FunSuite:

  test("direct club relation updates are restricted to super admins") {
    intercept[AuthorizationFailure] {
      ClubRelationAuthorizationFunctions.requireDirectRelationUpdate(
        principal("club-admin", RoleGrant(Role.ClubAdmin, fixedInstant, clubId = Some(clubId)))
      )
    }

    ClubRelationAuthorizationFunctions.requireDirectRelationUpdate(
      principal("super-admin", RoleGrant(Role.SuperAdmin, fixedInstant))
    )
  }

  test("ordinary club admins may submit relation change requests for their own club") {
    ClubRelationAuthorizationFunctions.requireRelationRequestActor(
      principal("club-admin", RoleGrant(Role.ClubAdmin, fixedInstant, clubId = Some(clubId))),
      club
    )
  }

  test("non-admin players cannot submit relation change requests") {
    intercept[AuthorizationFailure] {
      ClubRelationAuthorizationFunctions.requireRelationRequestActor(
        principal("member", RoleGrant(Role.RegisteredPlayer, fixedInstant)),
        club
      )
    }
  }

  private val fixedInstant = Instant.parse("2026-06-06T00:00:00Z")
  private val clubId = ClubId("club-relation-auth")
  private val adminId = PlayerId("player-club-admin")
  private val memberId = PlayerId("player-member")

  private val club = Club(
    id = clubId,
    name = "Relation Auth Club",
    creator = adminId,
    createdAt = fixedInstant,
    members = Vector(adminId, memberId),
    admins = Vector(adminId)
  )

  private def principal(id: String, grants: RoleGrant*): AccessPrincipal =
    val playerId = PlayerId(s"player-$id")

    AccessPrincipal(
      principalId = playerId.value,
      displayName = id,
      playerId = Some(playerId),
      roleGrants = grants.toVector
    )
