package riichinexus.microservices.tournament.domain.paifu.functions


import riichinexus.microservices.tournament.objects.paifu.PaifuAction

/** PaifuActionFunctions 提供牌谱动作相关的领域计算、校验和转换函数。 */

private[tournament] object PaifuActionFunctions:
  def validate(action: PaifuAction): Unit =
    require(action.sequenceNo >= 1, "Paifu action sequence number must be positive")
    action.tile.foreach(PaifuTileFunctions.validate)
    action.shantenAfterAction.foreach { value =>
      require(value >= -1 && value <= 13, "Shanten value must be between -1 and 13")
    }
    action.targetSequenceNo.foreach { value =>
      require(value >= 1, "Paifu action target sequence number must be positive")
      require(value < action.sequenceNo, "Paifu action target sequence number must reference an earlier action")
    }
    action.handTilesAfterAction.foreach { tiles =>
      require(tiles.nonEmpty, "Paifu action hand snapshot cannot be empty when provided")
      require(tiles.size >= 1 && tiles.size <= 14, "Paifu action hand snapshot must contain between 1 and 14 tiles")
      PaifuTileFunctions.validateAll(tiles, "Paifu action hand snapshot")
    }
    PaifuTileFunctions.validateAll(action.revealedTiles, "Paifu action revealed tiles")
