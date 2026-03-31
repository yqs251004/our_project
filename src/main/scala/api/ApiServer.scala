package api

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.{Host, Port, host, port}
import database.ApplicationContext
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

import scala.concurrent.duration.*
import scala.util.Try

final case class ApiServerConfig(
    host: String,
    port: Int,
    storageLabel: String,
    corsAllowOrigin: String = "*"
)

object ApiServerConfig:
  def fromEnv(env: collection.Map[String, String] = sys.env): ApiServerConfig =
    ApiServerConfig(
      host =
        env.get("HOST")
          .orElse(env.get("API_HOST"))
          .orElse(env.get("RIICHI_HOST"))
          .orElse(env.get("RIICHI_API_HOST"))
          .map(_.trim)
          .filter(_.nonEmpty)
          .getOrElse("0.0.0.0"),
      port =
        env.get("PORT")
          .orElse(env.get("API_PORT"))
          .orElse(env.get("RIICHI_PORT"))
          .orElse(env.get("RIICHI_API_PORT"))
          .flatMap(_.trim.toIntOption)
          .getOrElse(8080),
      storageLabel =
        env.get("RIICHI_STORAGE")
          .orElse(env.get("STORAGE_LABEL"))
          .map(_.trim)
          .filter(_.nonEmpty)
          .getOrElse("memory"),
      corsAllowOrigin =
        env.get("CORS_ALLOW_ORIGIN")
          .orElse(env.get("RIICHI_CORS_ALLOW_ORIGIN"))
          .map(_.trim)
          .filter(_.nonEmpty)
          .getOrElse("*")
    )

object ApiServer:

  def resource(
      app: ApplicationContext,
      config: ApiServerConfig
  ): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(config.host).getOrElse(host"0.0.0.0"))
      .withPort(Port.fromInt(config.port).getOrElse(port"8080"))
      .withShutdownTimeout(100.millis)
      .withHttpApp(
        ApiHttpApp.build(
          app = app,
          storageLabel = config.storageLabel,
          corsAllowOrigin = config.corsAllowOrigin
        )
      )
      .build

final class ApiServer(
    app: ApplicationContext,
    config: ApiServerConfig
):
  private var primaryServer: Option[Server] = None
  private var primaryRelease: Option[IO[Unit]] = None

  def start(): Unit =
    if primaryServer.isEmpty then
      val (server, release) = ApiServer.resource(app, config).allocated.unsafeRunSync()
      primaryServer = Some(server)
      primaryRelease = Some(release)

  def stop(delaySeconds: Int = 0): Unit =
    primaryRelease.foreach(_.unsafeRunSync())
    primaryRelease = None
    primaryServer = None

  def port: Int =
    primaryServer.map(_.address.getPort).getOrElse(config.port)
