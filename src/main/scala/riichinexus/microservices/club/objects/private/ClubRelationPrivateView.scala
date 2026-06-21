package riichinexus.microservices.club.objects.`private`

import riichinexus.microservices.club.objects.clubmanagement.ClubId
import riichinexus.microservices.club.objects.relationmanagement.ClubRelationKind

/** 内部俱乐部快照中的一条关系边。
  *
  * 使用强类型 `ClubId` 指向目标俱乐部，方便后端在刷新公开目录或计算关系筛选时避免字符串 ID 混用。
  */
final case class ClubRelationPrivateView(
    targetClubId: ClubId,
    relation: ClubRelationKind
)
