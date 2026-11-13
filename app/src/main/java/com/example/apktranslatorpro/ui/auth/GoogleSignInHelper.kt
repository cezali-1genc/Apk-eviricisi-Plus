package com.example.apktranslatorpro.ui.auth

import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "GoogleSignInHelper"

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        trySend(firebaseAuth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

sealed interface GoogleSignInOutcome {
    data class Success(val user: FirebaseUser) : GoogleSignInOutcome
    data object Cancelled : GoogleSignInOutcome
    data class Failure(val message: String) : GoogleSignInOutcome
}

object GoogleSignInHelper {

    suspend fun attemptSilentSignIn(activityContext: Context): Boolean {
        return try {
            val serverClientId = activityContext.getString(R.string.default_web_client_id)
            if (serverClientId.isBlank()) return false

            val credentialManager = CredentialManager.create(activityContext)
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(true)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )
            val idToken = extractGoogleIdToken(result) ?: return false
            val authResult = FirebaseAuth.getInstance()
                .signInWithCredential(GoogleAuthProvider.getCredential(idToken, null))
                .await()
            authResult.user != null
        } catch (e: CancellationException) {
            throw e
        } catch (_: NoCredentialException) {
            false
        } catch (_: GetCredentialCancellationException) {
            false
        } catch (e: Exception) {
            Log.d(TAG, "Silent Google sign-in skipped: ${e.message}")
            false
        }
    }

    suspend fun signInInteractive(activityContext: Context): GoogleSignInOutcome {
        return try {
            val serverClientId = activityContext.getString(R.string.default_web_client_id)
            if (serverClientId.isBlank()) {
                return GoogleSignInOutcome.Failure("OAuth Web Client ID bulunamadı.")
            }

            val credentialManager = CredentialManager.create(activityContext)
            val signInWithGoogleOption = GetSignInWithGoogleOption.Builder(serverClientId)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(signInWithGoogleOption)
                .build()

            val result = credentialManager.getCredential(
                request = request,
                context = activityContext
            )

            val idToken = extractGoogleIdToken(result)
                ?: return GoogleSignInOutcome.Failure("Google kimlik belirteci alınamadı.")

            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = FirebaseAuth.getInstance().signInWithCredential(credential).await()
            val user = authResult.user
                ?: return GoogleSignInOutcome.Failure("Firebase oturumu açılamadı.")

            GoogleSignInOutcome.Success(user)
        } catch (e: GetCredentialCancellationException) {
            GoogleSignInOutcome.Cancelled
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Interactive Google Sign-In failed", e)
            GoogleSignInOutcome.Failure(
                e.localizedMessage ?: "Google ile giriş sırasında hata oluştu."
            )
        }
    }

    suspend fun signOut(context: Context) {
        withContext(NonCancellable) {
            runCatching { FirebaseAuth.getInstance().signOut() }
            runCatching {
                CredentialManager.create(context)
                    .clearCredentialState(ClearCredentialStateRequest())
            }
        }
    }

    private fun extractGoogleIdToken(result: GetCredentialResponse): String? {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            return GoogleIdTokenCredential.createFrom(credential.data).idToken
        }
        return null
    }
}
