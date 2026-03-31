package routes

import java.time.Instant

import api.contracts.ApiContracts.*
import api.contracts.JsonSupport.given
import cats.effect.IO
import model.DomainModels.*
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*

object AdminRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "admin" / "advanced-stats" / "tasks" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val statusFilter = support.queryParam(req, "status").map(AdvancedStatsRecomputeTaskStatus.valueOf)
        val tasks = support.app.advancedStatsRecomputeTaskRepository.findAll()
          .filter(task => statusFilter.forall(_ == task.status))
        support.pagedJsonResponse(req, tasks, support.activeFilters(req, "status"))
      }

    case req @ GET -> Root / "admin" / "advanced-stats" / "summary" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        support.jsonResponse(Status.Ok, support.app.advancedStatsPipelineService.taskQueueSummary(asOf))
      }

    case req @ GET -> Root / "admin" / "domain-events" / "summary" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        support.jsonResponse(Status.Ok, support.app.domainEventOperationsService.summary(asOf))
      }

    case req @ GET -> Root / "admin" / "domain-events" / "outbox" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val statusFilter = support.queryParam(req, "status").map(DomainEventOutboxStatus.valueOf)
        val eventTypeFilter = support.queryParam(req, "eventType").filter(_.nonEmpty)
        val aggregateTypeFilter = support.queryParam(req, "aggregateType").filter(_.nonEmpty)
        val aggregateIdFilter = support.queryParam(req, "aggregateId").filter(_.nonEmpty)
        val subscriberIdFilter = support.queryParam(req, "subscriberId").filter(_.nonEmpty)
        val partitionKeyFilter = support.queryParam(req, "partitionKey").filter(_.nonEmpty)
        val deliveredFilter = support.queryBooleanParam(req, "delivered")
        val blockedOnly = support.queryBooleanParam(req, "blockedOnly").getOrElse(false)
        require(subscriberIdFilter.nonEmpty || deliveredFilter.isEmpty, "Query parameter delivered requires subscriberId")
        require(subscriberIdFilter.nonEmpty || partitionKeyFilter.isEmpty, "Query parameter partitionKey requires subscriberId")
        require(subscriberIdFilter.nonEmpty || !blockedOnly, "Query parameter blockedOnly requires subscriberId")
        val records = support.app.domainEventOperationsService.outboxRecords(
          asOf = asOf,
          status = statusFilter,
          eventType = eventTypeFilter,
          aggregateType = aggregateTypeFilter,
          aggregateId = aggregateIdFilter,
          subscriberId = subscriberIdFilter,
          partitionKey = partitionKeyFilter,
          delivered = deliveredFilter,
          blockedOnly = blockedOnly
        )
        support.pagedJsonResponse(
          req,
          records,
          support.activeFilters(
            req,
            "asOf",
            "status",
            "eventType",
            "aggregateType",
            "aggregateId",
            "subscriberId",
            "partitionKey",
            "delivered",
            "blockedOnly"
          )
        )
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / "replay" =>
      support.handled {
        support.readJsonBody[BatchReplayDomainEventOutboxRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.domainEventOperationsService.replayOutboxRecords(
              recordIds = request.records,
              actor = support.principal(request.operator),
              replayAt = request.replayAtInstant.getOrElse(Instant.now()),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / "ack" =>
      support.handled {
        support.readJsonBody[BatchAcknowledgeDomainEventOutboxRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.domainEventOperationsService.acknowledgeOutboxRecords(
              recordIds = request.records,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / "quarantine" =>
      support.handled {
        support.readJsonBody[BatchQuarantineDomainEventOutboxRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.domainEventOperationsService.quarantineOutboxRecords(
              recordIds = request.records,
              actor = support.principal(request.operator),
              reason = request.reason
            )
          )
        }
      }

    case req @ GET -> Root / "admin" / "domain-events" / "outbox" / recordId / "history" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.jsonResponse(
          Status.Ok,
          support.app.domainEventOperationsService.outboxHistory(
            recordId = DomainEventOutboxRecordId(recordId),
            actor = operator
          )
        )
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / recordId / "replay" =>
      support.handled {
        support.readJsonBody[ReplayDomainEventOutboxRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.domainEventOperationsService.replayOutboxRecord(
              recordId = DomainEventOutboxRecordId(recordId),
              actor = support.principal(request.operator),
              replayAt = request.replayAtInstant.getOrElse(Instant.now()),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / recordId / "ack" =>
      support.handled {
        support.readJsonBody[AcknowledgeDomainEventOutboxRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.domainEventOperationsService.acknowledgeOutboxRecord(
              recordId = DomainEventOutboxRecordId(recordId),
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "admin" / "domain-events" / "outbox" / recordId / "quarantine" =>
      support.handled {
        support.readJsonBody[QuarantineDomainEventOutboxRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.domainEventOperationsService.quarantineOutboxRecord(
              recordId = DomainEventOutboxRecordId(recordId),
              actor = support.principal(request.operator),
              reason = request.reason
            )
          )
        }
      }

    case req @ GET -> Root / "admin" / "domain-events" / "subscribers" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val subscriberIdFilter = support.queryParam(req, "subscriberId").filter(_.nonEmpty)
        val subscribers = support.app.domainEventOperationsService.subscriberStatuses(
          asOf = asOf,
          subscriberId = subscriberIdFilter
        )
        support.pagedJsonResponse(req, subscribers, support.activeFilters(req, "asOf", "subscriberId"))
      }

    case req @ GET -> Root / "admin" / "domain-events" / "subscribers" / subscriberId / "partitions" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val lagOnly = support.queryBooleanParam(req, "lagOnly").getOrElse(false)
        val blockedOnly = support.queryBooleanParam(req, "blockedOnly").getOrElse(false)
        val partitionKeyFilter = support.queryParam(req, "partitionKey").filter(_.nonEmpty)
        val partitions = support.app.domainEventOperationsService.subscriberPartitionStatuses(
          subscriberId = subscriberId,
          asOf = asOf,
          lagOnly = lagOnly,
          blockedOnly = blockedOnly,
          partitionKey = partitionKeyFilter
        )
        support.pagedJsonResponse(
          req,
          partitions,
          support.activeFilters(req, "asOf", "lagOnly", "blockedOnly", "partitionKey")
        )
      }

    case req @ GET -> Root / "admin" / "event-cascade-records" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        support.requirePermission(operator, Permission.ManageGlobalDictionary)
        val statusFilter = support.queryParam(req, "status").map(EventCascadeStatus.valueOf)
        val consumerFilter = support.queryParam(req, "consumer").map(EventCascadeConsumer.valueOf)
        val eventTypeFilter = support.queryParam(req, "eventType").filter(_.nonEmpty)
        val aggregateTypeFilter = support.queryParam(req, "aggregateType").filter(_.nonEmpty)
        val aggregateIdFilter = support.queryParam(req, "aggregateId").filter(_.nonEmpty)
        val records = support.app.eventCascadeRecordRepository.findAll()
          .filter(record => statusFilter.forall(_ == record.status))
          .filter(record => consumerFilter.forall(_ == record.consumer))
          .filter(record => eventTypeFilter.forall(_ == record.eventType))
          .filter(record => aggregateTypeFilter.forall(_ == record.aggregateType))
          .filter(record => aggregateIdFilter.forall(_ == record.aggregateId))
          .sortBy(record => (record.occurredAt, record.id.value))
        support.pagedJsonResponse(
          req,
          records,
          support.activeFilters(req, "status", "consumer", "eventType", "aggregateType", "aggregateId")
        )
      }

    case req @ POST -> Root / "admin" / "players" / playerId / "ban" =>
      support.handled {
        support.readJsonBody[BanPlayerRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.superAdminService.banPlayer(
              playerId = PlayerId(playerId),
              reason = request.reason,
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "admin" / "clubs" / clubId / "dissolve" =>
      support.handled {
        support.readJsonBody[DissolveClubRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.superAdminService.dissolveClub(
              clubId = ClubId(clubId),
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "admin" / "players" / playerId / "super-admin" =>
      support.handled {
        support.readJsonBody[GrantSuperAdminRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.superAdminService.grantSuperAdmin(
              playerId = PlayerId(playerId),
              actor = support.principal(request.operator)
            )
          )
        }
      }

    case req @ POST -> Root / "admin" / "advanced-stats" / "recompute" =>
      support.handled {
        support.readJsonBody[RecomputeAdvancedStatsRequest](req).flatMap { request =>
          val operator = support.principal(request.operator)
          support.requirePermission(operator, Permission.ManageGlobalDictionary)
          val requestedAt = Instant.now()
          val tasks =
            (request.ownerType, request.ownerId) match
              case (Some("player"), Some(ownerId)) =>
                Vector(
                  support.app.advancedStatsPipelineService.enqueueOwnerRecompute(
                    owner = DashboardOwner.Player(PlayerId(ownerId)),
                    reason = request.reason.getOrElse("manual-targeted-recompute"),
                    requestedAt = requestedAt
                  )
                )
              case (Some("club"), Some(ownerId)) =>
                Vector(
                  support.app.advancedStatsPipelineService.enqueueOwnerRecompute(
                    owner = DashboardOwner.Club(ClubId(ownerId)),
                    reason = request.reason.getOrElse("manual-targeted-recompute"),
                    requestedAt = requestedAt
                  )
                )
              case (Some(other), Some(_)) =>
                throw IllegalArgumentException(s"Unsupported advanced stats ownerType: $other")
              case _ =>
                request.parsedMode match
                  case AdvancedStatsBackfillMode.Full =>
                    support.app.advancedStatsPipelineService.enqueueFullRecompute(
                      requestedAt = requestedAt,
                      reason = request.reason.getOrElse("manual-full-recompute")
                    )
                  case mode =>
                    support.app.advancedStatsPipelineService.enqueueBackfill(
                      mode = mode,
                      requestedAt = requestedAt,
                      reason = request.reason.getOrElse(s"manual-${mode.toString.toLowerCase}-backfill"),
                      limit = request.limit
                    )
          support.jsonResponse(Status.Accepted, tasks)
        }
      }

    case req @ POST -> Root / "admin" / "advanced-stats" / "process" =>
      support.handled {
        support.readJsonBody[ProcessAdvancedStatsTasksRequest](req).flatMap { request =>
          val operator = support.principal(request.operator)
          support.requirePermission(operator, Permission.ManageGlobalDictionary)
          support.jsonResponse(
            Status.Ok,
            support.app.advancedStatsPipelineService.processPending(limit = request.limit, processedAt = Instant.now())
          )
        }
      }
  }
