package routes

import java.time.Instant

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import riichinexus.api.ApiModels.given
import riichinexus.api.*
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given

object TableRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "tables" =>
      support.handled {
        val statusFilter = support.queryParam(req, "status").filter(_.nonEmpty).map(
          support.parseEnum("status", _)(TableStatus.valueOf)
        )
        val tournamentIdFilter = support.queryParam(req, "tournamentId").filter(_.nonEmpty).map(TournamentId(_))
        val stageIdFilter = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_))
        val roundNumberFilter = support.queryIntParam(req, "roundNumber")
        val playerIdFilter = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val tables = support.app.tableRepository.findAll()
          .filter(table => statusFilter.forall(_ == table.status))
          .filter(table => tournamentIdFilter.forall(_ == table.tournamentId))
          .filter(table => stageIdFilter.forall(_ == table.stageId))
          .filter(table => roundNumberFilter.forall(_ == table.stageRoundNumber))
          .filter(table => playerIdFilter.forall(playerId => table.seats.exists(_.playerId == playerId)))
          .sortBy(table =>
            (table.tournamentId.value, table.stageId.value, table.stageRoundNumber, table.tableNo, table.id.value)
          )
        support.pagedJsonResponse(
          req,
          tables,
          support.activeFilters(req, "status", "tournamentId", "stageId", "roundNumber", "playerId")
        )
      }

    case GET -> Root / "tables" / tableId =>
      support.handled(support.optionJsonResponse(support.app.tableRepository.findById(TableId(tableId))))

    case req @ POST -> Root / "tables" / tableId / "seats" / seat / "state" =>
      support.handled {
        support.readJsonBody[UpdateTableSeatStateRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tableService.updateSeatState(
              tableId = TableId(tableId),
              seat = support.parseEnum("seat", seat)(SeatWind.valueOf),
              actor = support.principal(request.operator),
              ready = request.ready,
              disconnected = request.disconnected,
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "tables" / tableId / "start" =>
      support.handled {
        support.readOptionalJsonBody[OperatorRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tableService.startTable(
              TableId(tableId),
              actor = request.flatMap(_.operator).map(support.principal).getOrElse(AccessPrincipal.system)
            )
          )
        }
      }

    case req @ POST -> Root / "tables" / tableId / "paifu" =>
      support.handled {
        support.readJsonBody[UploadPaifuRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tableService.recordCompletedTable(
              tableId = TableId(tableId),
              paifu = request.paifu,
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "tables" / tableId / "reset" =>
      support.handled {
        support.readJsonBody[ForceResetTableRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.tableService.forceReset(
              tableId = TableId(tableId),
              note = request.note,
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "tables" / tableId / "appeals" =>
      support.handled {
        support.readJsonBody[FileAppealRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.appealService.fileAppeal(
              tableId = TableId(tableId),
              openedBy = request.player,
              description = request.description,
              attachments = request.attachments.map(_.toAttachment),
              priority = request.priorityLevel,
              dueAt = request.dueAtInstant,
              actor = support.principal(request.player)
            )
          )
        }
      }

    case req @ GET -> Root / "records" =>
      support.handled {
        val playerIdFilter = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val tournamentIdFilter = support.queryParam(req, "tournamentId").filter(_.nonEmpty).map(TournamentId(_))
        val stageIdFilter = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_))
        val tableIdFilter = support.queryParam(req, "tableId").filter(_.nonEmpty).map(TableId(_))
        val records = support.app.matchRecordRepository.findAll()
          .filter(record => playerIdFilter.forall(record.playerIds.contains))
          .filter(record => tournamentIdFilter.forall(_ == record.tournamentId))
          .filter(record => stageIdFilter.forall(_ == record.stageId))
          .filter(record => tableIdFilter.forall(_ == record.tableId))
          .sortBy(record => (record.generatedAt, record.id.value))
        support.pagedJsonResponse(
          req,
          records,
          support.activeFilters(req, "playerId", "tournamentId", "stageId", "tableId")
        )
      }

    case GET -> Root / "records" / recordId =>
      support.handled(support.optionJsonResponse(support.app.matchRecordRepository.findById(MatchRecordId(recordId))))

    case GET -> Root / "records" / "table" / tableId =>
      support.handled(support.optionJsonResponse(support.app.matchRecordRepository.findByTable(TableId(tableId))))

    case req @ GET -> Root / "paifus" =>
      support.handled {
        val playerIdFilter = support.queryParam(req, "playerId").filter(_.nonEmpty).map(PlayerId(_))
        val tournamentIdFilter = support.queryParam(req, "tournamentId").filter(_.nonEmpty).map(TournamentId(_))
        val stageIdFilter = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_))
        val tableIdFilter = support.queryParam(req, "tableId").filter(_.nonEmpty).map(TableId(_))
        val paifus = support.app.paifuRepository.findAll()
          .filter(paifu => playerIdFilter.forall(paifu.playerIds.contains))
          .filter(paifu => tournamentIdFilter.forall(_ == paifu.metadata.tournamentId))
          .filter(paifu => stageIdFilter.forall(_ == paifu.metadata.stageId))
          .filter(paifu => tableIdFilter.forall(_ == paifu.metadata.tableId))
          .sortBy(paifu => (paifu.metadata.recordedAt, paifu.id.value))
        support.pagedJsonResponse(
          req,
          paifus,
          support.activeFilters(req, "playerId", "tournamentId", "stageId", "tableId")
        )
      }

    case GET -> Root / "paifus" / paifuId =>
      support.handled(support.optionJsonResponse(support.app.paifuRepository.findById(PaifuId(paifuId))))

    case req @ GET -> Root / "appeals" =>
      support.handled {
        val statusFilter = support.queryParam(req, "status").filter(_.nonEmpty).map(
          support.parseEnum("status", _)(AppealStatus.valueOf)
        )
        val priorityFilter = support.queryParam(req, "priority").filter(_.nonEmpty).map(
          support.parseEnum("priority", _)(AppealPriority.valueOf)
        )
        val tournamentIdFilter = support.queryParam(req, "tournamentId").filter(_.nonEmpty).map(TournamentId(_))
        val stageIdFilter = support.queryParam(req, "stageId").filter(_.nonEmpty).map(TournamentStageId(_))
        val tableIdFilter = support.queryParam(req, "tableId").filter(_.nonEmpty).map(TableId(_))
        val openedByFilter = support.queryParam(req, "openedBy").filter(_.nonEmpty).map(PlayerId(_))
        val assigneeIdFilter = support.queryParam(req, "assigneeId").filter(_.nonEmpty).map(PlayerId(_))
        val overdueOnly = support.queryParam(req, "overdueOnly").exists(_.equalsIgnoreCase("true"))
        val dueBeforeFilter = support.queryParam(req, "dueBefore").filter(_.nonEmpty).map(Instant.parse)
        val dueAfterFilter = support.queryParam(req, "dueAfter").filter(_.nonEmpty).map(Instant.parse)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val appeals = support.app.appealTicketRepository.findAll()
          .filter(ticket => statusFilter.forall(_ == ticket.status))
          .filter(ticket => priorityFilter.forall(_ == ticket.priority))
          .filter(ticket => tournamentIdFilter.forall(_ == ticket.tournamentId))
          .filter(ticket => stageIdFilter.forall(_ == ticket.stageId))
          .filter(ticket => tableIdFilter.forall(_ == ticket.tableId))
          .filter(ticket => openedByFilter.forall(_ == ticket.openedBy))
          .filter(ticket => assigneeIdFilter.forall(ticket.assigneeId.contains))
          .filter(ticket => !overdueOnly || ticket.dueAt.exists(_.isBefore(asOf)))
          .filter(ticket => dueBeforeFilter.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isAfter(limit))))
          .filter(ticket => dueAfterFilter.forall(limit => ticket.dueAt.exists(dueAt => !dueAt.isBefore(limit))))
          .sortBy(ticket => (ticket.updatedAt, ticket.id.value))
        support.pagedJsonResponse(
          req,
          appeals,
          support.activeFilters(
            req,
            "status",
            "priority",
            "tournamentId",
            "stageId",
            "tableId",
            "openedBy",
            "assigneeId",
            "overdueOnly",
            "dueBefore",
            "dueAfter",
            "asOf"
          )
        )
      }

    case GET -> Root / "appeals" / appealId =>
      support.handled(support.optionJsonResponse(support.app.appealTicketRepository.findById(AppealTicketId(appealId))))

    case req @ GET -> Root / "audits" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ViewAuditTrail)
        val aggregateTypeFilter = support.queryParam(req, "aggregateType").filter(_.nonEmpty)
        val aggregateIdFilter = support.queryParam(req, "aggregateId").filter(_.nonEmpty)
        val actorIdFilter = support.queryParam(req, "actorId").filter(_.nonEmpty).map(PlayerId(_))
        val eventTypeFilter = support.queryParam(req, "eventType").filter(_.nonEmpty)
        val audits = support.app.auditEventRepository.findAll()
          .filter(entry => aggregateTypeFilter.forall(_ == entry.aggregateType))
          .filter(entry => aggregateIdFilter.forall(_ == entry.aggregateId))
          .filter(entry => actorIdFilter.forall(entry.actorId.contains))
          .filter(entry => eventTypeFilter.forall(_ == entry.eventType))
          .sortBy(entry => (entry.occurredAt, entry.id.value))
        support.pagedJsonResponse(
          req,
          audits,
          support.activeFilters(req, "aggregateType", "aggregateId", "actorId", "eventType", "operatorId")
        )
      }

    case req @ GET -> Root / "audits" / aggregateType / aggregateId =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ViewAuditTrail)
        val actorIdFilter = support.queryParam(req, "actorId").filter(_.nonEmpty).map(PlayerId(_))
        val eventTypeFilter = support.queryParam(req, "eventType").filter(_.nonEmpty)
        val audits = support.app.auditEventRepository.findByAggregate(aggregateType, aggregateId)
          .filter(entry => actorIdFilter.forall(entry.actorId.contains))
          .filter(entry => eventTypeFilter.forall(_ == entry.eventType))
          .sortBy(entry => (entry.occurredAt, entry.id.value))
        support.pagedJsonResponse(
          req,
          audits,
          support.activeFilters(req, "actorId", "eventType", "operatorId") ++
            Map("aggregateType" -> aggregateType, "aggregateId" -> aggregateId)
        )
      }

    case req @ POST -> Root / "appeals" / appealId / "resolve" =>
      support.handled {
        support.readJsonBody[ResolveAppealRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.appealService.resolveAppeal(
              ticketId = AppealTicketId(appealId),
              verdict = request.verdict,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "appeals" / appealId / "adjudicate" =>
      support.handled {
        support.readJsonBody[AdjudicateAppealRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.appealService.adjudicateAppeal(
              ticketId = AppealTicketId(appealId),
              decision = request.decisionType,
              verdict = request.verdict,
              actor = support.principal(request.operator),
              tableResolution = request.resolution,
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "appeals" / appealId / "workflow" =>
      support.handled {
        support.readJsonBody[UpdateAppealWorkflowRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.appealService.updateAppealWorkflow(
              ticketId = AppealTicketId(appealId),
              actor = support.principal(request.operator),
              assigneeId = request.assignee,
              clearAssignee = request.clearAssignee,
              priority = request.priorityLevel,
              dueAt = request.dueAtInstant,
              clearDueAt = request.clearDueAt,
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "appeals" / appealId / "reopen" =>
      support.handled {
        support.readJsonBody[ReopenAppealRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.appealService.reopenAppeal(
              ticketId = AppealTicketId(appealId),
              reason = request.reason,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }
  }
