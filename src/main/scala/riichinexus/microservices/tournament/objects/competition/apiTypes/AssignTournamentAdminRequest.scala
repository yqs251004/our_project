package riichinexus.microservices.tournament.objects.competition.apiTypes

import upickle.default.{ReadWriter, macroRW}

/** 授予赛事管理员身份时提交的请求体。
  *
  * `playerId` 是被授权的玩家，`operatorId` 是当前执行授权的人，后端会检查其赛事或平台管理权限。
  */
final case class AssignTournamentAdminRequest(
    playerId: String,
    operatorId: String
)

object AssignTournamentAdminRequest:
  given ReadWriter[AssignTournamentAdminRequest] = macroRW
