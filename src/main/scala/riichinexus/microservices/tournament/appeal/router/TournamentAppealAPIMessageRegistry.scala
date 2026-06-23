package riichinexus.microservices.tournament.appeal.router
import riichinexus.system.api.RegisteredAPIMessage


import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.appeal.api.{AppealAdjudicateAPIMessage, AppealFileAPIMessage, AppealGetAPIMessage, AppealListAPIMessage, AppealReopenAPIMessage, AppealUpdateWorkflowAPIMessage}
import riichinexus.microservices.tournament.appeal.objects.AppealTicketView
import riichinexus.system.objects.PagedResponse

object TournamentAppealAPIMessageRegistry:

  val apiMessages: Vector[RegisteredAPIMessage] =
    Vector(
      RegisteredAPIMessage.api[AppealFileAPIMessage, AppealTicketView],
      RegisteredAPIMessage.api[AppealListAPIMessage, PagedResponse[AppealTicketView]],
      RegisteredAPIMessage.api[AppealGetAPIMessage, AppealTicketView],
      RegisteredAPIMessage.api[AppealAdjudicateAPIMessage, AppealTicketView],
      RegisteredAPIMessage.api[AppealUpdateWorkflowAPIMessage, AppealTicketView],
      RegisteredAPIMessage.api[AppealReopenAPIMessage, AppealTicketView]
    )
