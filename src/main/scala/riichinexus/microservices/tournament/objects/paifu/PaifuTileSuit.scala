package riichinexus.microservices.tournament.objects.paifu

/** Public paifu tile suit values encoded as m, p, s, and z. */

enum PaifuTileSuit:
  case Manzu
  case Pinzu
  case Souzu
  case Honor

object PaifuTileSuit:
  def toString(suit: PaifuTileSuit): String =
    suit match
      case PaifuTileSuit.Manzu => "m"
      case PaifuTileSuit.Pinzu => "p"
      case PaifuTileSuit.Souzu => "s"
      case PaifuTileSuit.Honor => "z"

  def fromString(value: String): PaifuTileSuit =
    value.trim.toLowerCase match
      case "m" => PaifuTileSuit.Manzu
      case "p" => PaifuTileSuit.Pinzu
      case "s" => PaifuTileSuit.Souzu
      case "z" => PaifuTileSuit.Honor
      case other =>
        throw IllegalArgumentException(
          s"Unsupported PaifuTileSuit value: $other"
        )
