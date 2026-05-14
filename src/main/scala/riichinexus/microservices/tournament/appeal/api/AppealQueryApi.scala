package riichinexus.microservices.tournament.appeal.api

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.appeal.objects.*
import riichinexus.microservices.tournament.appeal.tables.TournamentAppealTables

object AppealQueryApi:

  def listAppeals(tables: TournamentAppealTables, query: AppealListQuery): Vector[AppealTicket] =
    tables.listAppeals(query)

  def findAppeal(tables: TournamentAppealTables, ticketId: AppealTicketId): Option[AppealTicket] =
    tables.findAppeal(ticketId)
