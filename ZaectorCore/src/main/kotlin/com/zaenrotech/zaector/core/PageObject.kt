package com.zaenrotech.zaector.core

/**
 * Represents a Page Object extracted from recorded actions
 */
data class PageObject(
    val name: String,                          // e.g., "LoginPage", "DashboardPage"
    val url: String?,                          // Base URL for this page
    val actions: List<RecordedAction>,         // Actions performed on this page
    val methods: List<PageMethod>,             // Extracted methods
    val selectors: Map<String, String>         // Selector name → selector value
) {
    /**
     * Generate Python Page Object class code
     */
    fun generatePythonClass(formatting: CodeFormatting): String {
        val indent = if (formatting.useTabs) "\t" else " ".repeat(formatting.indentSize)
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        val builder = StringBuilder()

        // Class header
        builder.append("class $name:\n")
        builder.append("${indent}${quote}${quote}${quote}Page Object for $url${quote}${quote}${quote}\n\n")

        // Constructor
        builder.append("${indent}def __init__(self, page):\n")
        builder.append("${indent}${indent}self.page = page\n")

        // Selectors as instance variables
        if (selectors.isNotEmpty()) {
            builder.append("\n${indent}${indent}# Selectors\n")
            selectors.forEach { (name, selector) ->
                builder.append("${indent}${indent}self.${name} = $quote${selector}$quote\n")
            }
        }

        // Methods
        if (methods.isNotEmpty()) {
            builder.append("\n")
            methods.forEach { method ->
                builder.append(method.generatePythonMethod(formatting, indent))
                builder.append("\n")
            }
        }

        return builder.toString()
    }

    /**
     * Generate TypeScript Page Object class code
     */
    fun generateTypeScriptClass(formatting: CodeFormatting): String {
        val indent = if (formatting.useTabs) "\t" else " ".repeat(formatting.indentSize)
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        val builder = StringBuilder()

        // Import
        builder.append("import { Page, Locator } from '@playwright/test';\n\n")

        // Class header
        builder.append("export class $name {\n")
        builder.append("${indent}private page: Page;\n\n")

        // Selectors as private properties
        if (selectors.isNotEmpty()) {
            builder.append("${indent}// Selectors\n")
            selectors.forEach { (name, selector) ->
                builder.append("${indent}private readonly ${name}: string = $quote${selector}$quote;\n")
            }
            builder.append("\n")
        }

        // Constructor
        builder.append("${indent}constructor(page: Page) {\n")
        builder.append("${indent}${indent}this.page = page;\n")
        builder.append("${indent}}\n")

        // Methods
        if (methods.isNotEmpty()) {
            builder.append("\n")
            methods.forEach { method ->
                builder.append(method.generateTypeScriptMethod(formatting, indent))
                builder.append("\n")
            }
        }

        builder.append("}\n")

        return builder.toString()
    }
}

/**
 * Represents a method in a Page Object
 */
data class PageMethod(
    val name: String,                          // e.g., "login", "clickSubmit"
    val actions: List<RecordedAction>,         // Actions in this method
    val parameters: List<MethodParameter>,     // Method parameters
    val returnType: String = "void"            // Return type (for fluent API)
) {
    /**
     * Generate Python method code
     */
    fun generatePythonMethod(formatting: CodeFormatting, indent: String): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        val builder = StringBuilder()

        // Method signature
        val params = if (parameters.isEmpty()) {
            "self"
        } else {
            "self, " + parameters.joinToString(", ") { "${it.name}: ${it.type}" }
        }

        builder.append("${indent}def $name($params):\n")

        // Docstring
        val docstring = generateDocstring()
        if (docstring.isNotEmpty()) {
            builder.append("${indent}${indent}${quote}${quote}${quote}$docstring${quote}${quote}${quote}\n")
        }

        // Method body
        actions.forEach { action ->
            val code = generateActionCode(action, parameters, formatting, indent + indent)
            builder.append("$code\n")
        }

        return builder.toString()
    }

    /**
     * Generate TypeScript method code
     */
    fun generateTypeScriptMethod(formatting: CodeFormatting, indent: String): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        val builder = StringBuilder()

        // Method signature
        val params = if (parameters.isEmpty()) {
            ""
        } else {
            parameters.joinToString(", ") { "${it.name}: ${it.type}" }
        }

        val asyncKeyword = if (actions.any { it.type != ActionType.NAVIGATE }) "async " else ""
        builder.append("${indent}${asyncKeyword}$name($params): Promise<void> {\n")

        // Method body
        actions.forEach { action ->
            val code = generateActionCodeTypeScript(action, parameters, formatting, indent + indent)
            builder.append("$code\n")
        }

        builder.append("${indent}}\n")

        return builder.toString()
    }

    private fun generateDocstring(): String {
        val actionDescriptions = actions.map { it.type.name.lowercase() }.distinct()
        return when {
            parameters.isEmpty() -> "Performs: ${actionDescriptions.joinToString(", ")}"
            else -> "Performs: ${actionDescriptions.joinToString(", ")} with ${parameters.joinToString(", ") { it.name }}"
        }
    }

    private fun generateActionCode(
        action: RecordedAction,
        parameters: List<MethodParameter>,
        formatting: CodeFormatting,
        indent: String
    ): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""

        // Replace parameter values with parameter names
        val value = when (action.type) {
            ActionType.FILL, ActionType.TYPE -> {
                parameters.find { it.originalValue == action.value }?.name ?: "$quote${action.value ?: ""}$quote"
            }
            else -> action.value?.let { "$quote$it$quote" } ?: ""
        }

        return when (action.type) {
            ActionType.CLICK -> {
                "${indent}self.page.locator(self.${getSelectorVariableName(action.selector)}).click()"
            }
            ActionType.FILL -> {
                "${indent}self.page.locator(self.${getSelectorVariableName(action.selector)}).fill($value)"
            }
            ActionType.TYPE -> {
                "${indent}self.page.locator(self.${getSelectorVariableName(action.selector)}).type($value)"
            }
            ActionType.PRESS -> {
                "${indent}self.page.keyboard.press($value)"
            }
            ActionType.HOVER -> {
                "${indent}self.page.locator(self.${getSelectorVariableName(action.selector)}).hover()"
            }
            ActionType.SELECT -> {
                "${indent}self.page.locator(self.${getSelectorVariableName(action.selector)}).select_option($value)"
            }
            ActionType.CHECK -> {
                "${indent}self.page.locator(self.${getSelectorVariableName(action.selector)}).check()"
            }
            ActionType.UNCHECK -> {
                "${indent}self.page.locator(self.${getSelectorVariableName(action.selector)}).uncheck()"
            }
            ActionType.NAVIGATE -> {
                "${indent}self.page.goto($quote${action.url}$quote)"
            }
        }
    }

    private fun generateActionCodeTypeScript(
        action: RecordedAction,
        parameters: List<MethodParameter>,
        formatting: CodeFormatting,
        indent: String
    ): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""

        // Replace parameter values with parameter names
        val value = when (action.type) {
            ActionType.FILL, ActionType.TYPE -> {
                parameters.find { it.originalValue == action.value }?.name ?: "$quote${action.value ?: ""}$quote"
            }
            else -> action.value?.let { "$quote$it$quote" } ?: ""
        }

        return when (action.type) {
            ActionType.CLICK -> {
                "${indent}await this.page.locator(this.${getSelectorVariableName(action.selector)}).click();"
            }
            ActionType.FILL -> {
                "${indent}await this.page.locator(this.${getSelectorVariableName(action.selector)}).fill($value);"
            }
            ActionType.TYPE -> {
                "${indent}await this.page.locator(this.${getSelectorVariableName(action.selector)}).type($value);"
            }
            ActionType.PRESS -> {
                "${indent}await this.page.keyboard.press($value);"
            }
            ActionType.HOVER -> {
                "${indent}await this.page.locator(this.${getSelectorVariableName(action.selector)}).hover();"
            }
            ActionType.SELECT -> {
                "${indent}await this.page.locator(this.${getSelectorVariableName(action.selector)}).selectOption($value);"
            }
            ActionType.CHECK -> {
                "${indent}await this.page.locator(this.${getSelectorVariableName(action.selector)}).check();"
            }
            ActionType.UNCHECK -> {
                "${indent}await this.page.locator(this.${getSelectorVariableName(action.selector)}).uncheck();"
            }
            ActionType.NAVIGATE -> {
                "${indent}await this.page.goto($quote${action.url}$quote);"
            }
        }
    }

    private fun getSelectorVariableName(selector: String): String {
        // Extract meaningful name from selector
        return when {
            selector.contains("data-testid") -> {
                val match = Regex("""data-testid['"=\s]+([^'"\]]+)""").find(selector)
                match?.groupValues?.get(1)?.replace("-", "_")?.lowercase() ?: "element"
            }
            selector.startsWith("#") -> {
                selector.substring(1).replace("-", "_").replace(".", "_").lowercase()
            }
            selector.contains("[name=") -> {
                val match = Regex("""name['"=\s]+([^'"\]]+)""").find(selector)
                match?.groupValues?.get(1)?.replace("-", "_")?.lowercase() ?: "element"
            }
            selector.startsWith(".") -> {
                selector.substring(1).split(".").first().replace("-", "_").lowercase()
            }
            else -> {
                "element_${selector.hashCode().toString().takeLast(4)}"
            }
        }
    }
}

/**
 * Represents a method parameter
 */
data class MethodParameter(
    val name: String,              // Parameter name
    val type: String,              // Parameter type (string, number, etc.)
    val originalValue: String?     // Original value from recorded action
)
