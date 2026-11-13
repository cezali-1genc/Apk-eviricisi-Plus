package com.example.apktranslatorpro.data.remote

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType(val label: String) {
    CREATE("create"),
    UPDATE("update"),
    DELETE("delete"),
    GET("get"),
    LIST("list"),
    WRITE("write")
}

fun handleFirestoreError(
    exception: Throwable,
    operationType: OperationType,
    path: String
) {
    val auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
    val currentUser = auth?.currentUser

    val providerArray = JSONArray()
    currentUser?.providerData?.forEach { profile ->
        val obj = JSONObject().apply {
            put("providerId", profile.providerId)
            put("displayName", profile.displayName ?: JSONObject.NULL)
            put("email", profile.email ?: JSONObject.NULL)
            put("photoUrl", profile.photoUrl?.toString() ?: JSONObject.NULL)
        }
        providerArray.put(obj)
    }

    val errObj = JSONObject().apply {
        put("error", exception.localizedMessage ?: exception.message ?: exception.toString())
        put("operationType", operationType.label)
        put("path", path)
        put("authInfo", JSONObject().apply {
            put("userId", currentUser?.uid ?: JSONObject.NULL)
            put("email", currentUser?.email ?: JSONObject.NULL)
            put("emailVerified", currentUser?.isEmailVerified ?: false)
            put("isAnonymous", currentUser?.isAnonymous ?: false)
            put("tenantId", currentUser?.tenantId ?: JSONObject.NULL)
            put("providerInfo", providerArray)
        })
    }

    Log.e("FirestoreError", errObj.toString())
}
