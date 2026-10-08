package dev.diego.expanda.gitsync

import dev.diego.expanda.data.CompatibilityIssue
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * SHA of the last successfully applied sync, with the ETag GitHub returned for the commit check and the
 * [GitSyncConfig.repoKey] it was applied from.
 */
data class AppliedState(val sha: String? = null, val etag: String? = null, val repoKey: String? = null)

/** Outcome of the cheap commit check. */
sealed interface BranchCheck {
    /** Nothing new: HTTP 304, or the same SHA we already applied. */
    class Unchanged(val etag: String? = null) : BranchCheck

    /** Remote moved (or the check was forced). [sha] is the commit every following request is pinned to. */
    class Changed(val sha: String, val etag: String?) : BranchCheck
}

/** Persisted form of a CompatibilityIssue: severity + message only. */
data class StoredIssue(val severity: String, val message: String)

object IssueCodec {
    const val MAX_ISSUES = 100

    fun fromCompat(issues: List<CompatibilityIssue>): List<StoredIssue> =
        issues.map { StoredIssue(it.severity.name, it.message) }

    fun encode(issues: List<StoredIssue>): String {
        val array = JSONArray()
        issues.take(MAX_ISSUES).forEach {
            array.put(JSONObject().put("severity", it.severity).put("message", it.message))
        }
        return array.toString()
    }

    fun decode(json: String?): List<StoredIssue> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until minOf(array.length(), MAX_ISSUES)).map { i ->
                val o = array.getJSONObject(i)
                StoredIssue(o.optString("severity"), o.optString("message"))
            }
        } catch (e: JSONException) {
            emptyList()
        }
    }
}

/** Policy for the on-app-open sync, kept pure so it is unit-testable. */
object SyncOnOpenPolicy {
    const val MIN_INTERVAL_MS = 60 * 60 * 1000L

    fun shouldSync(configured: Boolean, folderLinked: Boolean, lastCheckedAt: Long?, now: Long): Boolean =
        configured && !folderLinked && (lastCheckedAt == null || now - lastCheckedAt > MIN_INTERVAL_MS)
}
