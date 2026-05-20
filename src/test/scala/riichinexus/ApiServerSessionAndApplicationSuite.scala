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
import riichinexus.microservices.auth.api.*
import riichinexus.microservices.club.api.*
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.DictionaryResponses.given
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.tournament.appeal.objects.apiTypes.*
import upickle.default.*

class ApiServerSessionAndApplicationSuite extends FunSuite with ApiServerSuiteSupport:

  test("guest session endpoints support anonymous club applications") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:30:00Z")

    val owner = playerService(app).registerPlayer("guest-api-owner", "GuestApiOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = clubApi(app).createClub("Guest API Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/api/createguestsessionauthapi",
        write(CreateGuestSessionAuthAPIMessage(displayName = Some("AnonymousFan")))
      )
      assertEquals(createResponse.statusCode(), 201)
      val session = read[GuestAccessSession](createResponse.body())
      assertEquals(session.displayName, "AnonymousFan")

      val detailResponse = postJson(
        s"$baseUrl/api/getguestsessionauthapi",
        write(GetGuestSessionAuthAPIMessage(session.id.value))
      )
      assertEquals(detailResponse.statusCode(), 200)
      assertEquals(read[GuestAccessSession](detailResponse.body()).id, session.id)

      val applicationResponse = postJson(
        s"$baseUrl/api/submitclubapplicationapi",
        write(
          SubmitClubApplicationAPIMessage(
            clubId = club.id.value,
            applicantUserId = None,
            displayName = "ignored-by-session",
            message = Some("guest route test"),
            guestSessionId = Some(session.id.value)
          )
        )
      )
      assertEquals(applicationResponse.statusCode(), 200)
      val application = read[ClubMembershipApplication](applicationResponse.body())
      assertEquals(application.displayName, "AnonymousFan")
      assertEquals(application.applicantUserId, Some(s"guest:${session.id.value}"))

      val listResponse = postJson(
        s"$baseUrl/api/listclubapplicationsapi",
        write(ListClubApplicationsAPIMessage(club.id.value, operatorId = Some(owner.id.value)))
      )
      assertEquals(listResponse.statusCode(), 200)
      val applications = readPage[ClubMembershipApplicationView](listResponse.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.applicationId, application.id)
    }
  }

  test("registered players can submit and withdraw club applications over the API") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:45:00Z")

    val owner = playerService(app).registerPlayer("api-withdraw-owner", "ApiWithdrawOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val applicant = playerService(app).registerPlayer("api-withdraw-player", "ApiWithdrawPlayer", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val club = clubApi(app).createClub("Withdraw API Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val createResponse = postJson(
        s"$baseUrl/api/submitclubapplicationapi",
        write(
          SubmitClubApplicationAPIMessage(
            clubId = club.id.value,
            applicantUserId = None,
            displayName = "fallback-name",
            message = Some("registered path"),
            guestSessionId = None,
            operatorId = Some(applicant.id.value)
          )
        )
      )
      assertEquals(createResponse.statusCode(), 200)
      val created = read[ClubMembershipApplication](createResponse.body())
      assertEquals(created.applicantUserId, Some(applicant.userId))
      assertEquals(created.displayName, applicant.nickname)

      val withdrawResponse = postJson(
        s"$baseUrl/api/withdrawclubapplicationapi",
        write(WithdrawClubApplicationAPIMessage(club.id.value, created.id.value, operatorId = Some(applicant.id.value), note = Some("not this season")))
      )
      assertEquals(withdrawResponse.statusCode(), 200)
      val withdrawn = read[ClubMembershipApplication](withdrawResponse.body())
      assertEquals(withdrawn.status, ClubMembershipApplicationStatus.Withdrawn)
      assertEquals(withdrawn.withdrawnByPrincipalId, Some(applicant.id.value))

      val filteredResponse = postJson(
        s"$baseUrl/api/listclubapplicationsapi",
        write(ListClubApplicationsAPIMessage(club.id.value, operatorId = Some(owner.id.value), status = Some("Withdrawn"), applicantUserId = Some(applicant.userId)))
      )
      assertEquals(filteredResponse.statusCode(), 200)
      val applications = readPage[ClubMembershipApplicationView](filteredResponse.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.applicationId, created.id)
    }
  }

  test("club applications endpoint returns submitted applications") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T08:00:00Z")

    val owner = playerService(app).registerPlayer("owner-1", "Owner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = clubApi(app).createClub("Application Club", owner.id, now, owner.asPrincipal)

    clubApi(app).applyForMembership(
      clubId = club.id,
      applicantUserId = Some("guest-1"),
      displayName = "Guest Applicant",
      message = Some("let me in")
    )

    withServer(app) { baseUrl =>
      val response = postJson(
        s"$baseUrl/api/listclubapplicationsapi",
        write(ListClubApplicationsAPIMessage(club.id.value, operatorId = Some(owner.id.value)))
      )
      assertEquals(response.statusCode(), 200)

      val applications = readPage[ClubMembershipApplicationView](response.body())
      assertEquals(applications.total, 1)
      assertEquals(applications.items.head.applicant.displayName, "Guest Applicant")
      assertEquals(applications.items.head.message, Some("let me in"))
    }
  }
