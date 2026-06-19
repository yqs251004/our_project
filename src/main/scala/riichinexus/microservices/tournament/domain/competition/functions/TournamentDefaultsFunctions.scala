package riichinexus.microservices.tournament.domain.competition.functions

import riichinexus.microservices.tournament.domain.identity.functions.TournamentIdGenerator
import riichinexus.microservices.tournament.domain.stage.model.TournamentStage
import riichinexus.microservices.tournament.domain.competition.model.Tournament

import riichinexus.microservices.tournament.objects.competition.TournamentFormat

/** TournamentDefaultsFunctions 提供赛事默认值相关的领域计算、校验和转换函数。 */

private[tournament] object TournamentDefaultsFunctions:
  def initialStage(): TournamentStage =
    TournamentStage(
      id = TournamentIdGenerator.stageId(),
      name = "Swiss Stage 1",
      format = TournamentFormat.Swiss,
      order = 1,
      roundCount = 4
    )

  def initialStages(stages: Vector[TournamentStage]): Vector[TournamentStage] =
    if stages.nonEmpty then stages else Vector(initialStage())

  def ensureInitialStage(tournament: Tournament): Tournament =
    if tournament.stages.nonEmpty then tournament
    else tournament.copy(stages = initialStages(tournament.stages))
