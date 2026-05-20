package riichinexus.microservices.tournament.api

import java.util.NoSuchElementException

import cats.effect.IO
import riichinexus.api.{APIMessage, ApiPlanContext}
import riichinexus.domain.model.*
import riichinexus.infrastructure.json.JsonCodecs.given
import riichinexus.microservices.tournament.objects.*
import riichinexus.microservices.tournament.objects.apiTypes.*
import riichinexus.microservices.tournament.objects.apiTypes.ManagementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.SettlementRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.StageRequests.given
import riichinexus.microservices.tournament.objects.apiTypes.TableRequests.given
import riichinexus.system.objects.PagedResponse
import upickle.default.*

final case class TournamentWhitelistListAPIMessage(
    tournamentId: String,
    participantKind: Option[String] = None,
    playerId: Option[String] = None,
    clubId: Option[String] = None,
    limit: Option[Int] = None,
    offset: Option[Int] = None
) extends APIMessage[PagedResponse[TournamentWhitelistEntryView]] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[PagedResponse[TournamentWhitelistEntryView]] =
    IO {
      val query = TournamentWhitelistQuery(
        participantKind = participantKind.filter(_.nonEmpty).map(TournamentParticipantKind.valueOf),
        playerId = playerId.filter(_.nonEmpty).map(PlayerId(_)),
        clubId = clubId.filter(_.nonEmpty).map(ClubId(_))
      )
      val tournamentIdValue = TournamentId(tournamentId)
      val whitelist = context.support.tournamentModule.tables
        .findTournament(tournamentIdValue)
        .map(_.whitelist
          .filter(entry => query.participantKind.forall(_ == entry.participantKind))
          .filter(entry => query.playerId.forall(id => entry.playerId.contains(id)))
          .filter(entry => query.clubId.forall(id => entry.clubId.contains(id)))
        )
        .getOrElse(throw NoSuchElementException(s"Tournament ${tournamentIdValue.value} was not found"))
        .map(TournamentWhitelistEntryView.fromDomain)
      page(whitelist, filters(participantKind.filter(_.nonEmpty).map("participantKind" -> _), playerId.filter(_.nonEmpty).map("playerId" -> _), clubId.filter(_.nonEmpty).map("clubId" -> _)))
    }

  private def page(items: Vector[TournamentWhitelistEntryView], appliedFilters: Map[String, String]): PagedResponse[TournamentWhitelistEntryView] =
    val resolvedLimit = limit.getOrElse(20)
    val resolvedOffset = offset.getOrElse(0)
    require(resolvedLimit > 0, "Input field limit must be positive")
    require(resolvedOffset >= 0, "Input field offset must be non-negative")
    val boundedLimit = math.min(resolvedLimit, 100)
    val pageItems = items.slice(resolvedOffset, resolvedOffset + boundedLimit)
    PagedResponse(pageItems, items.size, boundedLimit, resolvedOffset, resolvedOffset + pageItems.size < items.size, appliedFilters)

  private def filters(values: Option[(String, String)]*): Map[String, String] =
    values.flatten.toMap
