package riichinexus.microservices.tournament.domain.model

import riichinexus.domain.model.IdGenerator
import riichinexus.microservices.tournament.objects.TournamentFormat

object TournamentDefaults:
  def initialStage(): TournamentStage =
    TournamentStage(
      id = IdGenerator.stageId(),
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
