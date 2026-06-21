package riichinexus.microservices.tournament.objects.competition.apiTypes

import java.time.Instant

import riichinexus.system.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.stage.apiTypes.CreateTournamentStageRequest
import upickle.default.{ReadWriter, macroRW}

/** 创建赛事时提交的基础信息和阶段规划。
  *
  * 请求同时定义赛事名称、主办方、时间窗口、初始阶段列表和可选管理员，后端会据此生成赛事聚合与阶段结构。
  */
final case class CreateTournamentRequest(
    name: String,
    organizer: String,
    startsAt: Instant,
    endsAt: Instant,
    stages: Vector[CreateTournamentStageRequest],
    adminId: Option[String] = None
)

object CreateTournamentRequest:
  given ReadWriter[CreateTournamentRequest] = macroRW
