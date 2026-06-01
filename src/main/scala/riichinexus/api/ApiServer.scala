package riichinexus.api

import java.util.concurrent.atomic.AtomicReference

import cats.effect.IO
import org.http4s.server.Server

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
