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
import riichinexus.microservices.tournament.objects.paifumanagement.PaifuAction

object PaifuActionFunctions:
  def validate(action: PaifuAction): Unit =
    require(action.sequenceNo >= 1, "Paifu action sequence number must be positive")
    action.tile.foreach(PaifuTileFunctions.validate)
    action.shantenAfterAction.foreach { value =>
      require(value >= -1 && value <= 13, "Shanten value must be between -1 and 13")
    }
    action.handTilesAfterAction.foreach { tiles =>
      require(tiles.nonEmpty, "Paifu action hand snapshot cannot be empty when provided")
      require(tiles.size >= 1 && tiles.size <= 14, "Paifu action hand snapshot must contain between 1 and 14 tiles")
      PaifuTileFunctions.validateAll(tiles, "Paifu action hand snapshot")
    }
    PaifuTileFunctions.validateAll(action.revealedTiles, "Paifu action revealed tiles")
