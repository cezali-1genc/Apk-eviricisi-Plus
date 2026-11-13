package com.example.apktranslatorpro

import com.example.apktranslatorpro.data.remote.ApkProjectRemote
import com.example.apktranslatorpro.data.remote.FirestoreSyncRepository
import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class FirestoreSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    private fun sampleProject(userId: String): ApkProjectRemote = ApkProjectRemote(
        userId = userId,
        fileName = "demo_${UUID.randomUUID().toString().take(6)}.apk",
        packageName = "com.example.demo",
        appLabel = "Demo App",
        versionName = "2.1.0",
        inputSha256 = "a".repeat(64),
        sourceLang = "en",
        targetLang = "tr",
        translationEngine = "GEMINI_AI",
        translatedCount = 12L,
        status = "inspected"
    )

    @Test
    fun createProject_and_getProject_authenticatedOwner_succeeds() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreSyncRepository(firestore)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserProfile(
                userId = aliceUid,
                displayName = "Alice Tester",
                email = ALICE_EMAIL,
                preferredTargetLang = "tr"
            ).getOrThrow()
        }

        val createdId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createProject(aliceUid, sampleProject(aliceUid)).getOrThrow()
        }
        assertTrue(createdId.isNotBlank())

        val fetched = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getProject(aliceUid, createdId).getOrThrow()
        }
        assertNotNull(fetched)
        assertEquals(aliceUid, fetched?.userId)
        assertEquals("inspected", fetched?.status)
    }

    @Test
    fun observeUserProjects_authenticatedOwner_emitsRealtimeUpdates() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreSyncRepository(firestore)

        val createdId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createProject(aliceUid, sampleProject(aliceUid)).getOrThrow()
        }

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeUserProjects(aliceUid).first { list -> list.any { it.id == createdId } }
        }
        assertTrue(emitted.any { it.id == createdId })
    }

    @Test
    fun markProjectSigned_and_attachAiSummary_validTransitions_succeed() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val repo = FirestoreSyncRepository(firestore)

        val createdId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createProject(aliceUid, sampleProject(aliceUid)).getOrThrow()
        }

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.attachAiSummary(aliceUid, createdId, "Risk yok, resources.arsc STORED uyumlu.").getOrThrow()
            repo.markProjectSigned(
                userId = aliceUid,
                projectId = createdId,
                outputSha256 = "b".repeat(64),
                targetLang = "tr",
                translationEngine = "GEMINI_AI",
                translatedCount = 42L
            ).getOrThrow()
        }

        val updated = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.getProject(aliceUid, createdId).getOrThrow()
        }
        assertEquals("signed", updated?.status)
        assertEquals("b".repeat(64), updated?.outputSha256)
        assertEquals(42L, updated?.translatedCount)
    }

    @Test
    fun getProject_crossUserAccess_failsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = FirestoreSyncRepository(firestore)
        val projectId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createProject(aliceUid, sampleProject(aliceUid)).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = FirestoreSyncRepository(firestore)
        val exception = assertThrowsSuspending<FirebaseFirestoreException> {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                bobRepo.getProject(aliceUid, projectId).getOrThrow()
            }
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, exception.code)
    }

    @Test
    fun observeUserProjects_unauthenticated_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = FirestoreSyncRepository(firestore)

        val exception = assertThrowsSuspending<Exception> {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeUserProjects("unauth_user_probe").first()
            }
        }
        val firestoreException = checkNotNull(exception.unwrapFirestoreException()) {
            "Expected nested FirebaseFirestoreException, got $exception"
        }
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, firestoreException.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_apk@test.com"
        const val BOB_EMAIL = "bob_apk@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3000L
    }
}
