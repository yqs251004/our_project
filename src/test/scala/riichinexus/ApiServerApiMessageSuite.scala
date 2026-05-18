package riichinexus

import java.time.Instant

import munit.FunSuite
import upickle.default.*

import riichinexus.api.http.{ApiMessageContract, EmptyApiMessageInput}
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.auth.api.messages.AuthApiMessages.*
import riichinexus.microservices.club.api.messages.ClubApiMessages.*
import riichinexus.microservices.club.api.requests.*
import riichinexus.microservices.player.api.messages.PlayerApiMessages.*
import riichinexus.microservices.publicquery.api.messages.PublicQueryApiMessages.*
import riichinexus.microservices.auth.api.requests.*
import riichinexus.microservices.auth.api.responses.*
import riichinexus.microservices.auth.api.responses.AuthResponses.given
import riichinexus.microservices.club.api.responses.*
import riichinexus.microservices.club.api.responses.ClubTournamentResponses.given
import riichinexus.microservices.player.api.requests.CreatePlayerRequest
import riichinexus.microservices.player.api.requests.PlayerRequests.given
import riichinexus.microservices.player.api.responses.*
import riichinexus.microservices.player.api.responses.PlayerResponses.given
import riichinexus.microservices.publicquery.api.responses.*
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.given
import riichinexus.microservices.shared.api.responses.{ErrorResponse, PagedResponse}

class ApiServerApiMessageSuite extends FunSuite with ApiServerSuiteSupport:

  test("auth account messages reuse the existing auth API and preserve /api prefix compatibility") {
    val app = ApplicationContext.inMemory()

    withServer(app) { baseUrl =>
      val registerResponse = postJson(
        s"$baseUrl/authRegisterApiMessage",
        write(RegisterAccountRequest("message-user", "pw-123456", "Message User"))
      )
      assertEquals(registerResponse.statusCode(), 201)
      val registered = read[AuthSuccessView](registerResponse.body())
      assertEquals(registered.username, "message-user")
      assert(registered.token.nonEmpty)

      val loginResponse = postJson(
        s"$baseUrl/api/authLoginApiMessage",
        write(LoginRequest("message-user", "pw-123456"))
      )
      assertEquals(loginResponse.statusCode(), 200)
      val loggedIn = read[AuthSuccessView](loginResponse.body())
      assertEquals(loggedIn.userId, registered.userId)

      val restoreResponse = postJson(
        s"$baseUrl/authRestoreSessionApiMessage",
        write(EmptyApiMessageInput()),
        "Authorization" -> s"Bearer ${loggedIn.token}"
      )
      assertEquals(restoreResponse.statusCode(), 200)
      val restored = read[AuthSessionView](restoreResponse.body())
      assertEquals(restored.userId, registered.userId)
      assert(restored.authenticated)

      val logoutResponse = postJson(
        s"$baseUrl/authLogoutApiMessage",
        write(EmptyApiMessageInput()),
        "Authorization" -> s"Bearer ${loggedIn.token}"
      )
      assertEquals(logoutResponse.statusCode(), 200)
      assertEquals(read[ApiMessage](logoutResponse.body()).message, "Logged out")
    }
  }

  test("auth guest session messages move path and query fields into JSON input") {
    val app = ApplicationContext.inMemory()
    val player = playerService(app).registerPlayer(
      "guest-message-upgrade-player",
      "GuestMessageUpgradePlayer",
      RankSnapshot(RankPlatform.Tenhou, "4-dan"),
      Instant.parse("2026-03-15T08:30:00Z"),
      1500
    )

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/authCreateGuestSessionApiMessage",
        write(CreateGuestSessionRequest(displayName = Some("MessageGuest")))
      )
      assertEquals(createResponse.statusCode(), 201)
      val created = read[GuestAccessSession](createResponse.body())
      assertEquals(created.displayName, "MessageGuest")

      val currentResponse = postJson(
        s"$baseUrl/authCurrentSessionApiMessage",
        write(AuthCurrentSessionApiMessageInput(guestSessionId = Some(created.id.value)))
      )
      assertEquals(currentResponse.statusCode(), 200)
      val current = read[CurrentSessionView](currentResponse.body())
      assertEquals(current.principalKind, SessionPrincipalKind.Guest)
      assertEquals(current.guestSession.map(_.id), Some(created.id))

      val listResponse = postJson(
        s"$baseUrl/authListGuestSessionsApiMessage",
        write(AuthListGuestSessionsApiMessageInput(activeOnly = Some(true), limit = Some(10), offset = Some(0)))
      )
      assertEquals(listResponse.statusCode(), 200)
      val listed = read[PagedResponse[GuestAccessSession]](listResponse.body())
      assertEquals(listed.total, 1)
      assertEquals(listed.items.head.id, created.id)
      assertEquals(listed.appliedFilters, Map("activeOnly" -> "true"))

      val getResponse = postJson(
        s"$baseUrl/authGetGuestSessionApiMessage",
        write(AuthGetGuestSessionApiMessageInput(created.id.value))
      )
      assertEquals(getResponse.statusCode(), 200)
      assertEquals(read[GuestAccessSession](getResponse.body()).id, created.id)

      val upgradeResponse = postJson(
        s"$baseUrl/authUpgradeGuestSessionApiMessage",
        write(AuthUpgradeGuestSessionApiMessageInput(created.id.value, player.id.value))
      )
      assertEquals(upgradeResponse.statusCode(), 200)
      assertEquals(read[GuestAccessSession](upgradeResponse.body()).upgradedToPlayerId, Some(player.id))

      val revocableResponse = postJson(
        s"$baseUrl/authCreateGuestSessionApiMessage",
        write(CreateGuestSessionRequest(displayName = Some("RevocableGuest")))
      )
      assertEquals(revocableResponse.statusCode(), 201)
      val revocable = read[GuestAccessSession](revocableResponse.body())

      val revokeResponse = postJson(
        s"$baseUrl/authRevokeGuestSessionApiMessage",
        write(AuthRevokeGuestSessionApiMessageInput(revocable.id.value, reason = Some("message-test")))
      )
      assertEquals(revokeResponse.statusCode(), 200)
      assert(read[GuestAccessSession](revokeResponse.body()).revokedAt.nonEmpty)
    }
  }


  test("player messages expose create list current and detail flows") {
    val app = ApplicationContext.inMemory()

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/playerCreatePlayerApiMessage",
        write(CreatePlayerRequest("player-message-user", "PlayerMessageUser", "Tenhou", "4-dan", initialElo = 1510))
      )
      assertEquals(createResponse.statusCode(), 201)
      val created = read[PlayerProfileView](createResponse.body())
      assertEquals(created.userId, "player-message-user")

      val currentResponse = postJson(
        s"$baseUrl/playerGetCurrentPlayerApiMessage",
        write(PlayerGetCurrentPlayerApiMessageInput(created.playerId.value))
      )
      assertEquals(currentResponse.statusCode(), 200)
      assertEquals(read[PlayerProfileView](currentResponse.body()).playerId, created.playerId)

      val detailResponse = postJson(
        s"$baseUrl/playerGetPlayerApiMessage",
        write(PlayerGetPlayerApiMessageInput(created.playerId.value))
      )
      assertEquals(detailResponse.statusCode(), 200)
      assertEquals(read[PlayerProfileView](detailResponse.body()).playerId, created.playerId)

      val listResponse = postJson(
        s"$baseUrl/playerListPlayersApiMessage",
        write(PlayerListPlayersApiMessageInput(nickname = Some("message"), limit = Some(5), offset = Some(0)))
      )
      assertEquals(listResponse.statusCode(), 200)
      val page = read[PagedResponse[PlayerProfileView]](listResponse.body())
      assertEquals(page.total, 1)
      assertEquals(page.items.head.playerId, created.playerId)
      assertEquals(page.appliedFilters, Map("nickname" -> "message"))
    }
  }

  test("publicquery messages expose public club directory and detail") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:30:00Z")
    val owner = playerService(app).registerPlayer("public-message-owner", "PublicMessageOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = clubService(app).createClub("Public Message Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val listResponse = postJson(
        s"$baseUrl/publicQueryListClubsApiMessage",
        write(PublicQueryListClubsApiMessageInput(name = Some("message"), limit = Some(10), offset = Some(0)))
      )
      assertEquals(listResponse.statusCode(), 200)
      val clubs = read[PagedResponse[PublicClubDirectoryEntry]](listResponse.body())
      assertEquals(clubs.total, 1)
      assertEquals(clubs.items.head.clubId, club.id)

      val detailResponse = postJson(
        s"$baseUrl/publicQueryGetClubApiMessage",
        write(PublicQueryGetClubApiMessageInput(club.id.value))
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
        s"$baseUrl/clubCreateClubApiMessage",
        write(CreateClubRequest("Message Club", owner.id.value))
      )
      assertEquals(createResponse.statusCode(), 201)
      val club = read[Club](createResponse.body())

      val listResponse = postJson(
        s"$baseUrl/clubListClubsApiMessage",
        write(ClubListClubsApiMessageInput(name = Some("message"), limit = Some(10), offset = Some(0)))
      )
      assertEquals(listResponse.statusCode(), 200)
      assertEquals(read[PagedResponse[Club]](listResponse.body()).items.head.id, club.id)

      val detailResponse = postJson(
        s"$baseUrl/clubGetClubApiMessage",
        write(ClubGetClubApiMessageInput(club.id.value))
      )
      assertEquals(detailResponse.statusCode(), 200)
      assertEquals(read[Club](detailResponse.body()).id, club.id)

      val submitResponse = postJson(
        s"$baseUrl/clubSubmitApplicationApiMessage",
        write(
          ClubSubmitApplicationApiMessageInput(
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
        s"$baseUrl/clubGetCurrentApplicationApiMessage",
        write(ClubGetCurrentApplicationApiMessageInput(club.id.value, operatorId = Some(applicant.id.value)))
      )
      assertEquals(currentResponse.statusCode(), 200)
      assertEquals(read[ClubMembershipApplicationView](currentResponse.body()).applicationId, application.id)

      val applicationsResponse = postJson(
        s"$baseUrl/clubListApplicationsApiMessage",
        write(ClubListApplicationsApiMessageInput(club.id.value, operatorId = Some(owner.id.value), status = Some("Pending")))
      )
      assertEquals(applicationsResponse.statusCode(), 200)
      val applications = read[PagedResponse[ClubMembershipApplicationView]](applicationsResponse.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.applicationId, application.id)

      val detailApplicationResponse = postJson(
        s"$baseUrl/clubGetApplicationApiMessage",
        write(ClubGetApplicationApiMessageInput(club.id.value, application.id.value, operatorId = Some(owner.id.value)))
      )
      assertEquals(detailApplicationResponse.statusCode(), 200)
      assertEquals(read[ClubMembershipApplicationView](detailApplicationResponse.body()).applicationId, application.id)

      val reviewResponse = postJson(
        s"$baseUrl/clubReviewApplicationApiMessage",
        write(
          ClubReviewApplicationApiMessageInput(
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
        s"$baseUrl/clubListMembersApiMessage",
        write(ClubListMembersApiMessageInput(club.id.value, nickname = Some("Applicant")))
      )
      assertEquals(membersResponse.statusCode(), 200)
      val members = read[PagedResponse[PlayerProfileView]](membersResponse.body())
      assertEquals(members.total, 1)
      assertEquals(members.items.head.playerId, applicant.id)
    }
  }

  test("api message registry exports stable auth contracts and unknown messages return contract errors") {
    val app = ApplicationContext.inMemory()

    withServer(app) { baseUrl =>
      val registryResponse = get(s"$baseUrl/api-message-registry.json")
      assertEquals(registryResponse.statusCode(), 200)
      val contracts = read[Vector[ApiMessageContract]](registryResponse.body())
      val byName = contracts.map(contract => contract.messageName -> contract).toMap

      assertEquals(byName("authLoginApiMessage").inputType, "LoginRequest")
      assertEquals(byName("authLoginApiMessage").outputType, "AuthSuccessResponse")
      assertEquals(byName("authLoginApiMessage").ownerService, "auth")
      assertEquals(byName("authCurrentSessionApiMessage").oldRestRoute, "GET /session")
      assertEquals(byName("authUpgradeGuestSessionApiMessage").status, "done")
      assertEquals(byName("playerGetPlayerApiMessage").outputType, "PlayerResponse")
      assertEquals(byName("publicQueryListClubsApiMessage").ownerService, "publicquery")
      assertEquals(byName("clubReviewApplicationApiMessage").outputType, "ClubMembershipApplicationResponse")
      assertEquals(byName("opsAnalyticsPlayerDashboardApiMessage").ownerService, "opsanalytics")
      assertEquals(byName("platformAdminBanPlayerApiMessage").outputType, "PlatformAdminPlayerResponse")
      assertEquals(byName("appealListApiMessage").outputType, "PagedResponse[AppealTicketResponse]")
      assertEquals(byName("tournamentListApiMessage").outputType, "PagedResponse[TournamentSummaryResponse]")
      assertEquals(byName("dictionarySchemaApiMessage").outputType, "GlobalDictionarySchemaView")
      assertEquals(contracts.size, 148)

      val prefixedRegistryResponse = get(s"$baseUrl/api/api-message-registry.json")
      assertEquals(prefixedRegistryResponse.statusCode(), 200)
      assertEquals(read[Vector[ApiMessageContract]](prefixedRegistryResponse.body()).size, contracts.size)

      val unknownResponse = postJson(s"$baseUrl/authMissingApiMessage", "{}")
      assertEquals(unknownResponse.statusCode(), 404)
      val error = read[ErrorResponse](unknownResponse.body())
      assertEquals(error.code, "api_message_not_found")
      assert(error.message.contains("authMissingApiMessage"))
    }
  }
