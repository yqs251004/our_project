package riichinexus.microservices.tournament.appeal.api.responses

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object TournamentAppealResponseCodecs:
  given ReadWriter[AppealAttachmentView] = macroRW
  given ReadWriter[AppealDecisionLogView] = macroRW
  given ReadWriter[AppealTicketView] = macroRW
