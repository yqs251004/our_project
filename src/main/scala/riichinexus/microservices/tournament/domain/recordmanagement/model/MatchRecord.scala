package riichinexus.microservices.tournament.domain.recordmanagement.model

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
import java.time.Instant
import java.util.NoSuchElementException

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.Table


final case class MatchRecord(
    id: MatchRecordId,
    tableId: TableId,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    stageRoundNumber: Int,
    generatedAt: Instant,
    seatResults: Vector[MatchRecordSeatResult],
    paifuId: Option[PaifuId] = None,
    finalizedBy: Option[PlayerId] = None,
    sourceEvent: String = "table-state-machine",
    notes: Vector[String] = Vector.empty
) derives CanEqual
