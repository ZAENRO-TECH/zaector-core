package com.zaenrotech.playwright.playwrightpycharm

data class ScannedElement(
    val tagName: String,
    val text: String?,
    val id: String?,
    val selector: String?,
    val attributes: Map<String, String>? // IMPORTANT: This must be present!
) {
    override fun toString(): String {
        var label = tagName
        if (!id.isNullOrBlank()) label += " #$id"
        else if (!text.isNullOrBlank()) label += " \"$text\""

        if (attributes != null && attributes["visible"] == "false") {
            label += " (hidden)"
        }
        return label
    }
}