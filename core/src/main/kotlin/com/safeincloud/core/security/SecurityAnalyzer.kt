package com.safeincloud.core.security

import com.safeincloud.core.model.ItemStatus
import com.safeincloud.core.model.Vault
import com.safeincloud.core.model.VaultItem

data class SecurityReport(
    val total: Int = 0,
    val weak: List<VaultItem> = emptyList(),
    val reused: List<VaultItem> = emptyList(),
    val breached: List<VaultItem> = emptyList(),
    val old: List<VaultItem> = emptyList()
)

object SecurityAnalyzer {
    private const val OLD_THRESHOLD_MILLIS = 180L * 24L * 60L * 60L * 1000L

    fun analyze(vault: Vault, breachedCounts: Map<String, Int> = emptyMap()): SecurityReport {
        val active = vault.items.filter { it.status == ItemStatus.ACTIVE }
        val withPassword = active.mapNotNull { item ->
            item.primaryPassword?.let { password -> item to password }
        }
        val weak = withPassword.filter { (_, password) ->
            PasswordStrength.evaluate(password).score <= 1
        }.map { it.first }

        val counts = withPassword.groupingBy { it.second }.eachCount()
        val reused = withPassword.filter { (_, password) -> (counts[password] ?: 0) > 1 }
            .map { it.first }

        val breached = active.filter { (breachedCounts[it.id] ?: 0) > 0 }
        val now = System.currentTimeMillis()
        val old = active.filter { now - it.updatedAt > OLD_THRESHOLD_MILLIS }

        return SecurityReport(
            total = active.size,
            weak = weak.distinctBy { it.id },
            reused = reused.distinctBy { it.id },
            breached = breached,
            old = old
        )
    }

    fun passwordIndex(vault: Vault): Map<String, List<String>> {
        val result = linkedMapOf<String, MutableList<String>>()
        vault.items.filter { it.status == ItemStatus.ACTIVE }.forEach { item ->
            val password = item.primaryPassword ?: return@forEach
            result.getOrPut(password) { mutableListOf() }.add(item.id)
        }
        return result
    }
}
