package riichinexus.microservices.tournament.domain.paifumanagement.functions

import riichinexus.domain.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.model.*
import riichinexus.microservices.tournament.domain.recordmanagement.model.*
import riichinexus.microservices.tournament.domain.settlementmanagement.model.*
import riichinexus.microservices.tournament.domain.tablemanagement.model.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.model.*
import riichinexus.microservices.tournament.domain.lineupmanagement.functions.*
import riichinexus.microservices.tournament.domain.paifumanagement.functions.*
import riichinexus.microservices.tournament.domain.recordmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.knockout.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.ranking.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.stageprogression.*
import riichinexus.microservices.tournament.domain.rulesmanagement.functions.swiss.*
import riichinexus.microservices.tournament.domain.settlementmanagement.functions.*
import riichinexus.microservices.tournament.domain.tablemanagement.functions.*
import riichinexus.microservices.tournament.domain.tournamentmanagement.functions.*
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuMetadata

object PaifuMetadataFunctions:
  def validate(metadata: PaifuMetadata): Unit =
    require(metadata.source.trim.nonEmpty, "Paifu source cannot be empty")
    require(metadata.seats.size == 4, "Paifu metadata must contain four seats")
    require(metadata.seats.map(_.playerId).distinct.size == metadata.seats.size, "Paifu seats must contain unique players")
    require(metadata.seats.map(_.seat).distinct.size == metadata.seats.size, "Paifu seats must contain unique winds")
