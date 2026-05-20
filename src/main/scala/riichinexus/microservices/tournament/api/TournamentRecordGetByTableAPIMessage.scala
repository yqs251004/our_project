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
import upickle.default.*

final case class TournamentRecordGetByTableAPIMessage(tableId: String) extends APIMessage[TournamentMatchRecordView] derives ReadWriter:

  override def plan(context: ApiPlanContext): IO[TournamentMatchRecordView] =
    IO {
      context.support.tournamentModule.tables.findMatchRecordByTable(TableId(tableId))
        .map(TournamentMatchRecordView.fromDomain)
        .getOrElse(throw NoSuchElementException("Resource not found"))
    }
