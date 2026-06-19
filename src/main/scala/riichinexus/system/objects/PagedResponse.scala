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
  export riichinexus.system.json.SharedResponseCodecs.given

  def fromItems[A, B](
      items: Vector[A],
      limit: Option[Int],
      offset: Option[Int],
      appliedFilters: Map[String, String] = Map.empty
  )(mapItem: A => B): PagedResponse[B] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val page = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(
      page.map(mapItem),
      items.size,
      boundedLimit,
      resolvedOffset,
      resolvedOffset + page.size < items.size,
      appliedFilters
    )
