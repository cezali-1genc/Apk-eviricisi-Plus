package com.example.apktranslatorpro.data.remote

import android.content.Context
import com.example.R
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreSyncRepository(
    private val db: FirebaseFirestore
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun userDocRef(userId: String) = db.collection("users").document(userId)

    private fun projectsCollection(userId: String) =
        userDocRef(userId).collection("apk_projects")

    fun observeUserProjects(userId: String): Flow<List<ApkProjectRemote>> =
        projectsCollection(userId)
            .snapshots(MetadataChanges.EXCLUDE)
            .map { snapshot ->
                snapshot.toObjects(
                    ApkProjectRemote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            }

    suspend fun ensureUserProfile(
        userId: String,
        displayName: String,
        email: String?,
        preferredTargetLang: String? = null
    ): Result<Unit> {
        val docRef = userDocRef(userId)
        return try {
            val existing = docRef.get().await()
            val safeName = displayName.trim().ifBlank { "Kullanıcı" }.take(80)
            val safeEmail = email?.trim()?.takeIf { it.isNotBlank() }?.take(160)
            val safeLang = preferredTargetLang?.trim()?.takeIf { it.length in 2..16 }
            if (!existing.exists()) {
                val payload = buildMap<String, Any> {
                    put("userId", userId)
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    if (safeLang != null) put("preferredTargetLang", safeLang)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(payload).await()
            } else {
                val updates = buildMap<String, Any> {
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    if (safeLang != null) put("preferredTargetLang", safeLang)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updates).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createProject(userId: String, project: ApkProjectRemote): Result<String> {
        val docId = "proj_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val docRef = projectsCollection(userId).document(docId)
        return try {
            val payload = buildMap<String, Any> {
                put("userId", userId)
                put("fileName", project.fileName.trim().ifBlank { "app.apk" }.take(160))
                put("packageName", project.packageName.trim().ifBlank { "unknown.package" }.take(160))
                put("appLabel", project.appLabel.trim().ifBlank { "Android App" }.take(120))
                put("versionName", project.versionName.trim().ifBlank { "1.0" }.take(64))
                put("inputSha256", project.inputSha256.take(64))
                project.outputSha256?.takeIf { it.isNotBlank() }?.let { put("outputSha256", it.take(64)) }
                put("sourceLang", project.sourceLang.trim().ifBlank { "en" }.take(16))
                put("targetLang", project.targetLang.trim().ifBlank { "tr" }.take(16))
                put("translationEngine", project.translationEngine)
                put("translatedCount", project.translatedCount.coerceIn(0L, 1_000_000L))
                project.aiSecuritySummary?.takeIf { it.isNotBlank() }?.let { put("aiSecuritySummary", it.take(4000)) }
                put("status", project.status)
                put("createdAt", FieldValue.serverTimestamp())
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.set(payload).await()
            Result.success(docId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getProject(userId: String, projectId: String): Result<ApkProjectRemote?> {
        val docRef = projectsCollection(userId).document(projectId)
        return try {
            val snap = docRef.get().await()
            val item = if (snap.exists()) {
                snap.toObject(
                    ApkProjectRemote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else null
            Result.success(item)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun getUserProjects(userId: String): Result<List<ApkProjectRemote>> {
        val colRef = projectsCollection(userId)
        return try {
            val snap = colRef.get().await()
            val items = snap.toObjects(
                ApkProjectRemote::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).sortedByDescending { it.createdAt?.seconds ?: 0L }
            Result.success(items)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            Result.failure(e)
        }
    }

    suspend fun markProjectSigned(
        userId: String,
        projectId: String,
        outputSha256: String,
        targetLang: String,
        translationEngine: String,
        translatedCount: Long
    ): Result<Unit> {
        val docRef = projectsCollection(userId).document(projectId)
        return try {
            val updates = mapOf(
                "status" to "signed",
                "outputSha256" to outputSha256.take(64),
                "targetLang" to targetLang.take(16),
                "translationEngine" to translationEngine,
                "translatedCount" to translatedCount.coerceIn(0L, 1_000_000L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.update(updates).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun attachAiSummary(
        userId: String,
        projectId: String,
        summary: String
    ): Result<Unit> {
        val docRef = projectsCollection(userId).document(projectId)
        return try {
            docRef.update(
                mapOf(
                    "aiSecuritySummary" to summary.take(4000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteProject(userId: String, projectId: String): Result<Unit> {
        val docRef = projectsCollection(userId).document(projectId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
