package riichinexus.microservices.tournament.domain.tablemanagement.model

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

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.objects.tablemanagement.{TableSeat, TableStatus}

final case class Table(
    id: TableId,
    tableNo: Int,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    seats: Vector[TableSeat],
    stageRoundNumber: Int = 1,
    bracketMatchId: Option[String] = None,
    bracketRoundNumber: Option[Int] = None,
    feederMatchIds: Vector[String] = Vector.empty,
    status: TableStatus = TableStatus.WaitingPreparation,
    startedAt: Option[Instant] = None,
    scoringStartedAt: Option[Instant] = None,
    endedAt: Option[Instant] = None,
    paifuId: Option[PaifuId] = None,
    matchRecordId: Option[MatchRecordId] = None,
    appealTicketIds: Vector[AppealTicketId] = Vector.empty,
    resetCount: Int = 0,
    operatorNotes: Vector[String] = Vector.empty,
    version: Int = 0
) derives CanEqual
