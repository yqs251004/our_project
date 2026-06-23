package riichinexus.system.json

import java.time.Instant

import riichinexus.microservices.club.domain.profile.model.Club
import riichinexus.microservices.club.domain.profile.model.ClubHonor
import riichinexus.microservices.club.domain.membership.model.{ClubMemberContribution, ClubMembershipApplication, ClubRecruitmentPolicy, ClubTitleAssignment}
import riichinexus.microservices.club.domain.rankprivilege.model.ClubMemberPrivilegeSnapshot
import riichinexus.microservices.club.domain.relation.model.ClubRelation
import riichinexus.microservices.club.objects.membership.{ClubApplicationStatus, MembershipApplicationId}
import riichinexus.microservices.club.objects.membership.ClubApplicationReviewDecision
import riichinexus.microservices.club.objects.rankprivilege.ClubPrivilegeCode
import riichinexus.microservices.club.objects.rankprivilege.ClubRankNode
import riichinexus.microservices.club.objects.relation.ClubRelationKind
import riichinexus.microservices.club.objects.participation.{ClubTournamentParticipationStatus, ClubTournamentScope}
import riichinexus.microservices.player.objects.PlayerId
import riichinexus.system.json.JsonCodecSupport.stringEnumReadWriter
import riichinexus.system.json.SharedJsonCodecs.given
import upickle.default.{ReadWriter, macroRW, read, readwriter, writeJs}

object ClubJsonCodecs:
  given ReadWriter[ClubApplicationStatus] =
    stringEnumReadWriter(
      ClubApplicationStatus.fromString,
      ClubApplicationStatus.toString
    )
  given ReadWriter[ClubMembershipApplication] =
    readwriter[ujson.Value].bimap[ClubMembershipApplication](
      application =>
        ujson.Obj.from(
          Vector(
            "id" -> writeJs(application.id),
            "playerId" -> writeJs(application.playerId),
            "displayName" -> writeJs(application.displayName),
            "submittedAt" -> writeJs(application.submittedAt),
            "message" -> writeJs(application.message),
            "status" -> writeJs(application.status),
            "reviewedBy" -> writeJs(application.reviewedBy),
            "reviewedAt" -> writeJs(application.reviewedAt),
            "reviewNote" -> writeJs(application.reviewNote),
            "withdrawnByPrincipalId" -> writeJs(application.withdrawnByPrincipalId)
          ) ++ application.applicantUserId.map("applicantUserId" -> writeJs(_)).toVector
        ),
      {
        case obj: ujson.Obj =>
          def optional[A: ReadWriter](name: String): Option[A] =
            obj.value.get(name).fold(Option.empty[A])(read[Option[A]](_))

          ClubMembershipApplication(
            id = read[MembershipApplicationId](obj("id")),
            playerId = optional[PlayerId]("playerId"),
            applicantUserId = optional[String]("applicantUserId"),
            displayName = read[String](obj("displayName")),
            submittedAt = read[Instant](obj("submittedAt")),
            message = optional[String]("message"),
            status = obj.value.get("status").fold(ClubApplicationStatus.Pending)(read[ClubApplicationStatus](_)),
            reviewedBy = optional[PlayerId]("reviewedBy"),
            reviewedAt = optional[Instant]("reviewedAt"),
            reviewNote = optional[String]("reviewNote"),
            withdrawnByPrincipalId = optional[String]("withdrawnByPrincipalId")
          )
        case json =>
          throw upickle.core.Abort(s"Expected ClubMembershipApplication object, got $json")
      }
    )
  given ReadWriter[ClubPrivilegeCode] =
    stringEnumReadWriter(ClubPrivilegeCode.fromString, ClubPrivilegeCode.toString)
  given ReadWriter[ClubRankNode] = macroRW
  given ReadWriter[ClubMemberContribution] = macroRW
  given ReadWriter[ClubMemberPrivilegeSnapshot] = macroRW
  given ReadWriter[ClubTitleAssignment] = macroRW
  given ReadWriter[ClubRelationKind] =
    stringEnumReadWriter(ClubRelationKind.fromString, ClubRelationKind.toString)
  given ReadWriter[ClubTournamentScope] =
    stringEnumReadWriter(ClubTournamentScope.fromString, ClubTournamentScope.toString)
  given ReadWriter[ClubTournamentParticipationStatus] =
    stringEnumReadWriter(
      ClubTournamentParticipationStatus.fromString,
      ClubTournamentParticipationStatus.toString
    )
  given ReadWriter[ClubApplicationReviewDecision] =
    stringEnumReadWriter(
      ClubApplicationReviewDecision.fromString,
      ClubApplicationReviewDecision.toString
    )
  given ReadWriter[ClubRelation] = macroRW
  given ReadWriter[ClubRecruitmentPolicy] = macroRW
  given ReadWriter[ClubHonor] = macroRW
  given ReadWriter[Club] = macroRW
