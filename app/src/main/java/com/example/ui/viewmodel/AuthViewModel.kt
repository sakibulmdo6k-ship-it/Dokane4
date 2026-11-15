package com.example.ui.viewmodel

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.model.InviteCodeConfig
import com.example.data.model.UserProfile
import com.example.data.repository.StoreRepository
import com.example.domain.ShopType
import com.example.domain.ShopTypeCatalog
import com.example.util.AppLanguage
import com.example.util.ShopLocale
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

sealed interface AuthUiState {
    data object Loading : AuthUiState
    data object Unauthenticated : AuthUiState
    data class AuthenticatedNoStore(val user: FirebaseUser) : AuthUiState
    data class AuthenticatedWithStore(val user: FirebaseUser, val storeId: String) : AuthUiState
    data class DemoMode(val storeId: String = "demo_store") : AuthUiState
}

class AuthViewModel(
    private val repository: StoreRepository
) : ViewModel() {

    private val auth: FirebaseAuth? = try {
        Firebase.auth
    } catch (e: Exception) {
        Log.w("AuthViewModel", "Firebase Auth not available: ${e.message}")
        null
    }

    private val _uiState = MutableStateFlow<AuthUiState>(AuthUiState.Loading)
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    val currentLanguage: StateFlow<AppLanguage> = repository.currentLanguage

    fun setLanguage(language: AppLanguage) = repository.setLanguage(language)

    fun toggleLanguage() = repository.toggleLanguage()

    private fun tr(key: String): String = ShopLocale.get(key, repository.currentLanguage.value)
    private fun tr(key: String, vararg args: Any): String =
        ShopLocale.format(key, repository.currentLanguage.value, *args)

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        if (_uiState.value is AuthUiState.DemoMode) return@AuthStateListener
        val user = firebaseAuth.currentUser
        if (user == null) {
            _uiState.value = AuthUiState.Unauthenticated
        } else {
            resolveUserStore(user)
        }
    }

    init {
        try {
            if (auth != null) {
                auth.addAuthStateListener(authListener)
            } else {
                _uiState.value = AuthUiState.Unauthenticated
            }
        } catch (e: Exception) {
            _uiState.value = AuthUiState.Unauthenticated
        }
    }

    override fun onCleared() {
        super.onCleared()
        try {
            auth?.removeAuthStateListener(authListener)
        } catch (e: Exception) {
            // Ignored
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun launchDemoMode(shopType: ShopType? = null) {
        clearMessages()
        repository.configureDemoStore(shopType)
        _userProfile.value = UserProfile(id = "demo_user", email = "demo@storeos.app", storeId = "demo_store", role = "owner")
        _uiState.value = AuthUiState.DemoMode()
    }

    fun resolveUserStore(user: FirebaseUser) {
        viewModelScope.launch {
            try {
                val profile = repository.getUserProfileOnce(user.uid)
                _userProfile.value = profile
                if (profile != null && profile.storeId.isNotBlank()) {
                    _uiState.value = AuthUiState.AuthenticatedWithStore(user, profile.storeId)
                } else {
                    _uiState.value = AuthUiState.AuthenticatedNoStore(user)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Failed to resolve store for user ${user.uid}", e)
                _userProfile.value = null
                _uiState.value = AuthUiState.AuthenticatedNoStore(user)
            }
        }
    }

    // -------------------------------------------------------------
    // GOOGLE SIGN-IN VIA CREDENTIAL MANAGER
    // -------------------------------------------------------------

    private fun Context.findActivity(): Activity? {
        var ctx: Context? = this
        while (ctx is android.content.ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    fun signInWithGoogle(context: Context) {
        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _errorMessage.value = tr("err_firebase_not_configured")
            return
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (e: Exception) {
            ""
        }
        if (clientId.isBlank()) {
            _errorMessage.value = tr("err_google_not_configured")
            return
        }

        val activity = context.findActivity()
        if (activity == null) {
            _errorMessage.value = tr("err_activity_required")
            return
        }

        val credentialManager = CredentialManager.create(context)
        val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(signInOption).build()

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val result = credentialManager.getCredential(activity, request)
                val credential = result.credential
                if (credential is CustomCredential && credential.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                    val googleIdToken = GoogleIdTokenCredential.createFrom(credential.data).idToken
                    val authCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
                    firebaseAuth.signInWithCredential(authCredential).await()
                } else {
                    _errorMessage.value = tr("err_unexpected_credential")
                }
            } catch (e: GetCredentialCancellationException) {
                Log.w("AuthViewModel", "Google Sign-In dismissed: ${e.message}")
                _errorMessage.value = tr("err_google_cancelled")
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Google Sign-In failed", e)
                val msg = e.localizedMessage ?: tr("err_google_failed")
                _errorMessage.value = msg
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // EMAIL & PASSWORD AUTHENTICATION
    // -------------------------------------------------------------

    fun signInWithEmail(email: String, pass: String) {
        _errorMessage.value = tr("err_google_active_provider")
    }

    fun signUpWithEmail(email: String, pass: String, confirmPass: String) {
        _errorMessage.value = tr("err_google_active_provider")
    }

    fun sendPasswordReset(email: String) {
        if (email.isBlank()) {
            _errorMessage.value = tr("err_enter_registered_email")
            return
        }

        val firebaseAuth = auth
        if (firebaseAuth == null) {
            _errorMessage.value = tr("err_firebase_short")
            return
        }

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                firebaseAuth.sendPasswordResetEmail(email.trim()).await()
                _successMessage.value = tr("reset_sent_to", email.trim())
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Reset password failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("reset_failed")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // STORE CREATION (FIRST LAUNCH ONBOARDING)
    // -------------------------------------------------------------

    fun createStore(name: String, phone: String, address: String, shopType: ShopType? = null) {
        if (name.isBlank() || phone.isBlank() || address.isBlank()) {
            _errorMessage.value = tr("fill_all_store_fields")
            return
        }
        if (shopType == null) {
            _errorMessage.value = tr("shop_type_required_err")
            return
        }

        _isBusy.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val startingCategories = ShopTypeCatalog.startingCategories(shopType)
                val newStoreId = repository.createStore(
                    name = name,
                    phone = phone,
                    address = address,
                    shopType = shopType.id,
                    productCategories = startingCategories
                )
                val user = auth?.currentUser
                if (user != null) {
                    _uiState.value = AuthUiState.AuthenticatedWithStore(user, newStoreId)
                } else {
                    _uiState.value = AuthUiState.DemoMode(newStoreId)
                }
            } catch (e: Exception) {
                Log.e("AuthViewModel", "Store creation failed", e)
                _errorMessage.value = e.localizedMessage ?: tr("err_create_store_failed")
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun joinStoreWithCode(code: String, onSuccess: (String) -> Unit = {}) {
        val cleanCode = InviteCodeConfig.normalize(code)
        if (cleanCode.isBlank()) {
            _errorMessage.value = tr("err_enter_invite_code", InviteCodeConfig.CODE_LENGTH)
            return
        }
        if (!InviteCodeConfig.isValidFormat(cleanCode)) {
            _errorMessage.value = tr("err_wrong_invite_code", InviteCodeConfig.CODE_LENGTH)
            return
        }

        val user = auth?.currentUser
        val uid = user?.uid ?: "demo_staff"
        val email = user?.email ?: "staff@store.com"
        val displayName = user?.displayName ?: tr("staff_member_default")

        _isBusy.value = true
        _errorMessage.value = null
        viewModelScope.launch {
            try {
                val result = repository.joinStoreWithInvite(cleanCode, uid, email, displayName)
                result.fold(
                    onSuccess = { storeId ->
                        if (user != null) {
                            _uiState.value = AuthUiState.AuthenticatedWithStore(user, storeId)
                        } else {
                            _uiState.value = AuthUiState.DemoMode(storeId)
                        }
                        _successMessage.value = tr("err_joined_store_success")
                        onSuccess(storeId)
                    },
                    onFailure = { error ->
                        _errorMessage.value = error.message ?: tr("err_join_store_failed")
                    }
                )
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: tr("err_join_store_failed")
            } finally {
                _isBusy.value = false
            }
        }
    }

    // -------------------------------------------------------------
    // LOG OUT
    // -------------------------------------------------------------

    fun signOut(context: Context) {
        try {
            auth?.signOut()
        } catch (e: Exception) {
            Log.w("AuthViewModel", "Sign out note: ${e.message}")
        }
        _uiState.value = AuthUiState.Unauthenticated
        _userProfile.value = null

        viewModelScope.launch(NonCancellable) {
            try {
                val credentialManager = CredentialManager.create(context)
                kotlinx.coroutines.withTimeoutOrNull(1500L) {
                    credentialManager.clearCredentialState(ClearCredentialStateRequest())
                }
            } catch (e: Exception) {
                Log.w("AuthViewModel", "Failed to clear credential state", e)
            }
        }
    }
}
