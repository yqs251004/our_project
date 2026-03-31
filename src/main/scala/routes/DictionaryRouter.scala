package routes

import java.time.Instant

import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.Status
import org.http4s.dsl.io.*
import riichinexus.api.ApiModels.given
import riichinexus.api.*
import riichinexus.domain.model.*
import riichinexus.domain.service.GlobalDictionaryRegistry
import riichinexus.infrastructure.json.JsonCodecs.given

object DictionaryRouter:

  def routes(support: RouteSupport): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case req @ GET -> Root / "dictionary" =>
      support.handled {
        val prefixFilter = support.queryParam(req, "prefix").filter(_.nonEmpty)
        val updatedByFilter = support.queryParam(req, "updatedBy").filter(_.nonEmpty).map(PlayerId(_))
        val entries = support.app.globalDictionaryRepository.findAll()
          .filter(entry => prefixFilter.forall(prefix => entry.key.startsWith(prefix)))
          .filter(entry => updatedByFilter.forall(_ == entry.updatedBy))
          .sortBy(_.key)
        support.pagedJsonResponse(req, entries, support.activeFilters(req, "prefix", "updatedBy"))
      }

    case GET -> Root / "dictionary" / "schema" =>
      support.handled(support.jsonResponse(Status.Ok, GlobalDictionaryRegistry.schemaView))

    case req @ GET -> Root / "dictionary" / "namespaces" / "backlog" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val dueSoonHours = support.queryParam(req, "dueSoonHours").filter(_.nonEmpty).map(_.toLong).getOrElse(24L)
        support.jsonResponse(
          Status.Ok,
          support.app.superAdminService.dictionaryNamespaceBacklog(
            actor = operator,
            asOf = asOf,
            dueSoonWindow = java.time.Duration.ofHours(dueSoonHours)
          )
        )
      }

    case req @ GET -> Root / "dictionary" / "namespaces" =>
      support.handled {
        val operator = support.queryPrincipal(req)
        val statusFilter = support.queryParam(req, "status").map(DictionaryNamespaceReviewStatus.valueOf)
        val contextClubFilter = support.queryParam(req, "contextClubId").filter(_.nonEmpty).map(ClubId(_))
        val ownerFilter = support.queryParam(req, "ownerId").filter(_.nonEmpty).map(PlayerId(_))
        val requestedByFilter = support.queryParam(req, "requestedBy").filter(_.nonEmpty).map(PlayerId(_))
        val reviewedByFilter = support.queryParam(req, "reviewedBy").filter(_.nonEmpty).map(PlayerId(_))
        val asOf = support.queryParam(req, "asOf").filter(_.nonEmpty).map(Instant.parse).getOrElse(Instant.now())
        val overdueOnly = support.queryParam(req, "overdueOnly").exists(_.equalsIgnoreCase("true"))
        val dueBefore = support.queryParam(req, "dueBefore").filter(_.nonEmpty).map(Instant.parse)
        val dueAfter = support.queryParam(req, "dueAfter").filter(_.nonEmpty).map(Instant.parse)
        val namespaces = support.app.dictionaryNamespaceRepository.findAll()
          .filter(registration => statusFilter.forall(_ == registration.status))
          .filter(registration => contextClubFilter.forall(clubId => registration.contextClubId.contains(clubId)))
          .filter(registration => ownerFilter.forall(_ == registration.ownerPlayerId))
          .filter(registration => requestedByFilter.forall(_ == registration.requestedBy))
          .filter(registration => reviewedByFilter.forall(reviewer => registration.reviewedBy.contains(reviewer)))
          .filter(registration => !overdueOnly || registration.isPendingOverdue(asOf))
          .filter(registration => dueBefore.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isAfter(bound))))
          .filter(registration => dueAfter.forall(bound => registration.reviewDueAt.exists(dueAt => !dueAt.isBefore(bound))))
          .filter(registration =>
            operator.isSuperAdmin ||
              operator.playerId.exists(registration.hasWriteAccess) ||
              operator.playerId.contains(registration.requestedBy)
          )
        support.pagedJsonResponse(
          req,
          namespaces,
          support.activeFilters(
            req,
            "status",
            "contextClubId",
            "ownerId",
            "requestedBy",
            "reviewedBy",
            "asOf",
            "overdueOnly",
            "dueBefore",
            "dueAfter"
          )
        )
      }

    case req @ POST -> Root / "dictionary" / "namespaces" =>
      support.handled {
        support.readJsonBody[RequestDictionaryNamespaceRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Created,
            support.app.superAdminService.requestDictionaryNamespace(
              namespacePrefix = request.namespacePrefix,
              actor = support.principal(request.operator),
              contextClubId = request.contextClub,
              ownerPlayerId = request.owner,
              coOwnerPlayerIds = request.coOwners,
              editorPlayerIds = request.editors,
              note = request.note,
              reviewDueAt = request.parsedReviewDueAt
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "review" =>
      support.handled {
        support.readJsonBody[ReviewDictionaryNamespaceRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.superAdminService.reviewDictionaryNamespace(
              namespacePrefix = request.namespacePrefix,
              approve = request.approve,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "transfer" =>
      support.handled {
        support.readJsonBody[TransferDictionaryNamespaceRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.superAdminService.transferDictionaryNamespace(
              namespacePrefix = request.namespacePrefix,
              newOwnerId = request.newOwner,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "collaborators" =>
      support.handled {
        support.readJsonBody[UpdateDictionaryNamespaceCollaboratorsRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.superAdminService.updateDictionaryNamespaceCollaborators(
              namespacePrefix = request.namespacePrefix,
              coOwnerPlayerIds = request.coOwners,
              editorPlayerIds = request.editors,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "context" =>
      support.handled {
        support.readJsonBody[UpdateDictionaryNamespaceContextRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.superAdminService.updateDictionaryNamespaceContext(
              namespacePrefix = request.namespacePrefix,
              contextClubId = request.contextClub,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "reminders" / "process" =>
      support.handled {
        support.readJsonBody[ProcessDictionaryNamespaceRemindersRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Ok,
            support.app.superAdminService.processDictionaryNamespaceReminders(
              actor = support.principal(request.operator),
              asOf = request.parsedAsOf.getOrElse(Instant.now()),
              dueSoonWindow = java.time.Duration.ofHours(request.dueSoonHours.toLong),
              reminderInterval = java.time.Duration.ofHours(request.reminderIntervalHours.toLong),
              escalationGrace = java.time.Duration.ofHours(request.escalationGraceHours.toLong)
            )
          )
        }
      }

    case req @ POST -> Root / "dictionary" / "namespaces" / "revoke" =>
      support.handled {
        support.readJsonBody[RevokeDictionaryNamespaceRequest](req).flatMap { request =>
          support.optionJsonResponse(
            support.app.superAdminService.revokeDictionaryNamespace(
              namespacePrefix = request.namespacePrefix,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }

    case GET -> Root / "dictionary" / key =>
      support.handled(support.optionJsonResponse(support.app.globalDictionaryRepository.findByKey(key)))

    case req @ POST -> Root / "admin" / "dictionary" =>
      support.handled {
        support.readJsonBody[UpsertDictionaryRequest](req).flatMap { request =>
          support.jsonResponse(
            Status.Created,
            support.app.superAdminService.upsertDictionary(
              key = request.key,
              value = request.value,
              actor = support.principal(request.operator),
              note = request.note
            )
          )
        }
      }
  }
