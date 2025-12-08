package com.zaenrotech.zaector.core

/**
 * Represents a single recorded user action during browser interaction
 */
data class RecordedAction(
    val type: ActionType,
    val selector: String,
    val value: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val url: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    // Smart Wait metadata
    val needsNavigationWait: Boolean = false,
    val needsSelectorWait: Boolean = true,
    val hasNetworkActivity: Boolean = false
) {
    override fun toString(): String {
        return when (type) {
            ActionType.CLICK -> "Click on '$selector'"
            ActionType.TYPE -> "Type '$value' into '$selector'"
            ActionType.FILL -> "Fill '$selector' with '$value'"
            ActionType.NAVIGATE -> "Navigate to '$url'"
            ActionType.SELECT -> "Select '$value' in '$selector'"
            ActionType.CHECK -> "Check '$selector'"
            ActionType.UNCHECK -> "Uncheck '$selector'"
            ActionType.PRESS -> "Press key '$value' on '$selector'"
            ActionType.HOVER -> "Hover over '$selector'"
        }
    }

    /**
     * Generate Playwright code for this action with smart waits
     */
    fun toCode(template: CodeTemplate, formatting: CodeFormatting): String {
        val indent = if (formatting.useTabs) "\t" else " ".repeat(formatting.indentSize)
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        val lines = mutableListOf<String>()

        when (type) {
            ActionType.NAVIGATE -> {
                if (url != null) {
                    lines.add("page.goto($quote$url$quote)")
                    // Always wait for network idle after navigation
                    lines.add("page.wait_for_load_state(${quote}networkidle$quote)")
                } else {
                    lines.add("# Navigation action (no URL)")
                }
            }
            ActionType.TYPE, ActionType.FILL -> {
                // Add selector wait before interaction
                if (needsSelectorWait) {
                    lines.add("page.wait_for_selector($quote$selector$quote, state=${quote}visible$quote)")
                }
                // Replace hardcoded "text" placeholder with actual value
                lines.add(template.generateAction(selector, "fill", formatting)
                    .replace("${quote}text${quote}", formatValue(value, formatting)))
            }
            else -> {
                // Add selector wait before other interactions (click, hover, etc.)
                if (needsSelectorWait && type != ActionType.NAVIGATE) {
                    lines.add("page.wait_for_selector($quote$selector$quote, state=${quote}visible$quote)")
                }
                lines.add(template.generateAction(selector, type.name.lowercase(), formatting))
            }
        }

        // Add network wait if there was significant network activity
        if (hasNetworkActivity && type != ActionType.NAVIGATE) {
            lines.add("page.wait_for_load_state(${quote}networkidle$quote)")
        }

        return lines.joinToString("\n$indent")
    }

    private fun formatValue(value: String?, formatting: CodeFormatting): String {
        if (value == null) return ""
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        return "$quote$value$quote"
    }
}

/**
 * Types of actions that can be recorded
 */
enum class ActionType {
    CLICK,
    TYPE,
    FILL,
    NAVIGATE,
    SELECT,
    CHECK,
    UNCHECK,
    PRESS,
    HOVER
}
