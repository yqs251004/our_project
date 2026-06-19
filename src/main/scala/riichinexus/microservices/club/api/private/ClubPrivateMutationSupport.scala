package riichinexus.microservices.club.api.`private`

import cats.effect.IO
import riichinexus.microservices.club.domain.Club
import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.tables.clubs.ClubTable
import riichinexus.system.api.ApiPlanContext

private[club] object ClubPrivateMutationSupport:
  def updateClub(
      context: ApiPlanContext,
      clubId: ClubId
  )(update: Club => Club): IO[Option[Club]] =
    for
      club <- IO.blocking(ClubTable.findById(context.connection, clubId))
      saved <- club match
        case Some(existing) =>
          IO.blocking(ClubTable.save(context.connection, update(existing))).map(Some(_))
        case None =>
          IO.pure(None)
    yield saved
