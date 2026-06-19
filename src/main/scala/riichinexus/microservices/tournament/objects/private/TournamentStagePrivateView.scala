package riichinexus.microservices.tournament.objects.`private`

import riichinexus.microservices.tournament.objects.tournamentmanagement.{StageStatus, TournamentStageId}

/** TournamentStagePrivateView 表示后端内部使用的赛事阶段后端内部视图 read model，包含 ID、名称、order、状态、lineupSubmissions。 */

final case class TournamentStagePrivateView(
    id: TournamentStageId,
    name: String,
    order: Int,
    status: StageStatus,
    lineupSubmissions: Vector[StageLineupSubmissionPrivateView]
)
