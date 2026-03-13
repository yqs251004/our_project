package riichinexus.api

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.concurrent.Executors

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.sun.net.httpserver.HttpServer

import scala.util.Try
import scala.util.Using

import riichinexus.api.ApiModels.given
import riichinexus.application.ports.*
import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

final case class ApiServerConfig(
    host: String,
    port: Int,
    storageLabel: String
)

object ApiServerConfig:
  def fromEnv(env: collection.Map[String, String] = sys.env): ApiServerConfig =
    ApiServerConfig(
      host = env.getOrElse("RIICHI_API_HOST", "0.0.0.0"),
      port = env.get("RIICHI_API_PORT").flatMap(value => Try(value.toInt).toOption).getOrElse(8080),
      storageLabel =
        env.get("RIICHI_STORAGE").map(_.trim.toLowerCase).getOrElse {
          if env.contains("RIICHI_DB_URL") then "postgres" else "memory"
        }
    )

final class RiichiNexusApiServer(
    app: ApplicationContext,
    config: ApiServerConfig
):
  private val server = HttpServer.create(InetSocketAddress(config.host, config.port), 0)

  server.createContext("/", ApiHandler(app, config))
  server.setExecutor(Executors.newVirtualThreadPerTaskExecutor())

  def start(): Unit =
    server.start()

  def stop(delaySeconds: Int = 0): Unit =
    server.stop(delaySeconds)

private final class ApiHandler(
    app: ApplicationContext,
    config: ApiServerConfig
) extends HttpHandler:
  override def handle(exchange: HttpExchange): Unit =
    try
      route(exchange)
    catch
      case error: IllegalArgumentException =>
        sendJson(exchange, 400, ApiError(error.getMessage))
      case error: NoSuchElementException =>
        sendJson(exchange, 404, ApiError(error.getMessage))
      case error: ujson.ParseException =>
        sendJson(exchange, 400, ApiError(s"Invalid JSON body: ${error.getMessage}"))
      case error: Throwable =>
        sendJson(exchange, 500, ApiError(Option(error.getMessage).getOrElse("Internal server error")))

  private def route(exchange: HttpExchange): Unit =
    val method = exchange.getRequestMethod.toUpperCase
    val path = exchange.getRequestURI.getPath
    val segments = path.split("/").toVector.filter(_.nonEmpty)

    (method, segments) match
      case ("GET", Vector()) | ("GET", Vector("health")) =>
        sendJson(
          exchange,
          200,
          HealthResponse(
            status = "ok",
            storage = config.storageLabel,
            timestamp = Instant.now()
          )
        )

      case ("GET", Vector("players")) =>
        sendJson(exchange, 200, app.playerRepository.findAll())
      case ("GET", Vector("players", playerId)) =>
        sendOption(exchange, app.playerRepository.findById(PlayerId(playerId)))
      case ("POST", Vector("players")) =>
        val request = readJsonBody[CreatePlayerRequest](exchange)
        val player = app.playerService.registerPlayer(
          userId = request.userId,
          nickname = request.nickname,
          rank = request.toRankSnapshot,
          initialElo = request.initialElo
        )
        sendJson(exchange, 201, player)

      case ("GET", Vector("clubs")) =>
        sendJson(exchange, 200, app.clubRepository.findAll())
      case ("GET", Vector("clubs", clubId)) =>
        sendOption(exchange, app.clubRepository.findById(ClubId(clubId)))
      case ("POST", Vector("clubs")) =>
        val request = readJsonBody[CreateClubRequest](exchange)
        val club = app.clubService.createClub(
          name = request.name,
          creatorId = request.creator
        )
        sendJson(exchange, 201, club)
      case ("POST", Vector("clubs", clubId, "members")) =>
        val request = readJsonBody[AddClubMemberRequest](exchange)
        sendOption(exchange, app.clubService.addMember(ClubId(clubId), request.player))

      case ("GET", Vector("tournaments")) =>
        sendJson(exchange, 200, app.tournamentRepository.findAll())
      case ("GET", Vector("tournaments", tournamentId)) =>
        sendOption(exchange, app.tournamentRepository.findById(TournamentId(tournamentId)))
      case ("POST", Vector("tournaments")) =>
        val request = readJsonBody[CreateTournamentRequest](exchange)
        val tournament = app.tournamentService.createTournament(
          name = request.name,
          organizer = request.organizer,
          startsAt = request.startsAt,
          endsAt = request.endsAt,
          stages = request.toStages
        )
        sendJson(exchange, 201, tournament)
      case ("POST", Vector("tournaments", tournamentId, "publish")) =>
        sendOption(exchange, app.tournamentService.publishTournament(TournamentId(tournamentId)))
      case ("POST", Vector("tournaments", tournamentId, "start")) =>
        sendOption(exchange, app.tournamentService.startTournament(TournamentId(tournamentId)))
      case ("POST", Vector("tournaments", tournamentId, "players", playerId)) =>
        sendOption(
          exchange,
          app.tournamentService.registerPlayer(TournamentId(tournamentId), PlayerId(playerId))
        )
      case ("POST", Vector("tournaments", tournamentId, "clubs", clubId)) =>
        sendOption(
          exchange,
          app.tournamentService.registerClub(TournamentId(tournamentId), ClubId(clubId))
        )
      case ("POST", Vector("tournaments", tournamentId, "stages", stageId, "schedule")) =>
        sendJson(
          exchange,
          200,
          app.tournamentService.scheduleStageTables(TournamentId(tournamentId), TournamentStageId(stageId))
        )

      case ("GET", Vector("tables")) =>
        sendJson(exchange, 200, app.tableRepository.findAll())
      case ("GET", Vector("tables", tableId)) =>
        sendOption(exchange, app.tableRepository.findById(TableId(tableId)))
      case ("POST", Vector("tables", tableId, "start")) =>
        sendOption(exchange, app.tableService.startTable(TableId(tableId)))

      case ("GET", Vector("dashboards", "players", playerId)) =>
        sendOption(exchange, app.dashboardRepository.findByOwner(DashboardOwner.Player(PlayerId(playerId))))
      case ("GET", Vector("dashboards", "clubs", clubId)) =>
        sendOption(exchange, app.dashboardRepository.findByOwner(DashboardOwner.Club(ClubId(clubId))))

      case _ =>
        sendJson(exchange, 404, ApiError(s"Unsupported route: $method $path"))

  private def readJsonBody[T: Reader](exchange: HttpExchange): T =
    val body = Using.resource(exchange.getRequestBody) { inputStream =>
      String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
    }

    if body.trim.isEmpty then
      throw IllegalArgumentException("Request body is required")
    else read[T](body)

  private def sendOption[T: Writer](exchange: HttpExchange, value: Option[T]): Unit =
    value match
      case Some(actual) => sendJson(exchange, 200, actual)
      case None         => sendJson(exchange, 404, ApiError("Resource not found"))

  private def sendJson[T: Writer](exchange: HttpExchange, statusCode: Int, payload: T): Unit =
    val json = write(payload, indent = 2)
    val bytes = json.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type", "application/json; charset=utf-8")
    exchange.sendResponseHeaders(statusCode, bytes.length.toLong)
    Using.resource(exchange.getResponseBody) { output =>
      output.write(bytes)
    }
