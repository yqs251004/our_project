package riichinexus

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Instant

import munit.FunSuite

import riichinexus.api.{ApiRuntimeContext, ApiServerConfig, RiichiNexusApiServer}
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.microservices.opsanalytics.objects.apiTypes.PerformanceDiagnosticsSnapshot
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.dictionary.objects.DictionaryTestClient
import riichinexus.system.objects.PagedResponse
import upickle.default.*

trait ApiServerSuiteSupport extends TestApplicationAccess:
  self: FunSuite =>

  protected val client: HttpClient = HttpClient.newHttpClient()

  protected def withServer[A](app: ApplicationContext)(f: String => A): A =
    val config = ApiServerConfig(host = "127.0.0.1", port = 0, storageLabel = "memory")
    val server = RiichiNexusApiServer(
      ApiRuntimeContext.fromApplication(app, config),
      config
    )

    server.start()
    try f(s"http://127.0.0.1:${server.port}")
    finally server.stop()

  protected def get(url: String): HttpResponse[String] =
    client.send(
      HttpRequest
        .newBuilder(URI.create(url))
        .version(HttpClient.Version.HTTP_1_1)
        .GET()
        .build(),
      HttpResponse.BodyHandlers.ofString()
    )

  protected def postJson(url: String, body: String, headers: (String, String)*): HttpResponse[String] =
    val builder = HttpRequest
      .newBuilder(URI.create(url))
      .version(HttpClient.Version.HTTP_1_1)
      .header("Content-Type", "application/json")
      .POST(HttpRequest.BodyPublishers.ofString(body))
    headers.foreach { case (name, value) => builder.header(name, value) }
    client.send(
      builder.build(),
      HttpResponse.BodyHandlers.ofString()
    )

  protected def postApi[Message: Writer](baseUrl: String, message: Message): HttpResponse[String] =
    postJson(s"$baseUrl/api/${apiNameOf(message)}", write(message))

  private def apiNameOf(message: Any): String =
    val className = message.getClass.getSimpleName.stripSuffix("$")
    val baseName = className.stripSuffix("APIMessage")
    s"${baseName}API".toLowerCase

  protected def principalFor(app: ApplicationContext, playerId: PlayerId): AccessPrincipal =
    playerRepository(app).findById(playerId).getOrElse(fail(s"player ${playerId.value} missing")).asPrincipal

  protected def dictionaryApi(app: ApplicationContext): DictionaryTestClient =
    dictionaryApiClient(app)

  protected def readPage[T: Reader](body: String): PagedResponse[T] =
    read[PagedResponse[T]](body)

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
        source = "api-test-fixture",
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
