package riichinexus.microservices.publicquery.api

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.microservices.publicquery.objects.apiTypes.PublicScheduleView
import riichinexus.microservices.publicquery.domain.PublicDirectoryQueries
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class ListPublicSchedulesAPIMessage(
    tournamentStatus: Option[String] = None,
    stageStatus: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[PublicScheduleView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[PublicScheduleView]] =
    for
      query <- IO(resolveQuery(context))
      schedules <- IO(listSchedules(context, query))
    yield PagedResponse.fromItems(schedules, limit, offset, query.appliedFilters)(identity)

  private def resolveQuery(context: ApiPlanContext): ResolvedScheduleQuery =
    context.support.authorizationService
      .requirePermission(AccessPrincipal.guest(), Permission.ViewPublicSchedule)
    ResolvedScheduleQuery(
      tournamentStatus = tournamentStatus.filter(_.nonEmpty).map(
        context.support.parseEnum("tournamentStatus", _)(TournamentStatus.valueOf)
      ),
      stageStatus = stageStatus.filter(_.nonEmpty).map(
        context.support.parseEnum("stageStatus", _)(StageStatus.valueOf)
      ),
      appliedFilters = Vector(
        tournamentStatus.filter(_.nonEmpty).map("tournamentStatus" -> _),
        stageStatus.filter(_.nonEmpty).map("stageStatus" -> _)
      ).flatten.toMap
    )

  private def listSchedules(
      context: ApiPlanContext,
      query: ResolvedScheduleQuery
  ): Vector[PublicScheduleView] =
    PublicDirectoryQueries.publicSchedules(context.connection)
      .filter(schedule => query.tournamentStatus.forall(_.toString == schedule.tournamentStatus))
      .filter(schedule => query.stageStatus.forall(_.toString == schedule.stageStatus))
      .sortBy(schedule => (schedule.startsAt, schedule.tournamentName, schedule.stageName))

  private final case class ResolvedScheduleQuery(
      tournamentStatus: Option[TournamentStatus],
      stageStatus: Option[StageStatus],
      appliedFilters: Map[String, String]
  )
