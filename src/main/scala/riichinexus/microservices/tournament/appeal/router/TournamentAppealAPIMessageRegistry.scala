package riichinexus.microservices.tournament.appeal.router

import riichinexus.api.RegisteredAPIMessage
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.api.*
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import riichinexus.microservices.tournament.appeal.objects.apiTypes.TournamentAppealResponses.given
import riichinexus.system.objects.apiTypes.PagedResponse

object TournamentAppealAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[AppealFileAPIMessage, AppealTicketView],
      RegisteredAPIMessage.api[AppealListAPIMessage, PagedResponse[AppealTicketView]],
      RegisteredAPIMessage.api[AppealGetAPIMessage, AppealTicketView],
      RegisteredAPIMessage.api[AppealResolveAPIMessage, AppealTicketView],
      RegisteredAPIMessage.api[AppealAdjudicateAPIMessage, AppealTicketView],
      RegisteredAPIMessage.api[AppealUpdateWorkflowAPIMessage, AppealTicketView],
      RegisteredAPIMessage.api[AppealReopenAPIMessage, AppealTicketView]
    )
