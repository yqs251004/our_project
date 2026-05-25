package riichinexus.api

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.{Host, Port, host, port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server

import java.util.concurrent.atomic.AtomicReference
import scala.annotation.tailrec
import scala.concurrent.duration.*

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
      runtime: ApiRuntimeContext,
      config: ApiServerConfig
  ): Resource[IO, Server] =
    EmberServerBuilder
      .default[IO]
      .withHost(Host.fromString(config.host).getOrElse(host"0.0.0.0"))
      .withPort(Port.fromInt(config.port).getOrElse(port"8080"))
      .withShutdownTimeout(100.millis)
      .withHttpApp(
        ApiHttpApp.build(
          runtime = runtime
        )
      )
      .build

final class ApiServer(
    runtime: ApiRuntimeContext,
    config: ApiServerConfig
):
  private final case class ServerHandle(
      server: Server,
      release: IO[Unit]
  )

  private val primary = AtomicReference(Option.empty[ServerHandle])

  def start(): Unit =
    val current = primary.get()
    if current.isEmpty then
      val (server, release) = ApiServer.resource(runtime, config).allocated.unsafeRunSync()
      val handle = ServerHandle(server, release)
      if !primary.compareAndSet(current, Some(handle)) then
        release.unsafeRunSync()

  def stop(delaySeconds: Int = 0): Unit =
    clearPrimary().foreach(_.release.unsafeRunSync())

  def port: Int =
    primary.get().map(_.server.address.getPort).getOrElse(config.port)

  @tailrec
  private def clearPrimary(): Option[ServerHandle] =
    val current = primary.get()
    if current.isEmpty || primary.compareAndSet(current, None) then current
    else clearPrimary()

final class RiichiNexusApiServer(
    runtime: ApiRuntimeContext,
    config: ApiServerConfig
):
  private val underlying = new ApiServer(runtime, config)

  def start(): Unit =
    underlying.start()

  def stop(delaySeconds: Int = 0): Unit =
    underlying.stop(delaySeconds)

  def port: Int =
    underlying.port
