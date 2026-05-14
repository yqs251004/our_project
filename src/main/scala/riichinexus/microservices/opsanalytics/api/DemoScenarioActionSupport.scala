package riichinexus.microservices.opsanalytics.api

import java.time.{Duration, Instant}
import java.util.NoSuchElementException

import riichinexus.application.ports.*
import riichinexus.domain.event.*
import riichinexus.domain.model.*
import riichinexus.domain.service.*
import riichinexus.microservices.auth.api.GuestSessionApplicationService
import riichinexus.microservices.club.api.ClubApplicationService
import riichinexus.microservices.dictionary.api.RuntimeDictionarySupport
import riichinexus.microservices.opsanalytics.api.responses.*
import riichinexus.microservices.player.api.PlayerApplicationService
import riichinexus.microservices.publicquery.api.PublicQueryService
import riichinexus.microservices.tournament.api.{TableLifecycleService, TournamentApplicationService}
import riichinexus.microservices.tournament.appeal.api.AppealApplicationService

private[opsanalytics] trait DemoScenarioActionSupport extends DemoScenarioSupport:
  protected def archiveNextRunnableTable(
      config: DemoScenarioConfig,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal
  ): String =
    val candidate = tableRepository.findByTournamentAndStage(tournamentId, stageId)
      .sortBy(table => (table.tableNo, table.id.value))
      .find(table =>
        matchRecordRepository.findByTable(table.id).isEmpty &&
          (table.status == TableStatus.WaitingPreparation || table.status == TableStatus.InProgress)
      )
      .getOrElse(throw IllegalArgumentException(s"No runnable tables remain in ${config.variant} demo scenario"))

    val startedTable =
      if candidate.status == TableStatus.WaitingPreparation then
        tableService.startTable(candidate.id, Instant.now(), actor).getOrElse(candidate)
      else candidate

    tableService.recordCompletedTable(
      startedTable.id,
      demoPaifu(startedTable, tournamentId, stageId, Instant.now()),
      actor
    )
    s"Archived table ${startedTable.id.value} for ${config.variant} demo scenario."

  protected def createDemoAppeal(
      config: DemoScenarioConfig,
      tournamentId: TournamentId,
      stageId: TournamentStageId
  ): String =
    val table = tableRepository.findByTournamentAndStage(tournamentId, stageId)
      .sortBy(table => (table.tableNo, table.id.value))
      .find(table =>
        table.status != TableStatus.Archived &&
          !appealTicketRepository.findAll().exists(ticket =>
            ticket.tableId == table.id &&
              (ticket.status == AppealStatus.Open ||
                ticket.status == AppealStatus.UnderReview ||
                ticket.status == AppealStatus.Escalated)
          )
      )
      .getOrElse(throw IllegalArgumentException(s"No eligible tables remain for new appeals in ${config.variant} demo scenario"))

    val openedBy = table.seats.head.playerId
    appealService.fileAppeal(
      tableId = table.id,
      openedBy = openedBy,
      description = s"Demo action appeal filed for ${config.variant} scenario.",
      priority = AppealPriority.High,
      dueAt = Some(Instant.now().plus(Duration.ofHours(2))),
      actor = principalFor(openedBy),
      createdAt = Instant.now()
    )
    s"Filed a demo appeal on table ${table.id.value}."

  protected def resolveOldestDemoAppeal(
      config: DemoScenarioConfig,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      actor: AccessPrincipal
  ): String =
    val ticket = appealTicketRepository.findAll()
      .filter(ticket =>
        ticket.tournamentId == tournamentId &&
          ticket.stageId == stageId &&
          (ticket.status == AppealStatus.Open ||
            ticket.status == AppealStatus.UnderReview ||
            ticket.status == AppealStatus.Escalated)
      )
      .sortBy(ticket => (ticket.createdAt, ticket.id.value))
      .headOption
      .getOrElse(throw IllegalArgumentException(s"No active appeals remain in ${config.variant} demo scenario"))

    appealService.adjudicateAppeal(
      ticketId = ticket.id,
      decision = AppealDecisionType.Resolve,
      verdict = "Demo action resolved the appeal and restored the table flow.",
      actor = actor,
      adjudicatedAt = Instant.now(),
      tableResolution = Some(AppealTableResolution.RestorePriorState),
      note = Some("demo-action-resolve")
    )
    s"Resolved appeal ${ticket.id.value}."
