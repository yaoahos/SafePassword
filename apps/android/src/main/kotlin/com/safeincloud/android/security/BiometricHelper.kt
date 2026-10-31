package com.safeincloud.android.security

import android.content.Context
import android.content.pm.PackageManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint-only biometric helper. Face authentication is intentionally not offered:
 * Android's public BiometricPrompt cannot reliably restrict the prompt to the face
 * modality, so keeping a separate "face unlock" switch would be misleading.
 */
object BiometricHelper {
    data class Status(
        val available: Boolean,
        val enrolled: Boolean,
        val message: String
    )

    fun status(context: Context): Status {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT)) {
            return Status(
                available = false,
                enrolled = false,
                message = "此设备没有指纹识别硬件"
            )
        }
        val manager = BiometricManager.from(context)
        return when (manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)) {
            BiometricManager.BIOMETRIC_SUCCESS ->
                Status(true, true, "指纹识别可用")
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                Status(true, false, "系统尚未录入指纹，请先在系统设置中录入")
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                Status(false, false, "此设备没有指纹识别硬件")
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                Status(false, false, "指纹识别硬件暂时不可用，请稍后重试")
            else ->
                Status(false, false, "指纹识别不可用，请检查系统安全设置")
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        onSuccess: () -> Unit,
        onFailure: (code: Int, message: String) -> Unit
    ) {
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onSuccess()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    onFailure(errorCode, errString.toString())
                }
            }
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("指纹解锁")
            .setSubtitle("验证系统指纹以解锁密码库")
            .setNegativeButtonText("使用主密码")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .setConfirmationRequired(false)
            .build()
        runCatching { prompt.authenticate(info) }
            .onFailure { onFailure(ERROR_CANNOT_START, it.message ?: "无法启动指纹验证") }
    }

    /**
     * 用户自己取消（点了"使用主密码"或返回键）。这类取消是预期行为，不该当成错误提示。
     * 注意 [BiometricPrompt.ERROR_CANCELED] 不算用户取消——它通常意味着系统在界面尚未就绪、
     * 或者应用被切到后台时把请求丢弃了，调用方应当重试而不是静默忽略。
     */
    fun isUserCancelled(code: Int): Boolean =
        code == BiometricPrompt.ERROR_NEGATIVE_BUTTON || code == BiometricPrompt.ERROR_USER_CANCELED

    /** prompt.authenticate 直接抛异常（例如宿主 Activity 正在销毁）。 */
    const val ERROR_CANNOT_START = -1
}
