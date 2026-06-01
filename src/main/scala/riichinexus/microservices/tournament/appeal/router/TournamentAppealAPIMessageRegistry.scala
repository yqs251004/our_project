package riichinexus.microservices.tournament.appeal.router
import riichinexus.api.functions.RegisteredAPIMessageFunctions

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.api.*
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import riichinexus.system.objects.PagedResponse

object TournamentAppealAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessageFunctions.api[AppealFileAPIMessage, AppealTicketView],
      RegisteredAPIMessageFunctions.api[AppealListAPIMessage, PagedResponse[AppealTicketView]],
      RegisteredAPIMessageFunctions.api[AppealGetAPIMessage, AppealTicketView],
      RegisteredAPIMessageFunctions.api[AppealResolveAPIMessage, AppealTicketView],
      RegisteredAPIMessageFunctions.api[AppealAdjudicateAPIMessage, AppealTicketView],
      RegisteredAPIMessageFunctions.api[AppealUpdateWorkflowAPIMessage, AppealTicketView],
      RegisteredAPIMessageFunctions.api[AppealReopenAPIMessage, AppealTicketView]
    )
