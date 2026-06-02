package riichinexus.system.api

import scala.annotation.tailrec
import scala.concurrent.duration.*
import java.util.concurrent.atomic.AtomicReference

import cats.effect.{IO, Resource}
import cats.effect.unsafe.implicits.global
import com.comcast.ip4s.{Host, Port, host, port}
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.server.Server
import riichinexus.system.api.http.ApiHttpApp

final case class ApiServerConfig(
    host: String,
    port: Int,
    storageLabel: String,
    corsAllowOrigin: String = "*"
)

final case class ApiServerHandle(
    server: Server,
    release: IO[Unit]
)

final case class ApiServerState(
    runtime: ApiRuntimeContext,
    config: ApiServerConfig,
    primary: AtomicReference[Option[ApiServerHandle]]
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

object ApiServerState:

  def from(runtime: ApiRuntimeContext, config: ApiServerConfig): ApiServerState =
    ApiServerState(
      runtime = runtime,
      config = config,
      primary = AtomicReference(Option.empty[ApiServerHandle])
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
      .withHttpApp(ApiHttpApp.build(runtime = runtime))
      .build

  def start(apiServer: ApiServerState): Unit =
    val current = apiServer.primary.get()
    if current.isEmpty then
      val (server, release) = resource(apiServer.runtime, apiServer.config).allocated.unsafeRunSync()
      val handle = ApiServerHandle(server, release)
      if !apiServer.primary.compareAndSet(current, Some(handle)) then
        release.unsafeRunSync()

  def stop(apiServer: ApiServerState, delaySeconds: Int = 0): Unit =
    clearPrimary(apiServer).foreach(_.release.unsafeRunSync())

  def port(apiServer: ApiServerState): Int =
    apiServer.primary.get().map(_.server.address.getPort).getOrElse(apiServer.config.port)

  @tailrec
  private def clearPrimary(apiServer: ApiServerState): Option[ApiServerHandle] =
    val current = apiServer.primary.get()
    if current.isEmpty || apiServer.primary.compareAndSet(current, None) then current
    else clearPrimary(apiServer)
