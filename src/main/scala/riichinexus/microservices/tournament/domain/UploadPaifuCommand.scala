package riichinexus.microservices.tournament.domain

import riichinexus.microservices.auth.domain.model.AccessPrincipal
import riichinexus.domain.model.TableId
import riichinexus.microservices.tournament.domain.model.Paifu

final case class UploadPaifuCommand(
    tableId: TableId,
    actor: AccessPrincipal,
    paifu: Paifu
)
