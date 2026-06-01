package riichinexus.api.functions

import riichinexus.api.ApiServerConfig

object ApiServerConfigFunctions:

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
