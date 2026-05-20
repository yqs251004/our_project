package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.application.ports.GlobalDictionaryRepository
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.tables.PublicQueryTables

class RiichiNexusTournamentDictionarySuite extends FunSuite with RiichiNexusSuiteSupport:

  test("global dictionary normalizes ranks in the public player leaderboard") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:30:00Z")

    dictionaryApi(app).upsertDictionary(
      key = "rank.normalization.tenhou.5-dan",
      value = "550",
      actor = AccessPrincipal.system,
      updatedAt = now
    )
    dictionaryApi(app).upsertDictionary(
      key = "rank.normalization.mahjongsoul.master",
      value = "600",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(1)
    )
    dictionaryApi(app).upsertDictionary(
      key = "rank.normalization.mahjongsoul.starWeight",
      value = "10",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(2)
    )

    val tenhou = playerService(app).registerPlayer("rank-dict-a", "RankA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    val soul = playerService(app).registerPlayer("rank-dict-b", "RankB", RankSnapshot(RankPlatform.MahjongSoul, "Master", Some(2)), now, 1700)

    val leaderboard = publicQueryOperations(app).publicPlayerLeaderboard(10)
    val soulEntry = leaderboard.find(_.playerId == soul.id).getOrElse(fail("soul entry missing"))
    val tenhouEntry = leaderboard.find(_.playerId == tenhou.id).getOrElse(fail("tenhou entry missing"))

    assertEquals(soulEntry.normalizedRankScore, Some(620))
    assertEquals(tenhouEntry.normalizedRankScore, Some(550))
    assertEquals(leaderboard.head.playerId, soul.id)
  }

  test("public player leaderboard reads global dictionary once per request") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:35:00Z")

    dictionaryApi(app).upsertDictionary(
      key = "rank.normalization.tenhou.5-dan",
      value = "550",
      actor = AccessPrincipal.system,
      updatedAt = now
    )
    dictionaryApi(app).upsertDictionary(
      key = "rank.normalization.mahjongsoul.master",
      value = "600",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(1)
    )
    dictionaryApi(app).upsertDictionary(
      key = "rank.normalization.mahjongsoul.starWeight",
      value = "10",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(2)
    )

    playerService(app).registerPlayer("rank-cache-a", "RankCacheA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)
    playerService(app).registerPlayer("rank-cache-b", "RankCacheB", RankSnapshot(RankPlatform.MahjongSoul, "Master", Some(2)), now, 1700)

    val countingDictionaryRepository = new CountingGlobalDictionaryRepository(globalDictionaryRepository(app))
    val publicQueryTables = PublicQueryTables(
      tournamentRepository(app),
      tableRepository(app),
      playerRepository(app),
      clubRepository(app),
      countingDictionaryRepository
    )

    val leaderboard = publicQueryTables.publicPlayerLeaderboard(10)

    assertEquals(leaderboard.size, 2)
    assertEquals(countingDictionaryRepository.findAllCalls, 1)
  }

  test("public player leaderboard records dictionary timing diagnostics") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:40:00Z")

    dictionaryApi(app).upsertDictionary(
      key = "rank.normalization.tenhou.5-dan",
      value = "550",
      actor = AccessPrincipal.system,
      updatedAt = now
    )
    playerService(app).registerPlayer("rank-diag-a", "RankDiagA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1700)

    publicQueryOperations(app).publicPlayerLeaderboard(10)

    assertEquals(repositoryCallCount(app, "GlobalDictionaryRepository.findAll"), 1L)
    assert(repositoryTotalMillis(app, "GlobalDictionaryRepository.findAll") >= 0.0)
  }

  test("club power dictionary update repairs active clubs with one dictionary scan") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:45:00Z")

    val ownerA = playerService(app).registerPlayer("club-power-owner-a", "ClubPowerOwnerA", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1800)
    val ownerB = playerService(app).registerPlayer("club-power-owner-b", "ClubPowerOwnerB", RankSnapshot(RankPlatform.Tenhou, "5-dan"), now, 1750)
    clubApi(app).createClub("Club Power A", ownerA.id, now, ownerA.asPrincipal)
    clubApi(app).createClub("Club Power B", ownerB.id, now.plusSeconds(1), ownerB.asPrincipal)

    val beforeCalls = repositoryCallCount(app, "GlobalDictionaryRepository.findAll")

    dictionaryApi(app).upsertDictionary(
      key = "club.power.eloWeight",
      value = "1.15",
      actor = AccessPrincipal.system,
      updatedAt = now.plusSeconds(2)
    )

    val afterCalls = repositoryCallCount(app, "GlobalDictionaryRepository.findAll")
    assertEquals(afterCalls - beforeCalls, 1L)
  }

  test("creating tournament with multiple templated stages reads global dictionary once") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T12:50:00Z")

    dictionaryApi(app).upsertDictionary(
      key = "tournament.rule-template.swiss-snake-template",
      value = "advancement=SwissCut;cutSize=8;pairingMethod=snake;maxRounds=2;schedulingPoolSize=2;note=template backed",
      actor = AccessPrincipal.system,
      updatedAt = now
    )

    val beforeCalls = repositoryCallCount(app, "GlobalDictionaryRepository.findAll")
    val stages = Vector(
      TournamentStage(
        id = IdGenerator.stageId(),
        name = "Template Stage A",
        format = StageFormat.Swiss,
        order = 1,
        roundCount = 2,
        advancementRule = AdvancementRule(AdvancementRuleType.Custom, templateKey = Some("swiss-snake-template"))
      ),
      TournamentStage(
        id = IdGenerator.stageId(),
        name = "Template Stage B",
        format = StageFormat.Swiss,
        order = 2,
        roundCount = 2,
        advancementRule = AdvancementRule(AdvancementRuleType.Custom, templateKey = Some("swiss-snake-template"))
      )
    )

    val tournament = tournamentService(app).createTournament(
      name = "Template Diagnostics Cup",
      organizer = "QA",
      startsAt = now.plusSeconds(60),
      endsAt = now.plusSeconds(7200),
      stages = stages
    )

    val afterCalls = repositoryCallCount(app, "GlobalDictionaryRepository.findAll")

    assertEquals(afterCalls - beforeCalls, 1L)
    assertEquals(tournament.stages.map(_.schedulingPoolSize), Vector(2, 2))
  }
