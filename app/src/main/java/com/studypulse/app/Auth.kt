package com.studypulse.app

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

/** Google and phone number sign-in through Firebase Authentication. */
object Auth {
    /** False when the build has no google-services.json yet. */
    fun configured(c: Context): Boolean = try { FirebaseApp.getApps(c).isNotEmpty() } catch (e: Exception) { false }

    fun current(): FirebaseUser? = try { FirebaseAuth.getInstance().currentUser } catch (e: Exception) { null }

    suspend fun google(a: Activity): FirebaseUser {
        val id = a.resources.getIdentifier("default_web_client_id", "string", a.packageName)
        if (id == 0) throw IllegalStateException("Google sign-in is not set up in this build.")
        val option = GetSignInWithGoogleOption.Builder(a.getString(id)).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        val result = CredentialManager.create(a).getCredential(a, request)
        val cred = result.credential
        if (cred is CustomCredential && cred.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val google = GoogleIdTokenCredential.createFrom(cred.data)
            val fb = GoogleAuthProvider.getCredential(google.idToken, null)
            val r = FirebaseAuth.getInstance().signInWithCredential(fb).await()
            return r.user ?: throw IllegalStateException("Sign-in did not return an account.")
        }
        throw IllegalStateException("Unexpected sign-in response.")
    }

    class PhoneFlow(
        val onCode: (String, PhoneAuthProvider.ForceResendingToken) -> Unit,
        val onDone: (FirebaseUser) -> Unit,
        val onError: (String) -> Unit,
        /** Play services verified the number without an SMS. The person must still tap Continue. */
        val onInstant: (PhoneAuthCredential) -> Unit = {}
    )

    fun sendCode(a: Activity, e164: String, resend: PhoneAuthProvider.ForceResendingToken?, flow: PhoneFlow) {
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) { flow.onInstant(credential) }
            override fun onVerificationFailed(e: FirebaseException) { flow.onError(friendly(e)) }
            override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                flow.onCode(verificationId, token)
            }
        }
        val b = PhoneAuthOptions.newBuilder(FirebaseAuth.getInstance())
            .setPhoneNumber(e164)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(a)
            .setCallbacks(callbacks)
        if (resend != null) b.setForceResendingToken(resend)
        PhoneAuthProvider.verifyPhoneNumber(b.build())
    }

    fun verify(verificationId: String, code: String, flow: PhoneFlow) {
        signIn(PhoneAuthProvider.getCredential(verificationId, code), flow)
    }

    fun confirm(credential: PhoneAuthCredential, flow: PhoneFlow) = signIn(credential, flow)

    private fun signIn(credential: PhoneAuthCredential, flow: PhoneFlow) {
        FirebaseAuth.getInstance().signInWithCredential(credential)
            .addOnSuccessListener { r ->
                val u = r.user
                if (u != null) flow.onDone(u) else flow.onError("Sign-in did not return an account.")
            }
            .addOnFailureListener { flow.onError(friendly(it)) }
    }

    suspend fun signOut(c: Context) {
        try { FirebaseAuth.getInstance().signOut() } catch (_: Exception) {}
        try { CredentialManager.create(c).clearCredentialState(ClearCredentialStateRequest()) } catch (_: Exception) {}
    }

    /** Turns "+91 98765 43210" or "9876543210" into +919876543210. Returns null when it cannot be a phone number. */
    fun e164(raw: String, defaultCountry: String = "+91"): String? {
        val digits = raw.filter { it.isDigit() }
        if (raw.trim().startsWith("+")) return if (digits.length in 8..15) "+$digits" else null
        val local = digits.trimStart('0')
        return if (local.length in 7..12) defaultCountry + local else null
    }

    fun friendly(e: Throwable): String = when (e) {
        is GetCredentialCancellationException -> "Sign-in was cancelled."
        is NoCredentialException -> "No Google account was found on this phone. Add one in Settings and try again."
        is FirebaseAuthInvalidCredentialsException -> "That code is not correct or has expired."
        is FirebaseTooManyRequestsException -> "Too many attempts. Please wait a while and try again."
        else -> {
            val m = e.message ?: ""
            when {
                m.contains("network", true) -> "No internet connection. Check your network and try again."
                m.contains("12500") || m.contains("10:") || m.contains("DEVELOPER_ERROR", true) ->
                    "Google sign-in is not set up correctly for this build (check the SHA-1 key and web client ID)."
                m.contains("BILLING_NOT_ENABLED", true) -> "SMS sign-in needs the Firebase project to be on a plan that allows SMS."
                m.contains("blocked", true) -> "This device was blocked for too many attempts. Try again later."
                m.isBlank() -> "Something went wrong. Please try again."
                else -> m
            }
        }
    }
}
