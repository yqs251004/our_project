package riichinexus.api

import _root_.api.{ApiServer => TemplateApiServer, ApiServerConfig => TemplateApiServerConfig}
import _root_.database.ApplicationContext

final case class ApiServerConfig(
    host: String,
    port: Int,
    storageLabel: String,
    corsAllowOrigin: String = "*"
):
  def toTemplateConfig: TemplateApiServerConfig =
    TemplateApiServerConfig(
      host = host,
      port = port,
      storageLabel = storageLabel,
      corsAllowOrigin = corsAllowOrigin
    )

object ApiServerConfig:
  def fromEnv(env: collection.Map[String, String] = sys.env): ApiServerConfig =
    val config = TemplateApiServerConfig.fromEnv(env)
    ApiServerConfig(
      host = config.host,
      port = config.port,
      storageLabel = config.storageLabel,
      corsAllowOrigin = config.corsAllowOrigin
    )

final class RiichiNexusApiServer(
    app: ApplicationContext,
    config: ApiServerConfig
):
  private val underlying = new TemplateApiServer(app, config.toTemplateConfig)

  def start(): Unit =
    underlying.start()

  def stop(delaySeconds: Int = 0): Unit =
    underlying.stop(delaySeconds)

  def port: Int =
    underlying.port
