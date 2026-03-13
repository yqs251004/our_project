package riichinexus

import java.time.Instant

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

@main def riichiNexusDemo(): Unit =
  val app = ApplicationContext.fromEnvironment()
  val now = Instant.parse("2026-03-13T09:00:00Z")

  val alice = app.playerService.registerPlayer(
    userId = "u-alice",
    nickname = "Alice",
    rank = RankSnapshot(RankPlatform.Tenhou, "4-dan"),
    registeredAt = now,
    initialElo = 1610
  )
  val bob = app.playerService.registerPlayer(
    userId = "u-bob",
    nickname = "Bob",
    rank = RankSnapshot(RankPlatform.MahjongSoul, "Expert-3"),
    registeredAt = now,
    initialElo = 1540
  )
  val charlie = app.playerService.registerPlayer(
    userId = "u-charlie",
    nickname = "Charlie",
    rank = RankSnapshot(RankPlatform.Tenhou, "3-dan"),
    registeredAt = now,
    initialElo = 1490
  )
  val diana = app.playerService.registerPlayer(
    userId = "u-diana",
    nickname = "Diana",
    rank = RankSnapshot(RankPlatform.MahjongSoul, "Master-1"),
    registeredAt = now,
    initialElo = 1580
  )

  val club = app.clubService.createClub("EastWind Club", alice.id, now)
  app.clubService.addMember(club.id, bob.id)

  val swissStage = TournamentStage(
    id = IdGenerator.stageId(),
    name = "Swiss Stage 1",
    format = StageFormat.Swiss,
    order = 1,
    roundCount = 1
  )

  val tournament = app.tournamentService.createTournament(
    name = "RiichiNexus Spring Open",
    organizer = "Backend Team",
    startsAt = now.plusSeconds(3600),
    endsAt = now.plusSeconds(7200),
    stages = Vector(swissStage)
  )

  app.tournamentService.registerClub(tournament.id, club.id)
  app.tournamentService.registerPlayer(tournament.id, charlie.id)
  app.tournamentService.registerPlayer(tournament.id, diana.id)
  app.tournamentService.publishTournament(tournament.id)
  app.tournamentService.startTournament(tournament.id)

  val table = app.tournamentService
    .scheduleStageTables(tournament.id, swissStage.id)
    .head

  app.tableService.startTable(table.id, now.plusSeconds(3900))

  val paifu = demoPaifu(
    table = table,
    tournamentId = tournament.id,
    stageId = swissStage.id,
    recordedAt = now.plusSeconds(5400)
  )
  app.tableService.recordCompletedTable(table.id, paifu)

  println("== Updated Players ==")
  Vector(alice.id, bob.id, charlie.id, diana.id)
    .flatMap(app.playerRepository.findById)
    .foreach { player =>
      println(
        s"${player.nickname}: rank=${player.currentRank.tier}, elo=${player.elo}, club=${player.clubId.map(_.value).getOrElse("none")}"
      )
    }

  println()
  println("== Player Dashboards ==")
  Vector(alice.id, bob.id, charlie.id, diana.id).foreach { playerId =>
    app.dashboardRepository.findByOwner(DashboardOwner.Player(playerId)).foreach { dashboard =>
      println(
        s"${playerId.value}: winRate=${dashboard.winRate}, dealInRate=${dashboard.dealInRate}, avgWinPoints=${dashboard.averageWinPoints}, riichiRate=${dashboard.riichiRate}"
      )
    }
  }

  println()
  println("== Club Projection ==")
  app.clubRepository.findById(club.id).foreach { updatedClub =>
    println(s"${updatedClub.name}: totalPoints=${updatedClub.totalPoints}")
  }

private def demoPaifu(
    table: Table,
    tournamentId: TournamentId,
    stageId: TournamentStageId,
    recordedAt: Instant
): Paifu =
  val seatByWind = table.seats.map(seat => seat.seat -> seat.playerId).toMap
  val east = seatByWind(SeatWind.East)
  val south = seatByWind(SeatWind.South)
  val west = seatByWind(SeatWind.West)
  val north = seatByWind(SeatWind.North)

  val firstRound = KyokuRecord(
    descriptor = KyokuDescriptor(SeatWind.East, handNumber = 1, honba = 0),
    initialHands = table.seats.map(seat => seat.playerId -> Vector("1m", "2m", "3m")).toMap,
    actions = Vector(
      PaifuAction(1, Some(east), PaifuActionType.Draw, Some("4m"), Some(3)),
      PaifuAction(2, Some(east), PaifuActionType.Discard, Some("9p"), Some(2)),
      PaifuAction(3, Some(south), PaifuActionType.Riichi, note = Some("closed riichi")),
      PaifuAction(4, Some(south), PaifuActionType.Discard, Some("5s"), Some(1)),
      PaifuAction(5, Some(south), PaifuActionType.Win, Some("3p"), Some(0))
    ),
    result = AgariResult(
      outcome = HandOutcome.Ron,
      winner = Some(south),
      target = Some(west),
      han = Some(3),
      fu = Some(40),
      yaku = Vector(Yaku("Riichi", 1), Yaku("Pinfu", 1), Yaku("Ippatsu", 1)),
      points = 7700,
      scoreChanges = Vector(
        ScoreChange(east, 0),
        ScoreChange(south, 7700),
        ScoreChange(west, -7700),
        ScoreChange(north, 0)
      )
    )
  )

  val secondRound = KyokuRecord(
    descriptor = KyokuDescriptor(SeatWind.East, handNumber = 2, honba = 0),
    initialHands = table.seats.map(seat => seat.playerId -> Vector("4p", "5p", "6p")).toMap,
    actions = Vector(
      PaifuAction(1, Some(north), PaifuActionType.Draw, Some("7m"), Some(2)),
      PaifuAction(2, Some(north), PaifuActionType.Discard, Some("7m"), Some(2)),
      PaifuAction(3, Some(east), PaifuActionType.Riichi, note = Some("pressure riichi")),
      PaifuAction(4, Some(east), PaifuActionType.Win, Some("2s"), Some(0))
    ),
    result = AgariResult(
      outcome = HandOutcome.Tsumo,
      winner = Some(east),
      target = None,
      han = Some(2),
      fu = Some(30),
      yaku = Vector(Yaku("Riichi", 1), Yaku("Tsumo", 1)),
      points = 2000,
      scoreChanges = Vector(
        ScoreChange(east, 4000),
        ScoreChange(south, -1000),
        ScoreChange(west, -1000),
        ScoreChange(north, -2000)
      )
    )
  )

  Paifu(
    id = IdGenerator.paifuId(),
    metadata = PaifuMetadata(
      recordedAt = recordedAt,
      source = "demo-seed",
      tableId = table.id,
      tournamentId = tournamentId,
      stageId = stageId,
      seats = table.seats
    ),
    rounds = Vector(firstRound, secondRound),
    finalStandings = Vector(
      FinalStanding(east, SeatWind.East, 32100, 1),
      FinalStanding(south, SeatWind.South, 28700, 2),
      FinalStanding(north, SeatWind.North, 21200, 3),
      FinalStanding(west, SeatWind.West, 18000, 4)
    )
  )
