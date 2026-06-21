package riichinexus.system.objects

import java.time.Instant

/** 健康检查接口返回的服务状态快照。 */
final case class HealthResponse(
    status: String,
    storage: String,
    timestamp: Instant,
    service: String = "riichi-nexus"
)

object HealthResponse:
  export riichinexus.system.json.SharedResponseCodecs.given
