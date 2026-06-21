package riichinexus.microservices.tournament.objects.paifu

/** 牌谱中麻将牌花色的公开编码。
  *
  * 序列化时使用 m、p、s、z 表示万、筒、索和字牌，便于前端牌面渲染与牌谱导入保持一致。
  */
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
