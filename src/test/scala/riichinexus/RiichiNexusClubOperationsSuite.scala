package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.microservices.club.api.ClubQueryApi
import riichinexus.microservices.club.objects.ClubPrivilegeSnapshotQuery

class RiichiNexusClubOperationsSuite extends FunSuite with RiichiNexusSuiteSupport:
  test("club operations update treasury point pool and rank tree") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T14:00:00Z")

    val owner = playerService(app).registerPlayer("club-ops-owner", "Owner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = clubService(app).createClub("Operations Club", owner.id, now, owner.asPrincipal)

    val afterTreasury = clubService(app).adjustTreasury(
      club.id,
      delta = 5000L,
      actor = principalFor(app, owner.id),
      occurredAt = now.plusSeconds(60),
      note = Some("sponsor payment")
    ).getOrElse(fail("treasury update failed"))
    assertEquals(afterTreasury.treasuryBalance, 5000L)

    val afterPointPool = clubService(app).adjustPointPool(
      club.id,
      delta = 320,
      actor = principalFor(app, owner.id),
      occurredAt = now.plusSeconds(120),
      note = Some("internal event reward")
    ).getOrElse(fail("point pool update failed"))
    assertEquals(afterPointPool.pointPool, 320)

    val updatedRankTree = clubService(app).updateRankTree(
      club.id,
      rankTree = Vector(
        ClubRankNode("rookie", "Rookie", 0),
        ClubRankNode("veteran", "Veteran", 1200, Vector("priority-lineup")),
        ClubRankNode("captain", "Captain", 2400, Vector("approve-roster", "manage-bank"))
      ),
      actor = principalFor(app, owner.id),
      occurredAt = now.plusSeconds(180),
      note = Some("season update")
    ).getOrElse(fail("rank tree update failed"))
    assertEquals(updatedRankTree.rankTree.map(_.code), Vector("rookie", "veteran", "captain"))
    assertEquals(updatedRankTree.rankTree.last.privileges, Vector("approve-roster", "manage-bank"))

    val auditTypes = auditEventRepository(app).findByAggregate("club", club.id.value).map(_.eventType)
    assert(auditTypes.contains("ClubTreasuryAdjusted"))
    assert(auditTypes.contains("ClubPointPoolAdjusted"))
    assert(auditTypes.contains("ClubRankTreeUpdated"))
  }

  test("club member contributions resolve effective rank privileges") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T11:00:00Z")

    val owner = playerService(app).registerPlayer("club-rank-owner", "RankOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val member = playerService(app).registerPlayer("club-rank-member", "RankMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val club = clubService(app).createClub("Ranked Club", owner.id, now, owner.asPrincipal)
    clubService(app).addMember(club.id, member.id, principalFor(app, owner.id))

    clubService(app).updateRankTree(
      club.id,
      Vector(
        ClubRankNode("rookie", "Rookie", 0),
        ClubRankNode("featured", "Featured", 100, Vector("priority-lineup")),
        ClubRankNode("officer", "Officer", 250, Vector("manage-bank", "approve-roster"))
      ),
      principalFor(app, owner.id),
      occurredAt = now.plusSeconds(30)
    )

    clubService(app).adjustMemberContribution(
      club.id,
      member.id,
      delta = 260,
      actor = principalFor(app, owner.id),
      occurredAt = now.plusSeconds(60),
      note = Some("league operations")
    )

    val snapshot = ClubQueryApi.privilegeSnapshot(clubTables(app), club.id, member.id)
      .getOrElse(fail("member privilege snapshot missing"))
    assertEquals(snapshot.contribution, 260)
    assertEquals(snapshot.rankCode, "officer")
    assertEquals(snapshot.privileges, Vector("approve-roster", "manage-bank"))
    assertEquals(snapshot.isAdmin, false)

    val listed = ClubQueryApi.listPrivilegeSnapshots(clubTables(app), club.id, ClubPrivilegeSnapshotQuery())
    assert(listed.exists(_.playerId == member.id))

    val auditTypes = auditEventRepository(app).findByAggregate("club", club.id.value).map(_.eventType)
    assert(auditTypes.contains("ClubMemberContributionAdjusted"))
  }

  test("delegated club rank privileges unlock roster bank and lineup operations") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T11:20:00Z")

    val owner = playerService(app).registerPlayer("delegated-owner", "DelegatedOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val delegate = playerService(app).registerPlayer("delegated-member", "DelegatedMember", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1700)
    val third = playerService(app).registerPlayer("delegated-third", "DelegatedThird", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650)
    val fourth = playerService(app).registerPlayer("delegated-fourth", "DelegatedFourth", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1620)
    val applicant = playerService(app).registerPlayer("delegated-applicant", "DelegatedApplicant", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)

    val club = clubService(app).createClub("Delegated Club", owner.id, now, owner.asPrincipal)
    Vector(delegate, third, fourth).foreach(player =>
      clubService(app).addMember(club.id, player.id, principalFor(app, owner.id))
    )

    clubService(app).updateRankTree(
      club.id,
      Vector(
        ClubRankNode("rookie", "Rookie", 0),
        ClubRankNode(
          "officer",
          "Officer",
          100,
          Vector("manage-bank", "approve-roster", "priority-lineup")
        )
      ),
      principalFor(app, owner.id),
      occurredAt = now.plusSeconds(10)
    )
    clubService(app).adjustMemberContribution(
      club.id,
      delegate.id,
      delta = 120,
      actor = principalFor(app, owner.id),
      occurredAt = now.plusSeconds(20)
    )

    val delegatePrincipal = principalFor(app, delegate.id)

    val application = clubService(app).applyForMembership(
      club.id,
      applicantUserId = Some(applicant.userId),
      displayName = applicant.nickname,
      message = Some("let me in"),
      submittedAt = now.plusSeconds(30),
      actor = principalFor(app, applicant.id)
    ).getOrElse(fail("application missing"))

    val afterApproval = clubService(app).approveMembershipApplication(
      club.id,
      application.id,
      applicant.id,
      actor = delegatePrincipal,
      approvedAt = now.plusSeconds(40),
      note = Some("delegated approval")
    ).getOrElse(fail("approval failed"))
    assert(afterApproval.members.contains(applicant.id))

    val afterTreasury = clubService(app).adjustTreasury(
      club.id,
      delta = 2000,
      actor = delegatePrincipal,
      occurredAt = now.plusSeconds(50),
      note = Some("delegated bank access")
    ).getOrElse(fail("delegated treasury update failed"))
    assertEquals(afterTreasury.treasuryBalance, 2000L)

    val stage = TournamentStage(IdGenerator.stageId(), "Delegated Lineup Stage", StageFormat.Swiss, 1, 1)
    val tournament = tournamentService(app).createTournament(
      "Delegated Lineup Cup",
      "QA",
      now,
      now.plusSeconds(7200),
      Vector(stage)
    )
    tournamentService(app).registerClub(tournament.id, club.id)
    tournamentService(app).publishTournament(tournament.id)

    val updatedTournament = tournamentService(app).submitLineup(
      tournament.id,
      stage.id,
      StageLineupSubmission(
        id = IdGenerator.lineupSubmissionId(),
        clubId = club.id,
        submittedBy = delegate.id,
        submittedAt = now.plusSeconds(60),
        seats = Vector(
          StageLineupSeat(owner.id),
          StageLineupSeat(delegate.id),
          StageLineupSeat(third.id),
          StageLineupSeat(fourth.id)
        )
      ),
      delegatePrincipal
    ).getOrElse(fail("delegated lineup submission failed"))

    val savedStage = updatedTournament.stages.find(_.id == stage.id).getOrElse(fail("stage missing"))
    assertEquals(savedStage.lineupSubmissions.size, 1)
    assertEquals(savedStage.lineupSubmissions.head.submittedBy, delegate.id)
  }

  test("club relations stay reciprocal when updated or cleared") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T18:00:00Z")

    val ownerA = playerService(app).registerPlayer("relation-a", "RelationA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1650)
    val ownerB = playerService(app).registerPlayer("relation-b", "RelationB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1640)
    val clubA = clubService(app).createClub("Alliance A", ownerA.id, now, ownerA.asPrincipal)
    val clubB = clubService(app).createClub("Alliance B", ownerB.id, now, ownerB.asPrincipal)

    clubService(app).updateRelation(
      clubA.id,
      ClubRelation(clubB.id, ClubRelationKind.Alliance, now, Some("shared training")),
      principalFor(app, ownerA.id),
      now
    )

    val alliedA = clubRepository(app).findById(clubA.id).getOrElse(fail("clubA missing"))
    val alliedB = clubRepository(app).findById(clubB.id).getOrElse(fail("clubB missing"))
    assertEquals(alliedA.relations.map(_.targetClubId), Vector(clubB.id))
    assertEquals(alliedA.relations.map(_.relation), Vector(ClubRelationKind.Alliance))
    assertEquals(alliedB.relations.map(_.targetClubId), Vector(clubA.id))
    assertEquals(alliedB.relations.map(_.relation), Vector(ClubRelationKind.Alliance))

    clubService(app).updateRelation(
      clubA.id,
      ClubRelation(clubB.id, ClubRelationKind.Neutral, now.plusSeconds(60), Some("season reset")),
      principalFor(app, ownerA.id),
      now.plusSeconds(60)
    )

    assertEquals(clubRepository(app).findById(clubA.id).map(_.relations), Some(Vector.empty))
    assertEquals(clubRepository(app).findById(clubB.id).map(_.relations), Some(Vector.empty))
    val auditTypes = auditEventRepository(app).findByAggregate("club", clubA.id.value).map(_.eventType)
    assert(auditTypes.contains("ClubRelationUpdated"))
  }

  test("club honors can be awarded and revoked with audit trail") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-13T14:30:00Z")

    val owner = playerService(app).registerPlayer("club-honor-owner", "HonorOwner", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val club = clubService(app).createClub("Honor Club", owner.id, now, owner.asPrincipal)

    val afterAward = clubService(app).awardHonor(
      club.id,
      ClubHonor("Spring Split Champion", now.plusSeconds(60), Some("won grand finals")),
      principalFor(app, owner.id),
      now.plusSeconds(60)
    ).getOrElse(fail("honor award failed"))
    assertEquals(afterAward.honors.map(_.title), Vector("Spring Split Champion"))

    val afterUpdate = clubService(app).awardHonor(
      club.id,
      ClubHonor("Spring Split Champion", now.plusSeconds(120), Some("updated note")),
      principalFor(app, owner.id),
      now.plusSeconds(120)
    ).getOrElse(fail("honor update failed"))
    assertEquals(afterUpdate.honors.size, 1)
    assertEquals(afterUpdate.honors.head.note, Some("updated note"))

    val afterRevoke = clubService(app).revokeHonor(
      club.id,
      "Spring Split Champion",
      principalFor(app, owner.id),
      now.plusSeconds(180),
      Some("season rollover")
    ).getOrElse(fail("honor revoke failed"))
    assertEquals(afterRevoke.honors, Vector.empty)

    val auditTypes = auditEventRepository(app).findByAggregate("club", club.id.value).map(_.eventType)
    assert(auditTypes.contains("ClubHonorAwarded"))
    assert(auditTypes.contains("ClubHonorRevoked"))
  }
