package riichinexus.microservices.tournament.domain.paifu.functions


import riichinexus.microservices.tournament.objects.paifumanagement.PaifuMetadata

/** PaifuMetadataFunctions 提供牌谱Metadata相关的领域计算、校验和转换函数。 */

private[tournament] object PaifuMetadataFunctions:
  def validate(metadata: PaifuMetadata): Unit =
    require(metadata.source.trim.nonEmpty, "Paifu source cannot be empty")
    require(metadata.seats.size == 4, "Paifu metadata must contain four seats")
    require(metadata.seats.map(_.playerId).distinct.size == metadata.seats.size, "Paifu seats must contain unique players")
    require(metadata.seats.map(_.seat).distinct.size == metadata.seats.size, "Paifu seats must contain unique winds")
