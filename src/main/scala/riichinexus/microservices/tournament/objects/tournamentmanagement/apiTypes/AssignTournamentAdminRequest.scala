package riichinexus.microservices.tournament.objects.tournamentmanagement.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** AssignTournamentAdminRequest 表示分配赛事管理员请求 的前端请求参数。 */

final case class AssignTournamentAdminRequest(
    playerId: String,
    operatorId: String
)

object AssignTournamentAdminRequest:
  given ReadWriter[AssignTournamentAdminRequest] = macroRW
