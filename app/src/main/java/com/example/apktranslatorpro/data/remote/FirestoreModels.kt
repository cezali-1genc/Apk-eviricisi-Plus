package com.example.apktranslatorpro.data.remote

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfileRemote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val preferredTargetLang: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ApkProjectRemote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val fileName: String = "",
    val packageName: String = "",
    val appLabel: String = "",
    val versionName: String = "",
    val inputSha256: String = "",
    val outputSha256: String? = null,
    val sourceLang: String = "",
    val targetLang: String = "",
    val translationEngine: String = "AUTO",
    val translatedCount: Long = 0L,
    val aiSecuritySummary: String? = null,
    val status: String = "inspected",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
