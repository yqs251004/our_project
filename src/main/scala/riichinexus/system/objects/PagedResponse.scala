package riichinexus.system.objects

final case class PagedResponse[T](
    items: Vector[T],
    total: Int,
    limit: Int,
    offset: Int,
    hasMore: Boolean,
    appliedFilters: Map[String, String] = Map.empty
)

object PagedResponse:
  export SharedResponseCodecs.given
