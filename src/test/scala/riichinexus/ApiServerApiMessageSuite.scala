package riichinexus

import java.time.Instant

import munit.FunSuite
import upickle.default.*

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.api.*
import riichinexus.microservices.club.api.*
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.dictionary.api.*
import riichinexus.microservices.tournament.appeal.api.*
import riichinexus.microservices.player.api.*
import riichinexus.microservices.platformadmin.api.*
import riichinexus.microservices.publicquery.api.*
import riichinexus.microservices.auth.objects.apiTypes.*
import riichinexus.microservices.auth.objects.apiTypes.AuthResponses.given
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import riichinexus.microservices.tournament.appeal.objects.apiTypes.TournamentAppealResponses.given
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.microservices.player.objects.apiTypes.PlayerResponses.given
import riichinexus.microservices.platformadmin.objects.apiTypes.*
import riichinexus.microservices.platformadmin.objects.apiTypes.PlatformAdminResponses.given
import riichinexus.microservices.publicquery.objects.apiTypes.*
import riichinexus.microservices.publicquery.objects.apiTypes.PublicQueryResponses.given
import riichinexus.system.objects.apiTypes.{ErrorResponse, PagedResponse}

class ApiServerApiMessageSuite extends FunSuite with ApiServerSuiteSupport:

  test("auth API classes expose class-derived /api routes") {
    val app = ApplicationContext.inMemory()

    withServer(app) { baseUrl =>
      val registerResponse = postJson(
        s"$baseUrl/api/registerauthapi",
        write(RegisterAuthAPIMessage("class-user", "pw-123456", "Class User"))
      )
      assertEquals(registerResponse.statusCode(), 201)
      val registered = read[AuthSuccessView](registerResponse.body())
      assertEquals(registered.username, "class-user")

      val loginResponse = postJson(
        s"$baseUrl/api/loginauthapi",
        write(LoginAuthAPIMessage("class-user", "pw-123456"))
      )
      assertEquals(loginResponse.statusCode(), 200)
      val loggedIn = read[AuthSuccessView](loginResponse.body())
      assertEquals(loggedIn.userId, registered.userId)

      val restoreResponse = postJson(
        s"$baseUrl/api/restoreauthsessionapi",
        write(RestoreAuthSessionAPIMessage()),
        "Authorization" -> s"Bearer ${loggedIn.token}"
      )
      assertEquals(restoreResponse.statusCode(), 200)
      val restored = read[AuthSessionView](restoreResponse.body())
      assertEquals(restored.userId, registered.userId)
      assert(restored.authenticated)

      val logoutResponse = postJson(
        s"$baseUrl/api/logoutauthapi",
        write(LogoutAuthAPIMessage()),
        "Authorization" -> s"Bearer ${loggedIn.token}"
      )
      assertEquals(logoutResponse.statusCode(), 200)
      assertEquals(read[ApiMessage](logoutResponse.body()).message, "Logged out")
    }
  }

  test("auth guest session API classes expose JSON-only inputs") {
    val app = ApplicationContext.inMemory()
    val player = playerService(app).registerPlayer(
      "guest-class-upgrade-player",
      "GuestClassUpgradePlayer",
      RankSnapshot(RankPlatform.Tenhou, "4-dan"),
      Instant.parse("2026-03-15T08:30:00Z"),
      1500
    )

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/api/createguestsessionauthapi",
        write(CreateGuestSessionAuthAPIMessage(displayName = Some("ClassGuest")))
      )
      assertEquals(createResponse.statusCode(), 201)
      val created = read[GuestAccessSession](createResponse.body())
      assertEquals(created.displayName, "ClassGuest")

      val currentResponse = postJson(
        s"$baseUrl/api/currentsessionauthapi",
        write(CurrentSessionAuthAPIMessage(guestSessionId = Some(created.id.value)))
      )
      assertEquals(currentResponse.statusCode(), 200)
      val current = read[CurrentSessionView](currentResponse.body())
      assertEquals(current.principalKind, SessionPrincipalKind.Guest)
      assertEquals(current.guestSession.map(_.id), Some(created.id))

      val listResponse = postJson(
        s"$baseUrl/api/listguestsessionsauthapi",
        write(ListGuestSessionsAuthAPIMessage(activeOnly = Some(true), limit = Some(10), offset = Some(0)))
      )
      assertEquals(listResponse.statusCode(), 200)
      val listed = read[PagedResponse[GuestAccessSession]](listResponse.body())
      assertEquals(listed.total, 1)
      assertEquals(listed.items.head.id, created.id)

      val getResponse = postJson(
        s"$baseUrl/api/getguestsessionauthapi",
        write(GetGuestSessionAuthAPIMessage(created.id.value))
      )
      assertEquals(getResponse.statusCode(), 200)
      assertEquals(read[GuestAccessSession](getResponse.body()).id, created.id)

      val upgradeResponse = postJson(
        s"$baseUrl/api/upgradeguestsessionauthapi",
        write(UpgradeGuestSessionAuthAPIMessage(created.id.value, player.id.value))
      )
      assertEquals(upgradeResponse.statusCode(), 200)
      assertEquals(read[GuestAccessSession](upgradeResponse.body()).upgradedToPlayerId, Some(player.id))

      val revocableResponse = postJson(
        s"$baseUrl/api/createguestsessionauthapi",
        write(CreateGuestSessionAuthAPIMessage(displayName = Some("ClassRevocableGuest")))
      )
      assertEquals(revocableResponse.statusCode(), 201)
      val revocable = read[GuestAccessSession](revocableResponse.body())

      val revokeResponse = postJson(
        s"$baseUrl/api/revokeguestsessionauthapi",
        write(RevokeGuestSessionAuthAPIMessage(revocable.id.value, reason = Some("class-test")))
      )
      assertEquals(revokeResponse.statusCode(), 200)
      assert(read[GuestAccessSession](revokeResponse.body()).revokedAt.nonEmpty)
    }
  }

  test("player API classes expose create list current and detail flows") {
    val app = ApplicationContext.inMemory()

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/api/createplayerapi",
        write(CreatePlayerAPIMessage("player-message-user", "PlayerMessageUser", "Tenhou", "4-dan", initialElo = 1510))
      )
      assertEquals(createResponse.statusCode(), 201)
      val created = read[PlayerProfileView](createResponse.body())
      assertEquals(created.userId, "player-message-user")

      val currentResponse = postJson(
        s"$baseUrl/api/getcurrentplayerapi",
        write(GetCurrentPlayerAPIMessage(created.playerId.value))
      )
      assertEquals(currentResponse.statusCode(), 200)
      assertEquals(read[PlayerProfileView](currentResponse.body()).playerId, created.playerId)

      val detailResponse = postJson(
        s"$baseUrl/api/getplayerapi",
        write(GetPlayerAPIMessage(created.playerId.value))
      )
      assertEquals(detailResponse.statusCode(), 200)
      assertEquals(read[PlayerProfileView](detailResponse.body()).playerId, created.playerId)

      val listResponse = postJson(
        s"$baseUrl/api/listplayersapi",
        write(ListPlayersAPIMessage(nickname = Some("message"), limit = Some(5), offset = Some(0)))
      )
      assertEquals(listResponse.statusCode(), 200)
      val page = read[PagedResponse[PlayerProfileView]](listResponse.body())
      assertEquals(page.total, 1)
      assertEquals(page.items.head.playerId, created.playerId)
      assertEquals(page.appliedFilters, Map("nickname" -> "message"))
    }
  }

  test("publicquery API classes expose public club directory and detail") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:30:00Z")
    val owner = playerService(app).registerPlayer("public-message-owner", "PublicMessageOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = clubApi(app).createClub("Public Message Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val listResponse = postJson(
        s"$baseUrl/api/listpublicclubsapi",
        write(ListPublicClubsAPIMessage(name = Some("message"), limit = Some(10), offset = Some(0)))
      )
      assertEquals(listResponse.statusCode(), 200)
      val clubs = read[PagedResponse[PublicClubDirectoryEntry]](listResponse.body())
      assertEquals(clubs.total, 1)
      assertEquals(clubs.items.head.clubId, club.id)

      val detailResponse = postJson(
        s"$baseUrl/api/getpublicclubapi",
        write(GetPublicClubAPIMessage(club.id.value))
      )
      assertEquals(detailResponse.statusCode(), 200)
      val detail = read[PublicClubDetailView](detailResponse.body())
      assertEquals(detail.clubId, club.id)
      assertEquals(detail.name, "Public Message Club")
    }
  }

  test("club messages cover core club and application workflows") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:30:00Z")
    val owner = playerService(app).registerPlayer("club-message-owner", "ClubMessageOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val applicant = playerService(app).registerPlayer("club-message-applicant", "ClubMessageApplicant", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/api/createclubapi",
        write(CreateClubAPIMessage("Message Club", owner.id.value))
      )
      assertEquals(createResponse.statusCode(), 201)
      val club = read[Club](createResponse.body())

      val listResponse = postJson(
        s"$baseUrl/api/listclubsapi",
        write(ListClubsAPIMessage(name = Some("message"), limit = Some(10), offset = Some(0)))
      )
      assertEquals(listResponse.statusCode(), 200)
      assertEquals(read[PagedResponse[Club]](listResponse.body()).items.head.id, club.id)

      val detailResponse = postJson(
        s"$baseUrl/api/getclubapi",
        write(GetClubAPIMessage(club.id.value))
      )
      assertEquals(detailResponse.statusCode(), 200)
      assertEquals(read[Club](detailResponse.body()).id, club.id)

      val submitResponse = postJson(
        s"$baseUrl/api/submitclubapplicationapi",
        write(
          SubmitClubApplicationAPIMessage(
            clubId = club.id.value,
            applicantUserId = None,
            displayName = "ignored",
            message = Some("message application"),
            operatorId = Some(applicant.id.value)
          )
        )
      )
      assertEquals(submitResponse.statusCode(), 200)
      val application = read[ClubMembershipApplication](submitResponse.body())

      val currentResponse = postJson(
        s"$baseUrl/api/getcurrentclubapplicationapi",
        write(GetCurrentClubApplicationAPIMessage(club.id.value, operatorId = Some(applicant.id.value)))
      )
      assertEquals(currentResponse.statusCode(), 200)
      assertEquals(read[ClubMembershipApplicationView](currentResponse.body()).applicationId, application.id)

      val applicationsResponse = postJson(
        s"$baseUrl/api/listclubapplicationsapi",
        write(ListClubApplicationsAPIMessage(club.id.value, operatorId = Some(owner.id.value), status = Some("Pending")))
      )
      assertEquals(applicationsResponse.statusCode(), 200)
      val applications = read[PagedResponse[ClubMembershipApplicationView]](applicationsResponse.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.applicationId, application.id)

      val detailApplicationResponse = postJson(
        s"$baseUrl/api/getclubapplicationapi",
        write(GetClubApplicationAPIMessage(club.id.value, application.id.value, operatorId = Some(owner.id.value)))
      )
      assertEquals(detailApplicationResponse.statusCode(), 200)
      assertEquals(read[ClubMembershipApplicationView](detailApplicationResponse.body()).applicationId, application.id)

      val reviewResponse = postJson(
        s"$baseUrl/api/reviewclubapplicationapi",
        write(
          ReviewClubApplicationAPIMessage(
            clubId = club.id.value,
            membershipId = application.id.value,
            operatorId = owner.id.value,
            decision = "approve",
            playerId = Some(applicant.id.value)
          )
        )
      )
      assertEquals(reviewResponse.statusCode(), 200)
      assertEquals(read[ClubMembershipApplicationView](reviewResponse.body()).status, ClubMembershipApplicationStatus.Approved)

      val membersResponse = postJson(
        s"$baseUrl/api/listclubmembersapi",
        write(ListClubMembersAPIMessage(club.id.value, nickname = Some("Applicant")))
      )
      assertEquals(membersResponse.statusCode(), 200)
      val members = read[PagedResponse[PlayerProfileView]](membersResponse.body())
      assertEquals(members.total, 1)
      assertEquals(members.items.head.playerId, applicant.id)
    }
  }

  test("platform admin API classes expose moderation and role flows") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:30:00Z")
    val operator = playerService(app).registerPlayer("platform-admin-operator", "PlatformAdminOperator", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val target = playerService(app).registerPlayer("platform-admin-target", "PlatformAdminTarget", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    playerRepository(app).save(operator.grantRole(RoleGrant.superAdmin(now)))

    withServer(app) { baseUrl =>
      val grantResponse = postJson(
        s"$baseUrl/api/platformadmingrantsuperadminapi",
        write(PlatformAdminGrantSuperAdminAPIMessage(target.id, operator.id))
      )
      assertEquals(grantResponse.statusCode(), 200)
      assertEquals(read[PlatformAdminPlayerView](grantResponse.body()).playerId, target.id)

      val banResponse = postJson(
        s"$baseUrl/api/platformadminbanplayerapi",
        write(PlatformAdminBanPlayerAPIMessage(target.id, operator.id, "message moderation"))
      )
      assertEquals(banResponse.statusCode(), 200)
      val banned = read[PlatformAdminPlayerView](banResponse.body())
      assertEquals(banned.playerId, target.id)
      assertEquals(banned.bannedReason, Some("message moderation"))
    }
  }

  test("dictionary API classes expose schema and entry workflows") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:30:00Z")
    val operator = playerService(app).registerPlayer("dictionary-message-operator", "DictionaryMessageOperator", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    playerRepository(app).save(operator.grantRole(RoleGrant.superAdmin(now)))

    withServer(app) { baseUrl =>
      val schemaResponse = postJson(
        s"$baseUrl/api/dictionaryschemaapi",
        write(DictionarySchemaAPIMessage())
      )
      assertEquals(schemaResponse.statusCode(), 200)
      assert(read[GlobalDictionarySchemaView](schemaResponse.body()).entries.nonEmpty)

      val upsertResponse = postJson(
        s"$baseUrl/api/dictionaryupsertentryapi",
        write(DictionaryUpsertEntryAPIMessage(operator.id.value, "settlement.defaultPayoutRatios", "0.6,0.25,0.15", Some("message dictionary")))
      )
      assertEquals(upsertResponse.statusCode(), 201)
      val upserted = read[GlobalDictionaryEntry](upsertResponse.body())
      assertEquals(upserted.key, "settlement.defaultPayoutRatios")

      val getResponse = postJson(
        s"$baseUrl/api/dictionarygetentryapi",
        write(DictionaryGetEntryAPIMessage("settlement.defaultPayoutRatios"))
      )
      assertEquals(getResponse.statusCode(), 200)
      assertEquals(read[GlobalDictionaryEntry](getResponse.body()).value, "0.6,0.25,0.15")

      val listResponse = postJson(
        s"$baseUrl/api/dictionarylistentriesapi",
        write(DictionaryListEntriesAPIMessage(prefix = Some("settlement."), limit = Some(10), offset = Some(0)))
      )
      assertEquals(listResponse.statusCode(), 200)
      assertEquals(read[PagedResponse[GlobalDictionaryEntry]](listResponse.body()).items.head.key, "settlement.defaultPayoutRatios")
    }
  }

  test("tournament appeal API classes expose file list and workflow routes") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T10:20:00Z")

    val admin = playerService(app).registerPlayer("appeal-api-admin", "AppealApiAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1810)
    val players = Vector(
      admin,
      playerService(app).registerPlayer("appeal-api-b", "AppealApiB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      playerService(app).registerPlayer("appeal-api-c", "AppealApiC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600),
      playerService(app).registerPlayer("appeal-api-d", "AppealApiD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    )
    val stage = TournamentStage(IdGenerator.stageId(), "API Appeal Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "API Appeal Cup",
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
    val openerId = table.seats.head.playerId

    withServer(app) { baseUrl =>
      val fileResponse = postJson(
        s"$baseUrl/api/appealfileapi",
        write(AppealFileAPIMessage(table.id.value, openerId.value, "disconnect happened"))
      )
      assertEquals(fileResponse.statusCode(), 200)
      val filed = read[AppealTicketView](fileResponse.body())
      assertEquals(filed.tableId, table.id)
      assertEquals(filed.status, AppealStatus.Open)

      val workflowDueAt = Instant.now().plusSeconds(600)
      val workflowResponse = postJson(
        s"$baseUrl/api/appealupdateworkflowapi",
        write(AppealUpdateWorkflowAPIMessage(filed.appealId.value, admin.id.value, assigneeId = Some(admin.id.value), priority = Some("Critical"), dueAt = Some(workflowDueAt.toString)))
      )
      assertEquals(workflowResponse.statusCode(), 200)
      val triaged = read[AppealTicketView](workflowResponse.body())
      assertEquals(triaged.assigneeId, Some(admin.id))
      assertEquals(triaged.priority, AppealPriority.Critical)

      val listResponse = postJson(
        s"$baseUrl/api/appeallistapi",
        write(AppealListAPIMessage(priority = Some("Critical"), assigneeId = Some(admin.id.value), overdueOnly = Some(true), asOf = Some(workflowDueAt.plusSeconds(60).toString)))
      )
      assertEquals(listResponse.statusCode(), 200)
      val page = read[PagedResponse[AppealTicketView]](listResponse.body())
      assertEquals(page.total, 1)
      assertEquals(page.items.head.appealId, filed.appealId)

      val getResponse = postJson(
        s"$baseUrl/api/appealgetapi",
        write(AppealGetAPIMessage(filed.appealId.value))
      )
      assertEquals(getResponse.statusCode(), 200)
      assertEquals(read[AppealTicketView](getResponse.body()).appealId, filed.appealId)
    }
  }

  test("legacy api message registry routes are removed and unknown class API routes return contract errors") {
    val app = ApplicationContext.inMemory()

    withServer(app) { baseUrl =>
      val registryResponse = get(s"$baseUrl/api-message-registry.json")
      assertEquals(registryResponse.statusCode(), 404)

      val prefixedRegistryResponse = get(s"$baseUrl/api/api-message-registry.json")
      assertEquals(prefixedRegistryResponse.statusCode(), 404)

      val unknownResponse = postJson(s"$baseUrl/api/authmissingapi", "{}")
      assertEquals(unknownResponse.statusCode(), 404)
      val error = read[ErrorResponse](unknownResponse.body())
      assertEquals(error.code, "api_not_found")
      assert(error.message.contains("authmissingapi"))

    }
  }
