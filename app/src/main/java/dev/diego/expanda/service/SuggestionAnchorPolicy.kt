package dev.diego.expanda.service

data class AnchorGeometry(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class SuggestionAnchor(
    val packageName: String,
    val windowId: Int,
    val uniqueId: String?,
    val viewId: String?,
    val className: String?,
    val bounds: AnchorGeometry,
)

object SuggestionAnchorPolicy {
    fun shouldKeep(anchor: SuggestionAnchor, active: SuggestionAnchor): Boolean {
        if (anchor.packageName != active.packageName || anchor.windowId != active.windowId) return false
        if (!anchor.uniqueId.isNullOrBlank() && !active.uniqueId.isNullOrBlank()) {
            return anchor.uniqueId == active.uniqueId
        }
        if (!anchor.viewId.isNullOrBlank() && !active.viewId.isNullOrBlank()) {
            return anchor.viewId == active.viewId && anchor.className == active.className
        }
        return anchor.className == active.className && anchor.bounds == active.bounds
    }
}
