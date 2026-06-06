package riichinexus.microservices.club.domain.relationmanagement.functions

import riichinexus.microservices.auth.domain.AuthorizationFailure
import riichinexus.microservices.auth.domain.functions.AccessPrincipalFunctions
import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.microservices.auth.objects.Permission
import riichinexus.microservices.club.domain.{Club, ClubAuthorization}

object ClubRelationAuthorizationFunctions:
  def requireDirectRelationUpdate(actor: AccessPrincipal): Unit =
    if !AccessPrincipalFunctions.isSuperAdmin(actor) then
      throw AuthorizationFailure("Only super admins can directly update club relations")

  def requireRelationRequestActor(actor: AccessPrincipal, club: Club): Unit =
    ClubAuthorization.requireClubAdmin(
      actor = actor,
      club = club,
      permission = Permission.ManageClubOperations
    )
