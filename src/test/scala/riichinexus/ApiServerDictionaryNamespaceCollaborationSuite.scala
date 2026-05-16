package riichinexus

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

import munit.FunSuite

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.api.requests.CreateGuestSessionRequest
import riichinexus.microservices.club.api.requests.*
import riichinexus.microservices.club.api.responses.*
import riichinexus.microservices.club.api.responses.ClubTournamentResponses.given
import riichinexus.microservices.dictionary.api.requests.*
import riichinexus.microservices.dictionary.api.responses.*
import riichinexus.microservices.dictionary.api.responses.DictionaryResponses.given
import riichinexus.microservices.opsanalytics.api.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.opsanalytics.api.requests.{ProcessAdvancedStatsTasksRequest, RecomputeAdvancedStatsRequest}
import riichinexus.microservices.tournament.appeal.api.requests.*
import upickle.default.*

class ApiServerDictionaryNamespaceCollaborationSuite extends FunSuite with ApiServerSuiteSupport:
  test("dictionary namespace collaborators can write and reminders can be processed over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:35:00Z")
    val requestNow = Instant.now()

    val root = playerService(app).registerPlayer("ns-collab-root", "NsCollabRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = playerService(app).registerPlayer("ns-collab-owner", "NsCollabOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val coOwner = playerService(app).registerPlayer("ns-collab-coowner", "NsCollabCoOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val editor = playerService(app).registerPlayer("ns-collab-editor", "NsCollabEditor", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val replacementEditor = playerService(app).registerPlayer("ns-collab-replacement", "NsCollabReplacement", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1570)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

    withServer(app) { baseUrl =>
      val requestResponse = postJson(
        s"$baseUrl/dictionary/namespaces",
        write(
          RequestDictionaryNamespaceRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.banner",
            coOwnerPlayerIds = Vector(coOwner.id.value),
            editorPlayerIds = Vector(editor.id.value),
            note = Some("frontend banners")
          )
        )
      )
      assertEquals(requestResponse.statusCode(), 201)
      val pending = read[DictionaryNamespaceRegistration](requestResponse.body())
      assertEquals(pending.coOwnerPlayerIds, Vector(coOwner.id))
      assertEquals(pending.editorPlayerIds, Vector(editor.id))

      val reviewResponse = postJson(
        s"$baseUrl/dictionary/namespaces/review",
        write(
          ReviewDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            approve = true,
            note = Some("approved")
          )
        )
      )
      assertEquals(reviewResponse.statusCode(), 200)

      val editorWrite = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = editor.id.value,
            key = "ui.banner.message",
            value = "editor managed copy",
            note = Some("editor write")
          )
        )
      )
      assertEquals(editorWrite.statusCode(), 201)

      val collaboratorUpdate = postJson(
        s"$baseUrl/dictionary/namespaces/collaborators",
        write(
          UpdateDictionaryNamespaceCollaboratorsRequest(
            operatorId = coOwner.id.value,
            namespacePrefix = "ui.banner",
            coOwnerPlayerIds = Vector(coOwner.id.value),
            editorPlayerIds = Vector(replacementEditor.id.value),
            note = Some("rotate editor")
          )
        )
      )
      assertEquals(collaboratorUpdate.statusCode(), 200)
      val updated = read[DictionaryNamespaceRegistration](collaboratorUpdate.body())
      assertEquals(updated.editorPlayerIds, Vector(replacementEditor.id))

      val oldEditorDenied = postJson(
        s"$baseUrl/admin/dictionary",
        write(
          UpsertDictionaryRequest(
            operatorId = editor.id.value,
            key = "ui.banner.message",
            value = "old editor blocked",
            note = Some("should fail")
          )
        )
      )
      assertEquals(oldEditorDenied.statusCode(), 400)

      val reminderRequest = postJson(
        s"$baseUrl/dictionary/namespaces",
        write(
          RequestDictionaryNamespaceRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.alert",
            note = Some("due-soon reminder"),
            reviewDueAt = Some(requestNow.plusSeconds(3600).toString)
          )
        )
      )
      assertEquals(reminderRequest.statusCode(), 201)

      val reminderProcess = postJson(
        s"$baseUrl/dictionary/namespaces/reminders/process",
        write(
          ProcessDictionaryNamespaceRemindersRequest(
            operatorId = superAdmin.id.value,
            asOf = Some(requestNow.plusSeconds(1800).toString),
            dueSoonHours = 2,
            reminderIntervalHours = 12,
            escalationGraceHours = 72
          )
        )
      )
      assertEquals(reminderProcess.statusCode(), 200)
      val reminderActions = read[Vector[DictionaryNamespaceReminderAction]](reminderProcess.body())
      assert(reminderActions.exists(action => action.namespacePrefix == "ui.alert." && action.reminderKind == DictionaryNamespaceReminderKind.DueSoon))
    }
  }

  test("dictionary namespace context club endpoints enforce explicit club scoping over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:37:00Z")

    val root = playerService(app).registerPlayer("ns-context-root", "NsContextRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = playerService(app).registerPlayer("ns-context-owner", "NsContextOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val coOwner = playerService(app).registerPlayer("ns-context-coowner", "NsContextCoOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val editor = playerService(app).registerPlayer("ns-context-editor", "NsContextEditor", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val outsider = playerService(app).registerPlayer("ns-context-outsider", "NsContextOutsider", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1570)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

    val club = clubService(app).createClub("API Namespace Context Club", owner.id, now, owner.asPrincipal)
    clubService(app).addMember(club.id, coOwner.id, principalFor(app, owner.id))
    clubService(app).addMember(club.id, editor.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val requestResponse = postJson(
        s"$baseUrl/dictionary/namespaces",
        write(
          RequestDictionaryNamespaceRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.banner",
            contextClubId = Some(club.id.value),
            coOwnerPlayerIds = Vector(coOwner.id.value),
            editorPlayerIds = Vector(editor.id.value),
            note = Some("club-scoped metadata")
          )
        )
      )
      assertEquals(requestResponse.statusCode(), 201)
      val pending = read[DictionaryNamespaceRegistration](requestResponse.body())
      assertEquals(pending.contextClubId, Some(club.id))

      val reviewResponse = postJson(
        s"$baseUrl/dictionary/namespaces/review",
        write(
          ReviewDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            approve = true,
            note = Some("approved")
          )
        )
      )
      assertEquals(reviewResponse.statusCode(), 200)

      val filteredList = get(
        s"$baseUrl/dictionary/namespaces?operatorId=${superAdmin.id.value}&status=Approved&contextClubId=${club.id.value}"
      )
      assertEquals(filteredList.statusCode(), 200)
      val filteredPage = readPage[DictionaryNamespaceRegistration](filteredList.body())
      assertEquals(filteredPage.total, 1)
      assertEquals(filteredPage.items.head.contextClubId, Some(club.id))

      val invalidCollaborators = postJson(
        s"$baseUrl/dictionary/namespaces/collaborators",
        write(
          UpdateDictionaryNamespaceCollaboratorsRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.banner",
            coOwnerPlayerIds = Vector(coOwner.id.value),
            editorPlayerIds = Vector(outsider.id.value),
            note = Some("should fail")
          )
        )
      )
      assertEquals(invalidCollaborators.statusCode(), 400)
      assert(invalidCollaborators.body().contains(club.id.value))

      val transferDenied = postJson(
        s"$baseUrl/dictionary/namespaces/transfer",
        write(
          TransferDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            newOwnerPlayerId = outsider.id.value,
            note = Some("should fail")
          )
        )
      )
      assertEquals(transferDenied.statusCode(), 400)
      assert(transferDenied.body().contains(club.id.value))

      val clearContext = postJson(
        s"$baseUrl/dictionary/namespaces/context",
        write(
          UpdateDictionaryNamespaceContextRequest(
            operatorId = owner.id.value,
            namespacePrefix = "ui.banner",
            contextClubId = None,
            note = Some("detach team context")
          )
        )
      )
      assertEquals(clearContext.statusCode(), 200)
      val cleared = read[DictionaryNamespaceRegistration](clearContext.body())
      assertEquals(cleared.contextClubId, None)

      val transferResponse = postJson(
        s"$baseUrl/dictionary/namespaces/transfer",
        write(
          TransferDictionaryNamespaceRequest(
            operatorId = superAdmin.id.value,
            namespacePrefix = "ui.banner",
            newOwnerPlayerId = outsider.id.value,
            note = Some("handoff after detach")
          )
        )
      )
      assertEquals(transferResponse.statusCode(), 200)
    }
  }
