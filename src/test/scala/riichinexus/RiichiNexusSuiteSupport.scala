package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.application.ports.GlobalDictionaryRepository
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

trait RiichiNexusSuiteSupport:
  self: FunSuite =>

  protected final class CountingGlobalDictionaryRepository(delegate: GlobalDictionaryRepository)
      extends GlobalDictionaryRepository:
    var findAllCalls: Int = 0

    override def save(entry: GlobalDictionaryEntry): GlobalDictionaryEntry =
      delegate.save(entry)

    override def findByKey(key: String): Option[GlobalDictionaryEntry] =
      delegate.findByKey(key)

    override def findAll(): Vector[GlobalDictionaryEntry] =
      findAllCalls += 1
      delegate.findAll()

  protected def repositoryCallCount(app: ApplicationContext, key: String): Long =
    app.performanceDiagnosticsService.snapshot(limit = 100).busiestRepositoryCalls
      .find(_.key == key)
      .map(_.count)
      .getOrElse(0L)

  protected def repositoryTotalMillis(app: ApplicationContext, key: String): Double =
    app.performanceDiagnosticsService.snapshot(limit = 100).busiestRepositoryCalls
      .find(_.key == key)
      .map(_.totalMillis)
      .getOrElse(0.0)

  protected def eventually[A](message: String, attempts: Int = 40, sleepMillis: Long = 25)(check: => Option[A]): A =
    var result = Option.empty[A]
    var attempt = 1
    while result.isEmpty && attempt <= attempts do
      result = check
      if result.isEmpty && attempt < attempts then Thread.sleep(sleepMillis)
      attempt += 1

    result.getOrElse(throw AssertionError(message))

  protected def principalFor(app: ApplicationContext, playerId: PlayerId): AccessPrincipal =
    app.playerRepository.findById(playerId).get.asPrincipal

  protected def detailedAnalyticsPaifu(
      table: Table,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      recordedAt: Instant
  ): Paifu =
    val orderedSeats = table.seats.sortBy(_.seat.ordinal)
    val east = orderedSeats(0).playerId
    val south = orderedSeats(1).playerId
    val west = orderedSeats(2).playerId
    val north = orderedSeats(3).playerId
    val seatByPlayer = table.seats.map(seat => seat.playerId -> seat.seat).toMap

    Paifu(
      id = IdGenerator.paifuId(),
      metadata = PaifuMetadata(
        recordedAt = recordedAt,
        source = "detailed-analytics-fixture",
        tableId = table.id,
        tournamentId = tournamentId,
        stageId = stageId,
        seats = table.seats
      ),
      rounds = Vector(
        KyokuRecord(
          descriptor = KyokuDescriptor(SeatWind.East, 1, 0),
          initialHands = Map(
            east -> Vector("1m", "2m", "4m", "5m", "6m", "7m", "2p", "3p", "4p", "6p", "1z", "1z", "9s"),
            south -> Vector("2m", "3m", "4m", "2p", "3p", "4p", "6p", "7p", "8p", "3s", "4s", "5s", "1z"),
            west -> Vector("3m", "4m", "5m", "4p", "5p", "6p", "4s", "5s", "6s", "2z", "3z", "4z", "7z"),
            north -> Vector("6m", "7m", "8m", "1p", "1p", "2p", "2p", "7s", "8s", "9s", "5z", "6z", "7z")
          ),
          actions = Vector(
            PaifuAction(1, Some(east), PaifuActionType.Draw, Some("5p"), Some(1)),
            PaifuAction(
              2,
              Some(east),
              PaifuActionType.Chi,
              Some("3m"),
              Some(0),
              handTilesAfterAction = Some(Vector("4m", "5m", "6m", "7m", "2p", "3p", "4p", "5p", "6p", "1z", "1z", "9s", "9s")),
              revealedTiles = Vector("1m", "2m", "3m")
            ),
            PaifuAction(
              3,
              Some(south),
              PaifuActionType.Riichi,
              Some("1z"),
              Some(0),
              revealedTiles = Vector("1z"),
              note = Some("closed riichi")
            ),
            PaifuAction(4, Some(east), PaifuActionType.Draw, Some("9p"), Some(0)),
            PaifuAction(
              5,
              Some(east),
              PaifuActionType.Discard,
              Some("1z"),
              Some(0),
              handTilesAfterAction = Some(Vector("4m", "5m", "6m", "7m", "2p", "3p", "4p", "5p", "6p", "9s", "9s", "9p", "1z")),
              revealedTiles = Vector("1z")
            ),
            PaifuAction(6, Some(south), PaifuActionType.Win, Some("5s"), Some(-1))
          ),
          result = AgariResult(
            outcome = HandOutcome.Ron,
            winner = Some(south),
            target = Some(west),
            han = Some(3),
            fu = Some(40),
            yaku = Vector(Yaku("Riichi", 1), Yaku("Pinfu", 1), Yaku("Tanyao", 1)),
            points = 7700,
            scoreChanges = Vector(
              ScoreChange(east, 0),
              ScoreChange(south, 7700),
              ScoreChange(west, -7700),
              ScoreChange(north, 0)
            )
          )
        )
      ),
      finalStandings = Vector(
        FinalStanding(south, seatByPlayer(south), 32700, 1),
        FinalStanding(east, seatByPlayer(east), 25000, 2),
        FinalStanding(north, seatByPlayer(north), 25000, 3),
        FinalStanding(west, seatByPlayer(west), 17300, 4)
      )
    )

  protected def demoPaifu(
      table: Table,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      recordedAt: Instant
  ): Paifu =
    val orderedSeats = table.seats.sortBy(_.seat.ordinal)
    demoPaifuForResult(
      table,
      tournamentId,
      stageId,
      recordedAt,
      winner = orderedSeats(1).playerId,
      target = orderedSeats(2).playerId
    )

  protected def demoPaifuForResult(
      table: Table,
      tournamentId: TournamentId,
      stageId: TournamentStageId,
      recordedAt: Instant,
      winner: PlayerId,
      target: PlayerId
  ): Paifu =
    val orderedSeats = table.seats.sortBy(_.seat.ordinal)
    val east = orderedSeats(0).playerId
    val south = orderedSeats(1).playerId
    val west = orderedSeats(2).playerId
    val north = orderedSeats(3).playerId
    val seatByPlayer = table.seats.map(seat => seat.playerId -> seat.seat).toMap
    val untouchedPlayers = orderedSeats.map(_.playerId).filterNot(playerId =>
      playerId == winner || playerId == target
    )
    val secondPlayer = untouchedPlayers.headOption.getOrElse(east)
    val thirdPlayer = untouchedPlayers.drop(1).headOption.getOrElse(north)
    val finalPoints = Map(
      winner -> 32700,
      secondPlayer -> 25000,
      thirdPlayer -> 25000,
      target -> 17300
    )
    val placements = Vector(winner, secondPlayer, thirdPlayer, target)

    Paifu(
      id = IdGenerator.paifuId(),
      metadata = PaifuMetadata(
        recordedAt = recordedAt,
        source = "test-fixture",
        tableId = table.id,
        tournamentId = tournamentId,
        stageId = stageId,
        seats = table.seats
      ),
      rounds = Vector(
        KyokuRecord(
          descriptor = KyokuDescriptor(SeatWind.East, 1, 0),
          initialHands = table.seats.map(seat => seat.playerId -> Vector("1m", "1p", "1s")).toMap,
          actions = Vector(
            PaifuAction(1, Some(east), PaifuActionType.Draw, Some("4m"), Some(3)),
            PaifuAction(2, Some(winner), PaifuActionType.Riichi, note = Some("riichi")),
            PaifuAction(3, Some(winner), PaifuActionType.Win, Some("7p"), Some(0))
          ),
          result = AgariResult(
            outcome = HandOutcome.Ron,
            winner = Some(winner),
            target = Some(target),
            han = Some(3),
            fu = Some(40),
            yaku = Vector(Yaku("Riichi", 1), Yaku("Pinfu", 1), Yaku("Ippatsu", 1)),
            points = 7700,
            scoreChanges = Vector(
              ScoreChange(east, if east == winner then 7700 else if east == target then -7700 else 0),
              ScoreChange(south, if south == winner then 7700 else if south == target then -7700 else 0),
              ScoreChange(west, if west == winner then 7700 else if west == target then -7700 else 0),
              ScoreChange(north, if north == winner then 7700 else if north == target then -7700 else 0)
            )
          )
        )
      ),
      finalStandings = placements.zipWithIndex.map { case (playerId, index) =>
        FinalStanding(
          playerId,
          seatByPlayer(playerId),
          finalPoints(playerId),
          index + 1
        )
      }
    )
