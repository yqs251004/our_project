package riichinexus.microservices.tournament.domain.paifu.model

/*
* 字牌
* */
private[tournament] enum PaifuHonorTileAlias:
  case East
  case South
  case West
  case North
  case WhiteDragon
  case GreenDragon
  case RedDragon

private[tournament] object PaifuHonorTileAlias:
  def fromString(value: String): Option[PaifuHonorTileAlias] =
    value.trim.toLowerCase.replace("-", "") match
      case "east" | "ton" | "e" =>
        Some(PaifuHonorTileAlias.East)
      case "south" | "nan" | "s" =>
        Some(PaifuHonorTileAlias.South)
      case "west" | "shaa" | "w" =>
        Some(PaifuHonorTileAlias.West)
      case "north" | "pei" | "n" =>
        Some(PaifuHonorTileAlias.North)
      case "haku" | "p" =>
        Some(PaifuHonorTileAlias.WhiteDragon)
      case "hatsu" | "f" =>
        Some(PaifuHonorTileAlias.GreenDragon)
      case "chun" | "c" =>
        Some(PaifuHonorTileAlias.RedDragon)
      case _ =>
        None

  def toString(alias: PaifuHonorTileAlias): String =
    alias match
      case PaifuHonorTileAlias.East        => "east"
      case PaifuHonorTileAlias.South       => "south"
      case PaifuHonorTileAlias.West        => "west"
      case PaifuHonorTileAlias.North       => "north"
      case PaifuHonorTileAlias.WhiteDragon => "haku"
      case PaifuHonorTileAlias.GreenDragon => "hatsu"
      case PaifuHonorTileAlias.RedDragon   => "chun"
