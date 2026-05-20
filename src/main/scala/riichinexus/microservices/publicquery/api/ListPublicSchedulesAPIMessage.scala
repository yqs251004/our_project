package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.objects.apiTypes.PublicScheduleView
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListPublicSchedulesAPIMessage(
    tournamentStatus: Option[String] = None,
    stageStatus: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicScheduleView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicScheduleView]] =
    IO {
      val module = context.support.publicQueryModule
      context.support.routeContext.authorizationService
        .requirePermission(AccessPrincipal.guest(), Permission.ViewPublicSchedule)

      val parsedTournamentStatus = tournamentStatus.filter(_.nonEmpty).map(
        context.support.parseEnum("tournamentStatus", _)(TournamentStatus.valueOf)
      )
      val parsedStageStatus = stageStatus.filter(_.nonEmpty).map(
        context.support.parseEnum("stageStatus", _)(StageStatus.valueOf)
      )
      val schedules = module.tables.publicSchedules()
        .filter(schedule => parsedTournamentStatus.forall(_ == schedule.tournamentStatus))
        .filter(schedule => parsedStageStatus.forall(_ == schedule.stageStatus))
        .sortBy(schedule => (schedule.startsAt, schedule.tournamentName, schedule.stageName))
      val resolvedLimit = limit.getOrElse(20)
      val resolvedOffset = offset.getOrElse(0)
      require(resolvedLimit > 0, "Input field limit must be positive")
      require(resolvedOffset >= 0, "Input field offset must be non-negative")
      val boundedLimit = math.min(resolvedLimit, 100)
      val page = schedules.slice(resolvedOffset, resolvedOffset + boundedLimit)

      PagedResponse(
        items = page,
        total = schedules.size,
        limit = boundedLimit,
        offset = resolvedOffset,
        hasMore = resolvedOffset + page.size < schedules.size,
        appliedFilters = Vector(
          tournamentStatus.filter(_.nonEmpty).map("tournamentStatus" -> _),
          stageStatus.filter(_.nonEmpty).map("stageStatus" -> _)
        ).flatten.toMap
      )
    }
