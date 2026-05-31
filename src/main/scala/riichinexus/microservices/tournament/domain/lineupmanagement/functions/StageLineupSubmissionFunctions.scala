package riichinexus.microservices.tournament.domain.lineupmanagement.functions

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
import riichinexus.domain.model.PlayerId
import riichinexus.microservices.tournament.domain.lineupmanagement.model.StageLineupSubmission

object StageLineupSubmissionFunctions:
  def validate(submission: StageLineupSubmission): Unit =
    require(submission.seats.nonEmpty, "Lineup submission must contain at least one seat")
    require(
      submission.seats.map(_.playerId).distinct.size == submission.seats.size,
      "Lineup submission cannot contain duplicate players"
    )
    require(
      submission.seats.exists(seat => !seat.reserve),
      "Lineup submission must contain at least one active player"
    )

  def activePlayerIds(submission: StageLineupSubmission): Vector[PlayerId] =
    submission.seats.filterNot(_.reserve).map(_.playerId)
