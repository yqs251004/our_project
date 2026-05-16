package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusAppealWorkflowSuite extends FunSuite with RiichiNexusSuiteSupport:

  test("appeal workflow emits moderation and notification cascade records") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T13:30:00Z")

    val admin = playerService(app).registerPlayer("appeal-admin", "AppealAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("appeal-b", "AppealB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("appeal-c", "AppealC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("appeal-d", "AppealD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Appeal Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Appeal Cascade Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    tableService(app).startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))

    val ticket = appealService(app).fileAppeal(
      tableId = table.id,
      openedBy = table.seats.head.playerId,
      description = "score mismatch",
      actor = principalFor(app, table.seats.head.playerId),
      createdAt = now.plusSeconds(90)
    ).getOrElse(fail("appeal ticket missing"))

    appealService(app).resolveAppeal(
      ticketId = ticket.id,
      verdict = "restored prior state",
      actor = principalFor(app, admin.id),
      resolvedAt = now.plusSeconds(120)
    )

    val records = eventCascadeRecordRepository(app).findAll().filter(_.aggregateId == ticket.id.value)
    assert(records.exists(record => record.eventType == "AppealTicketFiled" && record.consumer == EventCascadeConsumer.ModerationInbox && record.status == EventCascadeStatus.Pending))
    assert(records.exists(record => record.eventType == "AppealTicketResolved" && record.consumer == EventCascadeConsumer.ModerationInbox && record.status == EventCascadeStatus.Completed))
    assert(records.exists(record => record.eventType == "AppealTicketAdjudicated" && record.consumer == EventCascadeConsumer.Notification && record.status == EventCascadeStatus.Completed))
  }

  test("appeal workflow supports triage assignment overdue tracking and reopening") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T13:40:00Z")

    val admin = playerService(app).registerPlayer("appeal-flow-admin", "AppealFlowAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1820)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("appeal-flow-b", "AppealFlowB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("appeal-flow-c", "AppealFlowC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("appeal-flow-d", "AppealFlowD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Appeal Workflow Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Appeal Workflow Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    players.foreach(player => tournamentService(app).registerPlayer(tournament.id, player.id, principalFor(app, admin.id)))
    tournamentService(app).publishTournament(tournament.id, principalFor(app, admin.id))
    val table = tournamentService(app).scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id)).head
    tableService(app).startTable(table.id, now.plusSeconds(60), principalFor(app, admin.id))

    val ticket = appealService(app).fileAppeal(
      tableId = table.id,
      openedBy = table.seats.head.playerId,
      description = "ron points mismatch",
      priority = AppealPriority.High,
      dueAt = Some(now.plusSeconds(600)),
      actor = principalFor(app, table.seats.head.playerId),
      createdAt = now.plusSeconds(90)
    ).getOrElse(fail("appeal ticket missing"))
    assertEquals(ticket.priority, AppealPriority.High)

    val triaged = appealService(app).updateAppealWorkflow(
      ticketId = ticket.id,
      actor = principalFor(app, admin.id),
      assigneeId = Some(admin.id),
      priority = Some(AppealPriority.Critical),
      dueAt = Some(now.plusSeconds(300)),
      updatedAt = now.plusSeconds(120),
      note = Some("expedite finals ruling")
    ).getOrElse(fail("triaged appeal missing"))
    assertEquals(triaged.assigneeId, Some(admin.id))
    assertEquals(triaged.priority, AppealPriority.Critical)
    assertEquals(triaged.dueAt, Some(now.plusSeconds(300)))

    val rejected = appealService(app).adjudicateAppeal(
      ticketId = ticket.id,
      decision = AppealDecisionType.Reject,
      verdict = "log evidence insufficient",
      actor = principalFor(app, admin.id),
      adjudicatedAt = now.plusSeconds(180),
      note = Some("need clearer screenshot")
    ).getOrElse(fail("rejected appeal missing"))
    assertEquals(rejected.status, AppealStatus.Rejected)

    val reopened = appealService(app).reopenAppeal(
      ticketId = ticket.id,
      reason = "additional screenshot uploaded",
      actor = principalFor(app, table.seats.head.playerId),
      reopenedAt = now.plusSeconds(240),
      note = Some("new evidence available")
    ).getOrElse(fail("reopened appeal missing"))

    assertEquals(reopened.status, AppealStatus.Open)
    assertEquals(reopened.reopenCount, 1)
    assertEquals(reopened.assigneeId, Some(admin.id))
    assertEquals(tableRepository(app).findById(table.id).map(_.status), Some(TableStatus.AppealInProgress))

    val records = eventCascadeRecordRepository(app).findAll().filter(_.aggregateId == ticket.id.value)
    assert(records.exists(_.eventType == "AppealTicketWorkflowUpdated"))
    assert(records.exists(_.eventType == "AppealTicketReopened"))
    val auditTypes = auditEventRepository(app).findByAggregate("appeal", ticket.id.value).map(_.eventType)
    assert(auditTypes.contains("AppealTicketWorkflowUpdated"))
    assert(auditTypes.contains("AppealTicketReopened"))
  }
