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
import riichinexus.microservices.club.api.*
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.club.objects.apiTypes.ClubTournamentResponses.given
import riichinexus.microservices.club.objects.apiTypes.*
import riichinexus.microservices.dictionary.objects.apiTypes.UpsertDictionaryRequest
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest
import riichinexus.microservices.tournament.objects.apiTypes.OperatorRequest.given
import riichinexus.microservices.player.api.ListPlayersAPIMessage
import riichinexus.microservices.player.objects.apiTypes.*
import riichinexus.microservices.player.objects.apiTypes.PlayerResponses.given
import riichinexus.microservices.publicquery.api.ListPublicClubsAPIMessage
import riichinexus.microservices.publicquery.objects.apiTypes.*
import riichinexus.microservices.publicquery.objects.apiTypes.PublicQueryResponses.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.TournamentOperationResponses.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.*
import upickle.default.*

class ApiServerClubOperationsSuite extends FunSuite with ApiServerSuiteSupport:
  test("players and clubs list endpoints support shared pagination and filters") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T12:30:00Z")

    val owner = playerService(app).registerPlayer("page-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val alpha = playerService(app).registerPlayer("page-alpha", "Alpha", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val bravo = playerService(app).registerPlayer("page-bravo", "Bravo", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val suspended = playerService(app).registerPlayer("page-suspended", "Suspended", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1400)

    val club = clubApi(app).createClub("Paged Club", owner.id, now, owner.asPrincipal)
    clubApi(app).addMember(club.id, alpha.id, principalFor(app, owner.id))
    clubApi(app).addMember(club.id, bravo.id, principalFor(app, owner.id))
    clubApi(app).addMember(club.id, suspended.id, principalFor(app, owner.id))
    val suspendedMember = playerRepository(app).findById(suspended.id).getOrElse(fail("suspended member missing"))
    playerRepository(app).save(suspendedMember.copy(status = PlayerStatus.Suspended))

    val retiredClub = clubRepository(app).save(
      Club(
        id = IdGenerator.clubId(),
        name = "Retired Club",
        creator = owner.id,
        createdAt = now.minusSeconds(3600),
        members = Vector(owner.id),
        admins = Vector(owner.id),
        dissolvedAt = Some(now),
        dissolvedBy = Some(owner.id)
      )
    )

    withServer(app) { baseUrl =>
      val playersResponse = postJson(
        s"$baseUrl/api/listplayersapi",
        write(ListPlayersAPIMessage(clubId = Some(club.id.value), status = Some("Active"), limit = Some(1), offset = Some(1)))
      )
      assertEquals(playersResponse.statusCode(), 200)
      val playersPage = readPage[PlayerProfileView](playersResponse.body())
      assertEquals(playersPage.total, 3)
      assertEquals(playersPage.limit, 1)
      assertEquals(playersPage.offset, 1)
      assertEquals(playersPage.items.map(_.nickname), Vector("Bravo"))
      assertEquals(playersPage.appliedFilters("clubId"), club.id.value)
      assertEquals(playersPage.appliedFilters("status"), "Active")

      val clubsResponse = postJson(
        s"$baseUrl/api/listclubsapi",
        write(ListClubsAPIMessage(memberId = Some(owner.id.value), activeOnly = Some(true), limit = Some(10)))
      )
      assertEquals(clubsResponse.statusCode(), 200)
      val clubsPage = readPage[Club](clubsResponse.body())
      assertEquals(clubsPage.total, 1)
      assertEquals(clubsPage.items.map(_.id), Vector(club.id))
      assert(!clubsPage.items.exists(_.id == retiredClub.id))
      assertEquals(clubsPage.appliedFilters("activeOnly"), "true")
    }
  }

  test("public club directory endpoint supports filtered summaries") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T13:00:00Z")

    val alphaOwner = playerService(app).registerPlayer("alpha-owner", "AlphaOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1820)
    val alphaActive = playerService(app).registerPlayer("alpha-active", "AlphaActive", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1690)
    val alphaSuspended = playerService(app).registerPlayer("alpha-suspended", "AlphaSuspended", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val betaOwner = playerService(app).registerPlayer("beta-owner", "BetaOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)

    val alphaClub = clubApi(app).createClub("Alpha Club", alphaOwner.id, now, alphaOwner.asPrincipal)
    val betaClub = clubApi(app).createClub("Beta Club", betaOwner.id, now.plusSeconds(60), betaOwner.asPrincipal)

    clubApi(app).addMember(alphaClub.id, alphaActive.id, principalFor(app, alphaOwner.id))
    clubApi(app).addMember(alphaClub.id, alphaSuspended.id, principalFor(app, alphaOwner.id))
    val suspendedPlayer = playerRepository(app).findById(alphaSuspended.id).getOrElse(fail("suspended player missing"))
    playerRepository(app).save(suspendedPlayer.copy(status = PlayerStatus.Suspended))

    clubApi(app).updateRelation(
      alphaClub.id,
      ClubRelation(betaClub.id, ClubRelationKind.Rivalry, now.plusSeconds(120), Some("league rival")),
      principalFor(app, alphaOwner.id),
      now.plusSeconds(120)
    )

    withServer(app) { baseUrl =>
      val response = postJson(
        s"$baseUrl/api/listpublicclubsapi",
        write(ListPublicClubsAPIMessage(relation = Some("Rivalry"), name = Some("Alpha")))
      )
      assertEquals(response.statusCode(), 200)
      val page = readPage[PublicClubDirectoryEntry](response.body())
      assertEquals(page.total, 1)
      assertEquals(page.items.map(_.clubId), Vector(alphaClub.id))
      assertEquals(page.items.head.memberCount, 3)
      assertEquals(page.items.head.activeMemberCount, 2)
      assertEquals(page.items.head.rivalryCount, 1)
      assertEquals(page.items.head.strongestRivalClubId, Some(betaClub.id))
      assertEquals(page.appliedFilters("relation"), "Rivalry")
      assertEquals(page.appliedFilters("name"), "Alpha")
    }
  }

  test("club management endpoints revoke admin and remove member") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T13:00:00Z")

    val owner = playerService(app).registerPlayer("club-owner", "ClubOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val vice = playerService(app).registerPlayer("club-vice", "ClubVice", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val member = playerService(app).registerPlayer("club-member", "ClubMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)

    val club = clubApi(app).createClub("Managed Club", owner.id, now, owner.asPrincipal)
    clubApi(app).addMember(club.id, vice.id, principalFor(app, owner.id))
    clubApi(app).addMember(club.id, member.id, principalFor(app, owner.id))
    clubApi(app).assignAdmin(club.id, vice.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val revokeResponse = postJson(
        s"$baseUrl/api/revokeclubadminapi",
        write(RevokeClubAdminAPIMessage(club.id.value, vice.id.value, operatorId = Some(owner.id.value)))
      )
      assertEquals(revokeResponse.statusCode(), 200)
      val updatedClub = read[Club](revokeResponse.body())
      assertEquals(updatedClub.admins, Vector(owner.id))

      val removeResponse = postJson(
        s"$baseUrl/api/removeclubmemberapi",
        write(RemoveClubMemberAPIMessage(club.id.value, member.id.value, operatorId = Some(owner.id.value)))
      )
      assertEquals(removeResponse.statusCode(), 200)
      val removedClub = read[Club](removeResponse.body())
      assertEquals(removedClub.members.toSet, Set(owner.id, vice.id))
      assertEquals(playerRepository(app).findById(member.id).map(_.boundClubIds), Some(Vector.empty))
    }
  }

  test("club operation endpoints update treasury point pool and rank tree") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:00:00Z")

    val owner = playerService(app).registerPlayer("club-fin-owner", "ClubFinOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = clubApi(app).createClub("Club Finance", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val treasuryResponse = postJson(
        s"$baseUrl/api/adjustclubtreasuryapi",
        write(AdjustClubTreasuryAPIMessage(club.id.value, owner.id.value, 2500L, Some("sponsor")))
      )
      assertEquals(treasuryResponse.statusCode(), 200)
      assertEquals(read[Club](treasuryResponse.body()).treasuryBalance, 2500L)

      val pointPoolResponse = postJson(
        s"$baseUrl/api/adjustclubpointpoolapi",
        write(AdjustClubPointPoolAPIMessage(club.id.value, owner.id.value, 180, Some("event reward")))
      )
      assertEquals(pointPoolResponse.statusCode(), 200)
      assertEquals(read[Club](pointPoolResponse.body()).pointPool, 180)

      val rankTreeResponse = postJson(
        s"$baseUrl/api/updateclubranktreeapi",
        write(
          UpdateClubRankTreeAPIMessage(
            clubId = club.id.value,
            operatorId = owner.id.value,
            ranks = Vector(
              ClubRankNodeRequest("rookie", "Rookie", 0),
              ClubRankNodeRequest("elite", "Elite", 1500, Vector("priority-lineup"))
            ),
            note = Some("season refresh")
          )
        )
      )
      assertEquals(rankTreeResponse.statusCode(), 200)
      assertEquals(read[Club](rankTreeResponse.body()).rankTree.map(_.code), Vector("rookie", "elite"))
    }
  }

  test("club member privilege endpoints expose delegated rank capabilities") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:03:00Z")

    val owner = playerService(app).registerPlayer("club-priv-owner", "ClubPrivOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val delegate = playerService(app).registerPlayer("club-priv-delegate", "ClubPrivDelegate", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val club = clubApi(app).createClub("Club Privileges", owner.id, now, owner.asPrincipal)
    clubApi(app).addMember(club.id, delegate.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val rankTreeResponse = postJson(
        s"$baseUrl/api/updateclubranktreeapi",
        write(
          UpdateClubRankTreeAPIMessage(
            clubId = club.id.value,
            operatorId = owner.id.value,
            ranks = Vector(
              ClubRankNodeRequest("rookie", "Rookie", 0),
              ClubRankNodeRequest("officer", "Officer", 100, Vector("manage-bank", "priority-lineup"))
            )
          )
        )
      )
      assertEquals(rankTreeResponse.statusCode(), 200)

      val contributionResponse = postJson(
        s"$baseUrl/api/adjustclubmembercontributionapi",
        write(AdjustClubMemberContributionAPIMessage(club.id.value, owner.id.value, delegate.id.value, 120, Some("season contribution")))
      )
      assertEquals(contributionResponse.statusCode(), 200)

      val detailResponse = postJson(
        s"$baseUrl/api/getclubmemberprivilegeapi",
        write(GetClubMemberPrivilegeAPIMessage(club.id.value, delegate.id.value))
      )
      assertEquals(detailResponse.statusCode(), 200)
      val detail = read[ClubMemberPrivilegeSnapshot](detailResponse.body())
      assertEquals(detail.rankCode, "officer")
      assertEquals(detail.privileges, Vector("manage-bank", "priority-lineup"))

      val listResponse = postJson(
        s"$baseUrl/api/listclubmemberprivilegesapi",
        write(ListClubMemberPrivilegesAPIMessage(club.id.value, privilege = Some("manage-bank")))
      )
      assertEquals(listResponse.statusCode(), 200)
      val page = readPage[ClubMemberPrivilegeSnapshot](listResponse.body())
      assertEquals(page.total, 1)
      assertEquals(page.items.head.playerId, delegate.id)

      val delegatedTreasury = postJson(
        s"$baseUrl/api/adjustclubtreasuryapi",
        write(AdjustClubTreasuryAPIMessage(club.id.value, delegate.id.value, 900L, Some("delegated bank access")))
      )
      assertEquals(delegatedTreasury.statusCode(), 200)
      assertEquals(read[Club](delegatedTreasury.body()).treasuryBalance, 900L)
    }
  }

  test("club title API supports clearing assigned titles") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:05:00Z")

    val owner = playerService(app).registerPlayer("api-title-owner", "ApiTitleOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = playerService(app).registerPlayer("api-title-member", "ApiTitleMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = clubApi(app).createClub("API Title Club", owner.id, now, owner.asPrincipal)
    clubApi(app).addMember(club.id, member.id, principalFor(app, owner.id))

    withServer(app) { baseUrl =>
      val assignResponse = postJson(
        s"$baseUrl/api/assignclubtitleapi",
        write(AssignClubTitleAPIMessage(club.id.value, member.id.value, owner.id.value, "Vice Captain", Some("promotion")))
      )
      assertEquals(assignResponse.statusCode(), 200)
      assertEquals(read[Club](assignResponse.body()).titleAssignments.map(_.title), Vector("Vice Captain"))

      val clearResponse = postJson(
        s"$baseUrl/api/clearclubtitleapi",
        write(ClearClubTitleAPIMessage(club.id.value, member.id.value, owner.id.value, Some("rotation")))
      )
      assertEquals(clearResponse.statusCode(), 200)
      assertEquals(read[Club](clearResponse.body()).titleAssignments, Vector.empty)
    }
  }

  test("club relation endpoint keeps reciprocal mappings in sync") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:15:00Z")

    val ownerA = playerService(app).registerPlayer("api-relation-a", "ApiRelationA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val ownerB = playerService(app).registerPlayer("api-relation-b", "ApiRelationB", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1780)
    val clubA = clubApi(app).createClub("API Alliance A", ownerA.id, now, ownerA.asPrincipal)
    val clubB = clubApi(app).createClub("API Alliance B", ownerB.id, now, ownerB.asPrincipal)

    withServer(app) { baseUrl =>
      val allianceResponse = postJson(
        s"$baseUrl/api/updateclubrelationapi",
        write(UpdateClubRelationAPIMessage(clubA.id.value, ownerA.id.value, clubB.id.value, "Alliance", Some("partner")))
      )
      assertEquals(allianceResponse.statusCode(), 200)
      assertEquals(read[Club](allianceResponse.body()).relations.map(_.targetClubId), Vector(clubB.id))
      assertEquals(
        clubRepository(app).findById(clubB.id).map(_.relations.map(_.targetClubId)),
        Some(Vector(clubA.id))
      )

      val neutralResponse = postJson(
        s"$baseUrl/api/updateclubrelationapi",
        write(UpdateClubRelationAPIMessage(clubA.id.value, ownerA.id.value, clubB.id.value, "Neutral", Some("reset")))
      )
      assertEquals(neutralResponse.statusCode(), 200)
      assertEquals(read[Club](neutralResponse.body()).relations, Vector.empty)
      assertEquals(clubRepository(app).findById(clubB.id).map(_.relations), Some(Vector.empty))
    }
  }

  test("club honor endpoints award and revoke honors") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-15T15:20:00Z")

    val owner = playerService(app).registerPlayer("api-honor-owner", "ApiHonorOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = clubApi(app).createClub("API Honor Club", owner.id, now, owner.asPrincipal)

    withServer(app) { baseUrl =>
      val awardResponse = postJson(
        s"$baseUrl/api/awardclubhonorapi",
        write(AwardClubHonorAPIMessage(club.id.value, owner.id.value, "Golden Tile", Some("season MVP"), Some(now.plusSeconds(60))))
      )
      assertEquals(awardResponse.statusCode(), 200)
      assertEquals(read[Club](awardResponse.body()).honors.map(_.title), Vector("Golden Tile"))

      val revokeResponse = postJson(
        s"$baseUrl/api/revokeclubhonorapi",
        write(RevokeClubHonorAPIMessage(club.id.value, owner.id.value, "Golden Tile", Some("retired award")))
      )
      assertEquals(revokeResponse.statusCode(), 200)
      assertEquals(read[Club](revokeResponse.body()).honors, Vector.empty)
    }
  }

