package riichinexus.microservices.publicquery.api

import riichinexus.microservices.publicquery.objects.PublicScheduleQuery
import riichinexus.microservices.publicquery.api.responses.PublicQueryResponses.PublicScheduleResponse

object PublicScheduleApi:

  def listSchedules(
      service: PublicQueryService,
      query: PublicScheduleQuery
  ): Vector[PublicScheduleResponse] =
    service.publicSchedules()
      .filter(schedule => query.tournamentStatus.forall(_ == schedule.tournamentStatus))
      .filter(schedule => query.stageStatus.forall(_ == schedule.stageStatus))
      .sortBy(schedule => (schedule.startsAt, schedule.tournamentName, schedule.stageName))
