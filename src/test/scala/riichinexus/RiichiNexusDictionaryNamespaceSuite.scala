package riichinexus

import java.time.Instant

import munit.FunSuite

import riichinexus.bootstrap.ApplicationContext
import riichinexus.domain.model.*

class RiichiNexusDictionaryNamespaceSuite extends FunSuite with RiichiNexusSuiteSupport:

  test("dictionary namespace workflow governs metadata writes while runtime registry stays strict") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T15:00:00Z")

    val root = playerService(app).registerPlayer("dict-root", "DictRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = playerService(app).registerPlayer("dict-owner", "DictOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val outsider = playerService(app).registerPlayer("dict-outsider", "DictOutsider", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).upsertDictionary(
        key = "rating.elo.kFactor",
        value = "oops",
        actor = AccessPrincipal.system,
        updatedAt = now
      )
    }

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).upsertDictionary(
        key = "rating.elo.experimentalFactor",
        value = "72",
        actor = principalFor(app, superAdmin.id),
        updatedAt = now.plusSeconds(10)
      )
    }

    val pendingNamespace = dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = owner.asPrincipal,
      requestedAt = now.plusSeconds(20),
      note = Some("frontend banners")
    )
    assertEquals(pendingNamespace.status, DictionaryNamespaceReviewStatus.Pending)

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).upsertDictionary(
        key = "ui.banner.message",
        value = "Spring finals this weekend",
        actor = owner.asPrincipal,
        updatedAt = now.plusSeconds(30)
      )
    }

    val approvedNamespace = dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "ui.banner",
      approve = true,
      actor = principalFor(app, superAdmin.id),
      reviewedAt = now.plusSeconds(40),
      note = Some("approved for product copy")
    ).getOrElse(fail("namespace approval missing"))
    assertEquals(approvedNamespace.status, DictionaryNamespaceReviewStatus.Approved)

    val metadata = dictionaryGovernance(app).upsertDictionary(
      key = "ui.banner.message",
      value = "Spring finals this weekend",
      actor = owner.asPrincipal,
      updatedAt = now.plusSeconds(50)
    )

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).upsertDictionary(
        key = "ui.banner.message",
        value = "tampered",
        actor = outsider.asPrincipal,
        updatedAt = now.plusSeconds(60)
      )
    }

    val transferredNamespace = dictionaryGovernance(app).transferDictionaryNamespace(
      namespacePrefix = "ui.banner",
      newOwnerId = outsider.id,
      actor = principalFor(app, superAdmin.id),
      note = Some("product team handoff"),
      transferredAt = now.plusSeconds(70)
    ).getOrElse(fail("namespace transfer missing"))
    assertEquals(transferredNamespace.ownerPlayerId, outsider.id)
    assertEquals(transferredNamespace.status, DictionaryNamespaceReviewStatus.Approved)

    val formerOwnerWrite = dictionaryGovernance(app).upsertDictionary(
      key = "ui.banner.message",
      value = "former owner co-owner write",
      actor = owner.asPrincipal,
      updatedAt = now.plusSeconds(80)
    )

    val transferredWrite = dictionaryGovernance(app).upsertDictionary(
      key = "ui.banner.message",
      value = "Summer finals this weekend",
      actor = outsider.asPrincipal,
      updatedAt = now.plusSeconds(90)
    )

    val revokedNamespace = dictionaryGovernance(app).revokeDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = principalFor(app, superAdmin.id),
      note = Some("namespace retired"),
      revokedAt = now.plusSeconds(100)
    ).getOrElse(fail("namespace revoke missing"))
    assertEquals(revokedNamespace.status, DictionaryNamespaceReviewStatus.Revoked)

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).upsertDictionary(
        key = "ui.banner.message",
        value = "revoked namespace blocked",
        actor = outsider.asPrincipal,
        updatedAt = now.plusSeconds(110)
      )
    }

    val namespaceAuditTypes = auditEventRepository(app).findByAggregate("dictionary-namespace", "ui.banner.").map(_.eventType)
    assert(namespaceAuditTypes.contains("DictionaryNamespaceTransferred"))
    assert(namespaceAuditTypes.contains("DictionaryNamespaceRevoked"))
    assertEquals(metadata.key, "ui.banner.message")
    assertEquals(metadata.value, "Spring finals this weekend")
    assertEquals(formerOwnerWrite.updatedBy, owner.id)
    assertEquals(transferredWrite.updatedBy, outsider.id)
  }

  test("dictionary namespace collaborators can write metadata and co-owners can manage editors") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T15:20:00Z")

    val root = playerService(app).registerPlayer("dict-collab-root", "DictCollabRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = playerService(app).registerPlayer("dict-collab-owner", "DictCollabOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val coOwner = playerService(app).registerPlayer("dict-collab-coowner", "DictCollabCoOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val editor = playerService(app).registerPlayer("dict-collab-editor", "DictCollabEditor", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val replacementEditor = playerService(app).registerPlayer("dict-collab-replacement", "DictCollabReplacement", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1570)
    val outsider = playerService(app).registerPlayer("dict-collab-outsider", "DictCollabOutsider", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1560)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

    val pending = dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = owner.asPrincipal,
      coOwnerPlayerIds = Vector(coOwner.id),
      editorPlayerIds = Vector(editor.id),
      requestedAt = now.plusSeconds(10)
    )
    assertEquals(pending.coOwnerPlayerIds, Vector(coOwner.id))
    assertEquals(pending.editorPlayerIds, Vector(editor.id))

    dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "ui.banner",
      approve = true,
      actor = principalFor(app, superAdmin.id),
      reviewedAt = now.plusSeconds(20)
    ).getOrElse(fail("namespace approval missing"))

    val coOwnerWrite = dictionaryGovernance(app).upsertDictionary(
      key = "ui.banner.message",
      value = "co-owner copy",
      actor = coOwner.asPrincipal,
      updatedAt = now.plusSeconds(30)
    )
    val editorWrite = dictionaryGovernance(app).upsertDictionary(
      key = "ui.banner.subtitle",
      value = "editor copy",
      actor = editor.asPrincipal,
      updatedAt = now.plusSeconds(40)
    )

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).upsertDictionary(
        key = "ui.banner.subtitle",
        value = "outsider blocked",
        actor = outsider.asPrincipal,
        updatedAt = now.plusSeconds(50)
      )
    }

    val updatedCollaborators = dictionaryGovernance(app).updateDictionaryNamespaceCollaborators(
      namespacePrefix = "ui.banner",
      coOwnerPlayerIds = Vector(coOwner.id),
      editorPlayerIds = Vector(replacementEditor.id),
      actor = coOwner.asPrincipal,
      note = Some("rotate content editor"),
      updatedAt = now.plusSeconds(60)
    ).getOrElse(fail("collaborator update missing"))
    assertEquals(updatedCollaborators.editorPlayerIds, Vector(replacementEditor.id))

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).upsertDictionary(
        key = "ui.banner.subtitle",
        value = "old editor blocked",
        actor = editor.asPrincipal,
        updatedAt = now.plusSeconds(70)
      )
    }

    val replacementWrite = dictionaryGovernance(app).upsertDictionary(
      key = "ui.banner.subtitle",
      value = "replacement editor copy",
      actor = replacementEditor.asPrincipal,
      updatedAt = now.plusSeconds(80)
    )

    val auditTypes = auditEventRepository(app).findByAggregate("dictionary-namespace", "ui.banner.").map(_.eventType)
    assert(auditTypes.contains("DictionaryNamespaceCollaboratorsUpdated"))
    assertEquals(coOwnerWrite.updatedBy, coOwner.id)
    assertEquals(editorWrite.updatedBy, editor.id)
    assertEquals(replacementWrite.updatedBy, replacementEditor.id)
  }

  test("dictionary namespace ownership rejects suspended or banned owners") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:30:00Z")

    val root = playerService(app).registerPlayer("dict-owner-safety-root", "OwnerSafetyRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = playerService(app).registerPlayer("dict-owner-safety-owner", "OwnerSafetyOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val suspended = playerService(app).registerPlayer("dict-owner-safety-suspended", "OwnerSafetySuspended", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1500)
    val banned = playerService(app).registerPlayer("dict-owner-safety-banned", "OwnerSafetyBanned", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1490)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))
    playerRepository(app).save(suspended.copy(status = PlayerStatus.Suspended))
    playerRepository(app).save(banned.ban("policy violation"))

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).requestDictionaryNamespace(
        namespacePrefix = "ui.suspended-owner",
        actor = principalFor(app, superAdmin.id),
        ownerPlayerId = Some(suspended.id),
        requestedAt = now.plusSeconds(10)
      )
    }

    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = owner.asPrincipal,
      requestedAt = now.plusSeconds(20)
    )
    dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "ui.banner",
      approve = true,
      actor = principalFor(app, superAdmin.id),
      reviewedAt = now.plusSeconds(30)
    ).getOrElse(fail("namespace approval missing"))

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).transferDictionaryNamespace(
        namespacePrefix = "ui.banner",
        newOwnerId = suspended.id,
        actor = principalFor(app, superAdmin.id),
        transferredAt = now.plusSeconds(40)
      )
    }

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).transferDictionaryNamespace(
        namespacePrefix = "ui.banner",
        newOwnerId = banned.id,
        actor = principalFor(app, superAdmin.id),
        transferredAt = now.plusSeconds(50)
      )
    }
  }

  test("dictionary namespace backlog tracks pending overdue and due-soon reviews") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T16:00:00Z")

    val root = playerService(app).registerPlayer("dict-backlog-root", "DictBacklogRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val ownerA = playerService(app).registerPlayer("dict-backlog-owner-a", "OwnerA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val ownerB = playerService(app).registerPlayer("dict-backlog-owner-b", "OwnerB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = ownerA.asPrincipal,
      requestedAt = now,
      reviewDueAt = Some(now.plusSeconds(3600)),
      note = Some("banner family")
    )
    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "ui.notice",
      actor = ownerA.asPrincipal,
      requestedAt = now.plusSeconds(60),
      reviewDueAt = Some(now.plusSeconds(8 * 3600)),
      note = Some("notice family")
    )
    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "ui.card",
      actor = ownerB.asPrincipal,
      requestedAt = now.plusSeconds(120),
      reviewDueAt = Some(now.plusSeconds(72 * 3600)),
      note = Some("card family")
    )

    val backlog = dictionaryGovernance(app).dictionaryNamespaceBacklog(
      actor = principalFor(app, superAdmin.id),
      asOf = now.plusSeconds(5 * 3600),
      dueSoonWindow = java.time.Duration.ofHours(6)
    )

    assertEquals(backlog.pendingCount, 3)
    assertEquals(backlog.overdueCount, 1)
    assertEquals(backlog.dueSoonCount, 1)
    assertEquals(backlog.oldestPendingRequestedAt, Some(now))
    assertEquals(backlog.nextDueAt, Some(now.plusSeconds(3600)))
    assertEquals(backlog.ownerBacklog.map(_.ownerPlayerId), Vector(ownerA.id, ownerB.id))
    assertEquals(backlog.ownerBacklog.head.overdueCount, 1)
    assertEquals(backlog.ownerBacklog.head.dueSoonCount, 1)
  }

  test("dictionary namespace reminder processing emits due-soon and escalated actions without spamming") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T17:00:00Z")

    val root = playerService(app).registerPlayer("dict-reminder-root", "DictReminderRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val ownerA = playerService(app).registerPlayer("dict-reminder-owner-a", "DictReminderOwnerA", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val ownerB = playerService(app).registerPlayer("dict-reminder-owner-b", "DictReminderOwnerB", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "ui.soon",
      actor = ownerA.asPrincipal,
      requestedAt = now.minusSeconds(600),
      reviewDueAt = Some(now.plusSeconds(2 * 3600))
    )
    dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "ui.legacy",
      actor = ownerB.asPrincipal,
      requestedAt = now.minusSeconds(120 * 3600),
      reviewDueAt = Some(now.minusSeconds(80 * 3600))
    )

    val firstBatch = dictionaryGovernance(app).processDictionaryNamespaceReminders(
      actor = principalFor(app, superAdmin.id),
      asOf = now,
      dueSoonWindow = java.time.Duration.ofHours(6),
      reminderInterval = java.time.Duration.ofHours(12),
      escalationGrace = java.time.Duration.ofHours(72)
    )
    assertEquals(firstBatch.map(_.reminderKind.toString).sorted, Vector(DictionaryNamespaceReminderKind.DueSoon.toString, DictionaryNamespaceReminderKind.Escalated.toString).sorted)
    assertEquals(firstBatch.map(_.namespacePrefix).sorted, Vector("ui.legacy.", "ui.soon."))

    val secondBatch = dictionaryGovernance(app).processDictionaryNamespaceReminders(
      actor = principalFor(app, superAdmin.id),
      asOf = now.plusSeconds(3600),
      dueSoonWindow = java.time.Duration.ofHours(6),
      reminderInterval = java.time.Duration.ofHours(12),
      escalationGrace = java.time.Duration.ofHours(72)
    )
    assertEquals(secondBatch, Vector.empty)

    val reminderEvents = auditEventRepository(app).findByAggregate("dictionary-namespace", "ui.legacy.").filter(_.eventType == "DictionaryNamespaceReminderTriggered")
    assertEquals(reminderEvents.size, 1)
  }

  test("dictionary namespace explicit context club governs collaborators transfers and writes") {
    val app = ApplicationContext.inMemory()
    val now = Instant.parse("2026-03-16T17:30:00Z")

    val root = playerService(app).registerPlayer("dict-context-root", "DictContextRoot", RankSnapshot(RankPlatform.Custom, "S"), now, 2000)
    val owner = playerService(app).registerPlayer("dict-context-owner", "DictContextOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1600)
    val coOwner = playerService(app).registerPlayer("dict-context-coowner", "DictContextCoOwner", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1590)
    val editor = playerService(app).registerPlayer("dict-context-editor", "DictContextEditor", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1580)
    val outsider = playerService(app).registerPlayer("dict-context-outsider", "DictContextOutsider", RankSnapshot(RankPlatform.Tenhou, "4-dan"), now, 1570)
    val superAdmin = playerRepository(app).save(root.grantRole(RoleGrant.superAdmin(now)))

    val club = clubService(app).createClub("Namespace Context Club", owner.id, now, owner.asPrincipal)
    clubService(app).addMember(club.id, coOwner.id, principalFor(app, owner.id))
    clubService(app).addMember(club.id, editor.id, principalFor(app, owner.id))

    val pending = dictionaryGovernance(app).requestDictionaryNamespace(
      namespacePrefix = "ui.banner",
      actor = owner.asPrincipal,
      contextClubId = Some(club.id),
      coOwnerPlayerIds = Vector(coOwner.id),
      editorPlayerIds = Vector(editor.id),
      requestedAt = now.plusSeconds(10)
    )
    assertEquals(pending.contextClubId, Some(club.id))

    dictionaryGovernance(app).reviewDictionaryNamespace(
      namespacePrefix = "ui.banner",
      approve = true,
      actor = principalFor(app, superAdmin.id),
      reviewedAt = now.plusSeconds(20)
    ).getOrElse(fail("namespace approval missing"))

    val editorWrite = dictionaryGovernance(app).upsertDictionary(
      key = "ui.banner.message",
      value = "club editor write",
      actor = editor.asPrincipal,
      updatedAt = now.plusSeconds(30)
    )
    assertEquals(editorWrite.updatedBy, editor.id)

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).updateDictionaryNamespaceCollaborators(
        namespacePrefix = "ui.banner",
        coOwnerPlayerIds = Vector(coOwner.id),
        editorPlayerIds = Vector(outsider.id),
        actor = owner.asPrincipal,
        updatedAt = now.plusSeconds(40)
      )
    }

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).transferDictionaryNamespace(
        namespacePrefix = "ui.banner",
        newOwnerId = outsider.id,
        actor = principalFor(app, superAdmin.id),
        transferredAt = now.plusSeconds(50)
      )
    }

    clubService(app).removeMember(club.id, editor.id, principalFor(app, owner.id))

    intercept[IllegalArgumentException] {
      dictionaryGovernance(app).upsertDictionary(
        key = "ui.banner.message",
        value = "removed editor blocked",
        actor = editor.asPrincipal,
        updatedAt = now.plusSeconds(60)
      )
    }

    val contextCleared = dictionaryGovernance(app).updateDictionaryNamespaceContext(
      namespacePrefix = "ui.banner",
      contextClubId = None,
      actor = owner.asPrincipal,
      note = Some("decouple from club"),
      updatedAt = now.plusSeconds(70)
    ).getOrElse(fail("namespace context update missing"))
    assertEquals(contextCleared.contextClubId, None)

    val transferred = dictionaryGovernance(app).transferDictionaryNamespace(
      namespacePrefix = "ui.banner",
      newOwnerId = outsider.id,
      actor = principalFor(app, superAdmin.id),
      transferredAt = now.plusSeconds(80)
    ).getOrElse(fail("namespace transfer missing"))
    assertEquals(transferred.ownerPlayerId, outsider.id)

    val postClearWrite = dictionaryGovernance(app).upsertDictionary(
      key = "ui.banner.message",
      value = "editor restored after context clear",
      actor = editor.asPrincipal,
      updatedAt = now.plusSeconds(90)
    )
    assertEquals(postClearWrite.updatedBy, editor.id)

    val auditTypes = auditEventRepository(app).findByAggregate("dictionary-namespace", "ui.banner.").map(_.eventType)
    assert(auditTypes.contains("DictionaryNamespaceContextUpdated"))
  }
