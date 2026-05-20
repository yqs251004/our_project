package riichinexus.system.objects.apiTypes

import riichinexus.infrastructure.json.JsonCodecs.given
import upickle.default.*

object SharedResponseCodecs:
  given ReadWriter[ErrorResponse] = macroRW
  given ReadWriter[HealthResponse] = macroRW
  given [T: Reader]: Reader[PagedResponse[T]] = macroR
  given [T: Writer]: Writer[PagedResponse[T]] = macroW
