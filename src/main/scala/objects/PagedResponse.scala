package objects

import upickle.default.*

final case class PagedResponse[T](
    items: Vector[T],
    total: Int,
    limit: Int,
    offset: Int,
    hasMore: Boolean,
    appliedFilters: Map[String, String] = Map.empty
)

object PagedResponse:
  given [T: Reader]: Reader[PagedResponse[T]] = macroR
  given [T: Writer]: Writer[PagedResponse[T]] = macroW
