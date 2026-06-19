package riichinexus.system.json

import riichinexus.system.objects.{ErrorResponse, HealthResponse, PagedResponse}
import riichinexus.system.json.SharedJsonCodecs.given
import upickle.default.{ReadWriter, Reader, Writer, macroR, macroRW, macroW}

object SharedResponseCodecs:
  given ReadWriter[ErrorResponse] = macroRW
  given ReadWriter[HealthResponse] = macroRW
  given [T: Reader]: Reader[PagedResponse[T]] = macroR
  given [T: Writer]: Writer[PagedResponse[T]] = macroW
