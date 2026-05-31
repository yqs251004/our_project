package riichinexus.microservices.tournament.domain.tournamentmanagement.functions

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import riichinexus.domain.model.IdGenerator
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.{Tournament, TournamentStage}
import riichinexus.microservices.tournament.objects.tournamentmanagement.TournamentFormat

object TournamentDefaultsFunctions:
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
