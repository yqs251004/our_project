package riichinexus

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.{Duration, Instant}

import munit.FunSuite

import riichinexus.api.*
import riichinexus.api.ApiModels.given
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import json.JsonCodecs.given
import upickle.default.*

class FrontendContractSuite extends FunSuite:
  private val client = HttpClient.newHttpClient()

  test("openapi and swagger endpoints expose frontend contract docs") {
    val app = ApplicationContext.inMemory()

    withServer(app) { baseUrl =>
      val openApiResponse = get(s"$baseUrl/openapi.json")
      assertEquals(openApiResponse.statusCode(), 200)
      val openApi = ujson.read(openApiResponse.body())
      val paths = openApi("paths").obj.keySet
      assert(paths.contains("/session"))
      assert(paths.contains("/players/me"))
      assert(paths.contains("/clubs/{clubId}/applications"))
      assert(paths.contains("/clubs/{clubId}/tournaments"))
      assert(paths.contains("/tournaments/{id}"))
      assert(paths.contains("/public/tournaments/{id}"))
      assert(paths.contains("/public/clubs/{clubId}"))

      val swaggerResponse = get(s"$baseUrl/swagger")
      assertEquals(swaggerResponse.statusCode(), 200)
      assert(swaggerResponse.body().contains("/openapi.json"))
      assert(swaggerResponse.body().toLowerCase.contains("swagger-ui"))
    }
  }

  test("session endpoints expose registered, guest and anonymous session views") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-29T09:00:00Z")

    val player = app.playerService.registerPlayer(
      "contract-session-player",
      "ContractSessionPlayer",
      RankSnapshot(RankPlatform.Tenhou, "4-dan"),
      now,
      1620
    )
    val guest = app.guestSessionService.createSession("ContractGuest")

    withServer(app) { baseUrl =>
      val registeredResponse = get(s"$baseUrl/session?operatorId=${player.id.value}")
      assertEquals(registeredResponse.statusCode(), 200)
      val registeredSession = read[CurrentSessionView](registeredResponse.body())
      assertEquals(registeredSession.principalKind, SessionPrincipalKind.RegisteredPlayer)
      assertEquals(registeredSession.player.map(_.id), Some(player.id))
      assert(registeredSession.roles.isRegisteredPlayer)

      val guestResponse = get(s"$baseUrl/session?guestSessionId=${guest.id.value}")
      assertEquals(guestResponse.statusCode(), 200)
      val guestSession = read[CurrentSessionView](guestResponse.body())
      assertEquals(guestSession.principalKind, SessionPrincipalKind.Guest)
      assertEquals(guestSession.guestSession.map(_.id), Some(guest.id))
      assert(guestSession.roles.isGuest)

      val anonymousResponse = get(s"$baseUrl/session")
      assertEquals(anonymousResponse.statusCode(), 200)
      assertEquals(read[CurrentSessionView](anonymousResponse.body()).principalKind, SessionPrincipalKind.Anonymous)

      val meResponse = get(s"$baseUrl/players/me?operatorId=${player.id.value}")
      assertEquals(meResponse.statusCode(), 200)
      assertEquals(read[Player](meResponse.body()).id, player.id)
    }
  }

  test("club application contract follows recruitment policy and supports review flow") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-29T09:30:00Z")

    val owner = app.playerService.registerPlayer("contract-owner", "ContractOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val applicant = app.playerService.registerPlayer("contract-applicant", "ContractApplicant", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560)
    val reserve = app.playerService.registerPlayer("contract-reserve", "ContractReserve", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1490)
    val club = app.clubService.createClub("Contract Club", owner.id, now, owner.asPrincipal)
    app.clubService.addMember(club.id, reserve.id, principalFor(app, owner.id))
    val guest = app.guestSessionService.createSession("ContractGuestApplicant")

    withServer(app) { baseUrl =>
      val closePolicyResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/recruitment-policy",
        write(
          UpdateClubRecruitmentPolicyRequest(
            operatorId = owner.id.value,
            applicationsOpen = false,
            note = Some("closed during review freeze")
          )
        )
      )
      assertEquals(closePolicyResponse.statusCode(), 200)
      assert(!read[Club](closePolicyResponse.body()).recruitmentPolicy.applicationsOpen)

      val closedJoinableResponse = get(s"$baseUrl/clubs?joinableOnly=true")
      assertEquals(closedJoinableResponse.statusCode(), 200)
      assertEquals(readPage[Club](closedJoinableResponse.body()).total, 0)

      val blockedApplicationResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/applications",
        write(
          ClubMembershipApplicationRequest(
            applicantUserId = None,
            displayName = "ignored-by-session",
            message = Some("please let me in"),
            guestSessionId = Some(guest.id.value)
          )
        )
      )
      assertEquals(blockedApplicationResponse.statusCode(), 400)
      assertEquals(read[ApiError](blockedApplicationResponse.body()).code, "invalid_request")

      val openPolicyResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/recruitment-policy",
        write(
          UpdateClubRecruitmentPolicyRequest(
            operatorId = owner.id.value,
            applicationsOpen = true,
            requirementsText = Some("Please share your latest ranked match link."),
            expectedReviewSlaHours = Some(48),
            note = Some("spring recruiting window")
          )
        )
      )
      assertEquals(openPolicyResponse.statusCode(), 200)
      val updatedClub = read[Club](openPolicyResponse.body())
      assert(updatedClub.recruitmentPolicy.applicationsOpen)
      assertEquals(updatedClub.recruitmentPolicy.expectedReviewSlaHours, Some(48))

      val applicationResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/applications",
        write(
          ClubMembershipApplicationRequest(
            applicantUserId = None,
            displayName = "ignored-by-session",
            message = Some("guest-origin contract flow"),
            guestSessionId = Some(guest.id.value)
          )
        )
      )
      assertEquals(applicationResponse.statusCode(), 200)
      val submitted = read[ClubMembershipApplication](applicationResponse.body())

      val joinableResponse = get(s"$baseUrl/clubs?activeOnly=true&joinableOnly=true")
      assertEquals(joinableResponse.statusCode(), 200)
      val joinableClubs = readPage[Club](joinableResponse.body())
      assert(joinableClubs.items.exists(_.id == club.id))

      val inboxResponse = get(
        s"$baseUrl/clubs/${club.id.value}/applications?operatorId=${owner.id.value}&status=Pending&limit=20"
      )
      assertEquals(inboxResponse.statusCode(), 200)
      val inbox = readPage[ClubMembershipApplicationView](inboxResponse.body())
      assertEquals(inbox.total, 1)
      assertEquals(inbox.items.head.applicationId, submitted.id)
      assert(inbox.items.head.canReview)
      assertEquals(inbox.items.head.applicant.applicantUserId, Some(s"guest:${guest.id.value}"))

      val applicantDetailResponse = get(
        s"$baseUrl/clubs/${club.id.value}/applications/${submitted.id.value}?guestSessionId=${guest.id.value}"
      )
      assertEquals(applicantDetailResponse.statusCode(), 200)
      val applicantDetail = read[ClubMembershipApplicationView](applicantDetailResponse.body())
      assert(applicantDetail.canWithdraw)
      assertEquals(applicantDetail.applicant.displayName, "ContractGuestApplicant")

      val reviewResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/applications/${submitted.id.value}/review",
        write(
          ReviewClubApplicationRequest(
            operatorId = owner.id.value,
            decision = "approve",
            playerId = Some(applicant.id.value),
            note = Some("approved after identity verification")
          )
        )
      )
      assertEquals(reviewResponse.statusCode(), 200)
      val reviewed = read[ClubMembershipApplicationView](reviewResponse.body())
      assertEquals(reviewed.status, ClubMembershipApplicationStatus.Approved)
      assertEquals(reviewed.reviewedBy, Some(owner.id))
      assert(!reviewed.canReview)

      val stage = TournamentStage(IdGenerator.stageId(), "Contract Stage", StageFormat.Swiss, 1, 1)
      val tournament = app.tournamentService.createTournament(
        "Contract Cup",
        "QA",
        now,
        now.plusSeconds(7200),
        Vector(stage),
        adminId = Some(owner.id)
      )
      Vector(owner, applicant, reserve).foreach(player =>
        app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, owner.id))
      )
      app.tournamentService.whitelistClub(tournament.id, club.id, principalFor(app, owner.id))
      app.tournamentService.publishTournament(tournament.id, principalFor(app, owner.id))
      app.tournamentService.submitLineup(
        tournament.id,
        stage.id,
        StageLineupSubmission(
          id = IdGenerator.lineupSubmissionId(),
          clubId = club.id,
          submittedBy = owner.id,
          submittedAt = now.plusSeconds(60),
          seats = Vector(
            StageLineupSeat(owner.id, Some(SeatWind.East)),
            StageLineupSeat(applicant.id, Some(SeatWind.South))
          ),
          note = Some("public lineup contract")
        ),
        principalFor(app, owner.id)
      )

      val publicClubResponse = get(s"$baseUrl/public/clubs/${club.id.value}")
      assertEquals(publicClubResponse.statusCode(), 200)
      val publicClub = read[PublicClubDetailView](publicClubResponse.body())
      assertEquals(publicClub.applicationPolicy.applicationsOpen, true)
      assertEquals(publicClub.applicationPolicy.requirementsText, Some("Please share your latest ranked match link."))
      assertEquals(publicClub.applicationPolicy.expectedReviewSlaHours, Some(48))
      assertEquals(publicClub.currentLineup.map(_.playerId).toSet, Set(owner.id, applicant.id))
      assert(!publicClub.currentLineup.exists(_.playerId == reserve.id))
    }
  }

  test("public tournament contract exposes index detail and stage directory") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-29T10:00:00Z")

    val admin = app.playerService.registerPlayer("contract-tournament-admin", "ContractTournamentAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1810)
    val players = Vector(
      admin,
      app.playerService.registerPlayer("contract-tournament-b", "ContractTournamentB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      app.playerService.registerPlayer("contract-tournament-c", "ContractTournamentC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1680),
      app.playerService.registerPlayer("contract-tournament-d", "ContractTournamentD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1660)
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Public Contract Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Public Contract Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )
    players.foreach(player =>
      app.tournamentService.registerPlayer(tournament.id, player.id, principalFor(app, admin.id))
    )
    app.tournamentService.publishTournament(tournament.id, principalFor(app, admin.id))
    app.tournamentService.scheduleStageTables(tournament.id, stage.id, principalFor(app, admin.id))

    withServer(app) { baseUrl =>
      val publicIndexResponse = get(s"$baseUrl/public/tournaments")
      assertEquals(publicIndexResponse.statusCode(), 200)
      val tournamentIndex = readPage[PublicTournamentSummaryView](publicIndexResponse.body())
      assert(tournamentIndex.items.exists(_.tournamentId == tournament.id))

      val stagesResponse = get(s"$baseUrl/tournaments/${tournament.id.value}/stages")
      assertEquals(stagesResponse.statusCode(), 200)
      val stages = read[Vector[TournamentStageDirectoryEntry]](stagesResponse.body())
      assertEquals(stages.map(_.stageId), Vector(stage.id))

      val operationsDetailResponse = get(s"$baseUrl/tournaments/${tournament.id.value}")
      assertEquals(operationsDetailResponse.statusCode(), 200)
      val operationsDetail = read[TournamentDetailView](operationsDetailResponse.body())
      assertEquals(operationsDetail.tournamentId, tournament.id)
      assertEquals(operationsDetail.participatingPlayers.map(_.playerId).toSet, players.map(_.id).toSet)
      assertEquals(operationsDetail.stages.map(_.stageId), Vector(stage.id))
      assertEquals(operationsDetail.stages.head.scheduledTableCount, 1)
      assert(operationsDetail.stages.head.lineupSubmissions.isEmpty)

      val publicDetailResponse = get(s"$baseUrl/public/tournaments/${tournament.id.value}")
      assertEquals(publicDetailResponse.statusCode(), 200)
      val publicDetail = read[PublicTournamentDetailView](publicDetailResponse.body())
      assertEquals(publicDetail.tournamentId, tournament.id)
      assertEquals(publicDetail.stages.map(_.stageId), Vector(stage.id))
      assert(publicDetail.stages.head.standings.nonEmpty)
      assertEquals(publicDetail.stages.head.bracket, None)
    }
  }

  test("creating tournaments without explicit stages still exposes a lineup-ready initial stage") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-29T10:45:00Z")

    val owner = app.playerService.registerPlayer("auto-stage-owner", "AutoStageOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1820)
    val club = app.clubService.createClub("Auto Stage Club", owner.id, now, owner.asPrincipal)
    val lineupMembers = Vector(
      owner,
      app.playerService.registerPlayer("auto-stage-b", "AutoStageB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1710),
      app.playerService.registerPlayer("auto-stage-c", "AutoStageC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1690),
      app.playerService.registerPlayer("auto-stage-d", "AutoStageD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1670)
    )
    lineupMembers.tail.foreach(member =>
      app.clubService.addMember(club.id, member.id, principalFor(app, owner.id))
    )

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/tournaments",
        write(
          CreateTournamentRequest(
            name = "Auto Stage Cup",
            organizer = "QA",
            startsAt = now,
            endsAt = now.plusSeconds(7200),
            stages = Vector.empty,
            adminId = Some(owner.id.value)
          )
        )
      )
      assertEquals(createResponse.statusCode(), 201)
      val created = read[Tournament](createResponse.body())
      assertEquals(created.stages.size, 1)
      assertEquals(created.stages.head.name, "Swiss Stage 1")

      val stageId = created.stages.head.id

      app.tournamentService.whitelistClub(created.id, club.id, principalFor(app, owner.id))
      app.tournamentService.publishTournament(created.id, principalFor(app, owner.id))

      val detailResponse = get(s"$baseUrl/tournaments/${created.id.value}")
      assertEquals(detailResponse.statusCode(), 200)
      val detail = read[TournamentDetailView](detailResponse.body())
      assertEquals(detail.stages.map(_.stageId), Vector(stageId))
      assertEquals(detail.stages.head.name, "Swiss Stage 1")

      val stagesResponse = get(s"$baseUrl/tournaments/${created.id.value}/stages")
      assertEquals(stagesResponse.statusCode(), 200)
      val stages = read[Vector[TournamentStageDirectoryEntry]](stagesResponse.body())
      assertEquals(stages.map(_.stageId), Vector(stageId))
      assertEquals(stages.head.name, "Swiss Stage 1")

      val publicDetailResponse = get(s"$baseUrl/public/tournaments/${created.id.value}")
      assertEquals(publicDetailResponse.statusCode(), 200)
      val publicDetail = read[PublicTournamentDetailView](publicDetailResponse.body())
      assertEquals(publicDetail.stages.map(_.stageId), Vector(stageId))

      val invitedResponse = get(
        s"$baseUrl/clubs/${club.id.value}/tournaments?viewer=${owner.id.value}&scope=all"
      )
      assertEquals(invitedResponse.statusCode(), 200)
      val invited = readPage[ClubTournamentParticipationView](invitedResponse.body())
      assertEquals(invited.items.head.clubParticipationStatus, ClubTournamentParticipationStatus.Invited)
      assert(invited.items.head.canSubmitLineup)

      val lineupResponse = postJson(
        s"$baseUrl/tournaments/${created.id.value}/stages/${stageId.value}/lineups",
        write(
          SubmitStageLineupRequest(
            clubId = club.id.value,
            operatorId = owner.id.value,
            seats = lineupMembers.map(player => StageLineupSeatRequest(player.id.value)),
            note = Some("auto-provisioned stage lineup")
          )
        )
      )
      assertEquals(lineupResponse.statusCode(), 200)
      val lineupMutation = read[TournamentMutationView](lineupResponse.body())
      assertEquals(lineupMutation.tournament.stages.map(_.stageId), Vector(stageId))
      assertEquals(lineupMutation.tournament.stages.head.lineupSubmissions.head.clubId, club.id)
    }
  }

  test("tournament mutation contract returns dedicated detail views for operations endpoints") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-29T11:00:00Z")

    val admin = app.playerService.registerPlayer("ops-admin", "OpsAdmin", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1830)
    val club = app.clubService.createClub("Ops Club", admin.id, now, admin.asPrincipal)
    val lineupMembers = Vector(
      admin,
      app.playerService.registerPlayer("ops-b", "OpsB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700),
      app.playerService.registerPlayer("ops-c", "OpsC", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1680),
      app.playerService.registerPlayer("ops-d", "OpsD", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1660)
    )
    lineupMembers.tail.foreach(member =>
      app.clubService.addMember(club.id, member.id, principalFor(app, admin.id))
    )

    val stage = TournamentStage(IdGenerator.stageId(), "Ops Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Ops Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage),
      adminId = Some(admin.id)
    )

    withServer(app) { baseUrl =>
      val registerClubResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/clubs/${club.id.value}",
        write(OperatorRequest(Some(admin.id.value)))
      )
      assertEquals(registerClubResponse.statusCode(), 200)
      val clubRegistered = read[TournamentMutationView](registerClubResponse.body())
      assertEquals(clubRegistered.tournament.participatingClubs.map(_.clubId), Vector(club.id))

      val publishResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/publish",
        write(OperatorRequest(Some(admin.id.value)))
      )
      assertEquals(publishResponse.statusCode(), 200)
      assertEquals(read[TournamentMutationView](publishResponse.body()).tournament.status, TournamentStatus.RegistrationOpen)

      val lineupResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/lineups",
        write(
          SubmitStageLineupRequest(
            clubId = club.id.value,
            operatorId = admin.id.value,
            seats = lineupMembers.map(player => StageLineupSeatRequest(player.id.value)),
            note = Some("operations contract lineup")
          )
        )
      )
      assertEquals(lineupResponse.statusCode(), 200)
      val lineupMutation = read[TournamentMutationView](lineupResponse.body())
      assertEquals(lineupMutation.tournament.stages.head.lineupSubmissions.size, 1)
      assertEquals(lineupMutation.tournament.stages.head.lineupSubmissions.head.clubId, club.id)

      val scheduleResponse = postJson(
        s"$baseUrl/tournaments/${tournament.id.value}/stages/${stage.id.value}/schedule",
        write(OperatorRequest(Some(admin.id.value)))
      )
      assertEquals(scheduleResponse.statusCode(), 200)
      val scheduled = read[TournamentMutationView](scheduleResponse.body())
      assertEquals(scheduled.scheduledTables.size, 1)
      assertEquals(scheduled.tournament.stages.head.scheduledTableCount, 1)
      assertEquals(scheduled.tournament.whitelistSummary.clubIds, Vector(club.id))
    }
  }

  test("club tournament participation contract exposes invitation and accept-decline lifecycle") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-29T11:30:00Z")

    val owner = app.playerService.registerPlayer("club-tour-owner", "ClubTourOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1810)
    val club = app.clubService.createClub("Participation Club", owner.id, now, owner.asPrincipal)
    val stage = TournamentStage(IdGenerator.stageId(), "Participation Stage", StageFormat.Swiss, 1, 1)
    val tournament = app.tournamentService.createTournament(
      "Participation Cup",
      "QA",
      now,
      now.plusSeconds(5400),
      Vector(stage),
      adminId = Some(owner.id)
    )
    app.tournamentService.whitelistClub(tournament.id, club.id, principalFor(app, owner.id))
    app.tournamentService.publishTournament(tournament.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val invitedResponse = get(
        s"$baseUrl/clubs/${club.id.value}/tournaments?viewer=${owner.id.value}&scope=all"
      )
      assertEquals(invitedResponse.statusCode(), 200)
      val invited = readPage[ClubTournamentParticipationView](invitedResponse.body())
      assertEquals(invited.total, 1)
      assertEquals(invited.items.head.clubParticipationStatus, ClubTournamentParticipationStatus.Invited)
      assert(invited.items.head.canSubmitLineup)

      val acceptResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/tournaments/${tournament.id.value}/accept",
        write(OperatorRequest(Some(owner.id.value)))
      )
      assertEquals(acceptResponse.statusCode(), 200)
      val accepted = read[TournamentMutationView](acceptResponse.body())
      assert(accepted.tournament.participatingClubs.exists(_.clubId == club.id))

      val participatingResponse = get(
        s"$baseUrl/clubs/${club.id.value}/tournaments?viewer=${owner.id.value}&scope=all"
      )
      val participating = readPage[ClubTournamentParticipationView](participatingResponse.body())
      assertEquals(participating.items.head.clubParticipationStatus, ClubTournamentParticipationStatus.Participating)

      val declineResponse = postJson(
        s"$baseUrl/clubs/${club.id.value}/tournaments/${tournament.id.value}/decline",
        write(OperatorRequest(Some(owner.id.value)))
      )
      assertEquals(declineResponse.statusCode(), 200)

      val afterDeclineResponse = get(
        s"$baseUrl/clubs/${club.id.value}/tournaments?viewer=${owner.id.value}&scope=all"
      )
      assertEquals(readPage[ClubTournamentParticipationView](afterDeclineResponse.body()).total, 0)
    }
  }

  test("club tournament participation recent scope excludes stale historical tournaments") {
    val app = ApplicationContext.inMemory()
    val now = Instant.now()

    val owner = app.playerService.registerPlayer("club-tour-recent-owner", "ClubTourRecentOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now.minusSeconds(120), 1810)
    val club = app.clubService.createClub("Recent Participation Club", owner.id, now.minusSeconds(120), owner.asPrincipal)
    val recentStage = TournamentStage(IdGenerator.stageId(), "Recent Participation Stage", StageFormat.Swiss, 1, 1)
    val historicalStage = TournamentStage(IdGenerator.stageId(), "Historical Participation Stage", StageFormat.Swiss, 1, 1)

    val recentTournament = app.tournamentService.createTournament(
      "Recent Participation Cup",
      "QA",
      now.minus(Duration.ofDays(10)),
      now.minus(Duration.ofDays(9)),
      Vector(recentStage),
      adminId = Some(owner.id)
    )
    val historicalTournament = app.tournamentService.createTournament(
      "Historical Participation Cup",
      "QA",
      now.minus(Duration.ofDays(200)),
      now.minus(Duration.ofDays(199)),
      Vector(historicalStage),
      adminId = Some(owner.id)
    )

    app.tournamentService.whitelistClub(recentTournament.id, club.id, principalFor(app, owner.id))
    app.tournamentService.whitelistClub(historicalTournament.id, club.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val recentResponse = get(
        s"$baseUrl/clubs/${club.id.value}/tournaments?viewer=${owner.id.value}&scope=recent"
      )
      assertEquals(recentResponse.statusCode(), 200)
      val recentPage = readPage[ClubTournamentParticipationView](recentResponse.body())
      assertEquals(recentPage.items.map(_.tournamentId), Vector(recentTournament.id))

      val allResponse = get(
        s"$baseUrl/clubs/${club.id.value}/tournaments?viewer=${owner.id.value}&scope=all"
      )
      assertEquals(allResponse.statusCode(), 200)
      val allPage = readPage[ClubTournamentParticipationView](allResponse.body())
      assert(allPage.items.exists(_.tournamentId == recentTournament.id))
      assert(allPage.items.exists(_.tournamentId == historicalTournament.id))
    }
  }

  private def withServer[A](app: ApplicationContext)(f: String => A): A =
    val server = RiichiNexusApiServer(
      app,
      ApiServerConfig(host = "127.0.0.1", port = 0, storageLabel = "memory")
    )

    server.start()
    try f(s"http://127.0.0.1:${server.port}")
    finally server.stop()

  private def get(url: String): HttpResponse[String] =
    client.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .GET()
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )

  private def postJson(url: String, body: String): HttpResponse[String] =
    client.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(body))
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )

  private def principalFor(app: ApplicationContext, playerId: PlayerId): AccessPrincipal =
    app.playerRepository.findById(playerId).getOrElse(fail(s"player ${playerId.value} missing")).asPrincipal

  private def readPage[T: Reader](body: String): PagedResponse[T] =
    read[PagedResponse[T]](body)

