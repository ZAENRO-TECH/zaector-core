package com.zaenrotech.playwright.playwrightpycharm

import java.net.URI

/**
 * Generates Page Object Model classes from recorded actions
 */
class PageObjectGenerator {

    /**
     * Extract Page Objects from recorded actions
     */
    fun extractPageObjects(actions: List<RecordedAction>): List<PageObject> {
        if (actions.isEmpty()) return emptyList()

        val pageObjects = mutableListOf<PageObject>()
        var currentPageActions = mutableListOf<RecordedAction>()
        var currentUrl: String? = null

        // Group actions by page (URL boundaries)
        for (action in actions) {
            if (action.type == ActionType.NAVIGATE && action.url != null) {
                // Save previous page if it has actions
                if (currentPageActions.isNotEmpty() && currentUrl != null) {
                    val pageObject = createPageObject(currentUrl, currentPageActions.toList())
                    pageObjects.add(pageObject)
                }

                // Start new page
                currentUrl = action.url
                currentPageActions = mutableListOf(action)
            } else {
                // Add action to current page
                currentPageActions.add(action)
            }
        }

        // Don't forget the last page
        if (currentPageActions.isNotEmpty() && currentUrl != null) {
            val pageObject = createPageObject(currentUrl, currentPageActions.toList())
            pageObjects.add(pageObject)
        }

        // If no navigation events, treat all actions as one page
        if (pageObjects.isEmpty() && currentPageActions.isNotEmpty()) {
            val pageObject = createPageObject("unknown", currentPageActions.toList())
            pageObjects.add(pageObject)
        }

        return pageObjects
    }

    /**
     * Create a Page Object from URL and actions
     */
    private fun createPageObject(url: String, actions: List<RecordedAction>): PageObject {
        val pageName = generatePageName(url)
        val selectors = extractSelectors(actions)
        val methods = extractMethods(actions)

        return PageObject(
            name = pageName,
            url = url,
            actions = actions,
            methods = methods,
            selectors = selectors
        )
    }

    /**
     * Generate Page Object name from URL
     * Examples:
     * - https://example.com/login -> LoginPage
     * - https://example.com/dashboard/settings -> DashboardSettingsPage
     * - https://example.com/ -> HomePage
     */
    private fun generatePageName(url: String): String {
        if (url == "unknown") return "MainPage"

        return try {
            val uri = URI(url)
            val path = uri.path.trim('/')

            val name = when {
                path.isEmpty() -> "Home"
                path.contains("/") -> {
                    // Multi-segment path: /dashboard/settings -> DashboardSettings
                    path.split("/")
                        .filter { it.isNotEmpty() }
                        .joinToString("") { it.capitalize() }
                }
                else -> path.capitalize()
            }

            "${name}Page"
        } catch (e: Exception) {
            "UnknownPage"
        }
    }

    /**
     * Extract unique selectors from actions and generate variable names
     */
    private fun extractSelectors(actions: List<RecordedAction>): Map<String, String> {
        val selectors = mutableMapOf<String, String>()

        actions.forEach { action ->
            if (action.selector.isNotBlank() && action.type != ActionType.NAVIGATE) {
                val variableName = generateSelectorVariableName(action.selector)
                selectors[variableName] = action.selector
            }
        }

        return selectors
    }

    /**
     * Extract methods from action sequences
     * Uses heuristics to group actions into logical methods
     */
    private fun extractMethods(actions: List<RecordedAction>): List<PageMethod> {
        val methods = mutableListOf<PageMethod>()

        // Skip navigation action if present
        val nonNavActions = actions.filter { it.type != ActionType.NAVIGATE }

        if (nonNavActions.isEmpty()) {
            return emptyList()
        }

        // Strategy 1: If multiple FILLs followed by CLICK, it's likely a form submission
        val formSubmitMethod = detectFormSubmission(nonNavActions)
        if (formSubmitMethod != null) {
            methods.add(formSubmitMethod)
            return methods
        }

        // Strategy 2: Group consecutive actions by type similarity
        val groupedMethods = groupActionsByIntent(nonNavActions)
        methods.addAll(groupedMethods)

        // Fallback: If no patterns detected, create generic methods
        if (methods.isEmpty()) {
            methods.addAll(createGenericMethods(nonNavActions))
        }

        return methods
    }

    /**
     * Detect form submission pattern: FILL(s) + CLICK
     */
    private fun detectFormSubmission(actions: List<RecordedAction>): PageMethod? {
        if (actions.size < 2) return null

        val fillActions = actions.filter { it.type == ActionType.FILL || it.type == ActionType.TYPE }
        val clickActions = actions.filter { it.type == ActionType.CLICK }

        if (fillActions.isEmpty() || clickActions.isEmpty()) return null

        // Check if there's a CLICK after FILLs
        val lastFillIndex = actions.indexOfLast { it.type == ActionType.FILL || it.type == ActionType.TYPE }
        val firstClickAfterFills = actions.drop(lastFillIndex + 1).firstOrNull { it.type == ActionType.CLICK }

        if (firstClickAfterFills != null) {
            val methodActions = fillActions + firstClickAfterFills
            val parameters = fillActions.map { action ->
                val paramName = generateParameterName(action.selector)
                MethodParameter(
                    name = paramName,
                    type = "str",
                    originalValue = action.value
                )
            }

            val methodName = inferMethodName(methodActions)

            return PageMethod(
                name = methodName,
                actions = methodActions,
                parameters = parameters
            )
        }

        return null
    }

    /**
     * Group actions by logical intent (experimental)
     */
    private fun groupActionsByIntent(actions: List<RecordedAction>): List<PageMethod> {
        val methods = mutableListOf<PageMethod>()
        var currentGroup = mutableListOf<RecordedAction>()

        for (action in actions) {
            when (action.type) {
                ActionType.FILL, ActionType.TYPE -> {
                    currentGroup.add(action)
                }
                ActionType.CLICK -> {
                    currentGroup.add(action)
                    // Click often ends a logical action sequence
                    if (currentGroup.isNotEmpty()) {
                        methods.add(createMethodFromGroup(currentGroup.toList()))
                        currentGroup.clear()
                    }
                }
                else -> {
                    currentGroup.add(action)
                }
            }
        }

        // Handle remaining actions
        if (currentGroup.isNotEmpty()) {
            methods.add(createMethodFromGroup(currentGroup.toList()))
        }

        return methods
    }

    /**
     * Create a method from a group of actions
     */
    private fun createMethodFromGroup(actions: List<RecordedAction>): PageMethod {
        val parameters = actions
            .filter { it.type == ActionType.FILL || it.type == ActionType.TYPE }
            .map { action ->
                val paramName = generateParameterName(action.selector)
                MethodParameter(
                    name = paramName,
                    type = "str",
                    originalValue = action.value
                )
            }

        val methodName = inferMethodName(actions)

        return PageMethod(
            name = methodName,
            actions = actions,
            parameters = parameters
        )
    }

    /**
     * Create generic methods (one per action)
     */
    private fun createGenericMethods(actions: List<RecordedAction>): List<PageMethod> {
        return actions.map { action ->
            val methodName = when (action.type) {
                ActionType.CLICK -> "click_${generateSelectorVariableName(action.selector)}"
                ActionType.FILL -> "fill_${generateSelectorVariableName(action.selector)}"
                ActionType.TYPE -> "type_${generateSelectorVariableName(action.selector)}"
                ActionType.HOVER -> "hover_${generateSelectorVariableName(action.selector)}"
                ActionType.CHECK -> "check_${generateSelectorVariableName(action.selector)}"
                ActionType.UNCHECK -> "uncheck_${generateSelectorVariableName(action.selector)}"
                else -> "perform_action"
            }

            val parameters = if (action.type == ActionType.FILL || action.type == ActionType.TYPE) {
                listOf(
                    MethodParameter(
                        name = "text",
                        type = "str",
                        originalValue = action.value
                    )
                )
            } else {
                emptyList()
            }

            PageMethod(
                name = methodName,
                actions = listOf(action),
                parameters = parameters
            )
        }
    }

    /**
     * Infer method name from action sequence
     */
    private fun inferMethodName(actions: List<RecordedAction>): String {
        // Common patterns
        val hasUsername = actions.any { it.selector.contains("username", ignoreCase = true) || it.selector.contains("email", ignoreCase = true) }
        val hasPassword = actions.any { it.selector.contains("password", ignoreCase = true) }
        val hasSubmit = actions.any {
            it.type == ActionType.CLICK && (
                it.selector.contains("submit", ignoreCase = true) ||
                it.selector.contains("login", ignoreCase = true) ||
                it.selector.contains("sign", ignoreCase = true)
            )
        }

        when {
            hasUsername && hasPassword && hasSubmit -> return "login"
            hasUsername && hasPassword -> return "enter_credentials"
            actions.all { it.type == ActionType.FILL || it.type == ActionType.TYPE } -> return "fill_form"
            actions.all { it.type == ActionType.CLICK } -> {
                // Use selector name for click methods
                val firstClick = actions.firstOrNull { it.type == ActionType.CLICK }
                if (firstClick != null) {
                    return "click_${generateSelectorVariableName(firstClick.selector)}"
                }
            }
        }

        // Fallback: use dominant action type
        val dominantAction = actions.groupBy { it.type }.maxByOrNull { it.value.size }?.key
        return when (dominantAction) {
            ActionType.CLICK -> "click_element"
            ActionType.FILL -> "fill_form"
            ActionType.TYPE -> "type_text"
            else -> "perform_action"
        }
    }

    /**
     * Generate parameter name from selector
     */
    private fun generateParameterName(selector: String): String {
        val baseName = generateSelectorVariableName(selector)
        return when {
            baseName.contains("username") -> "username"
            baseName.contains("email") -> "email"
            baseName.contains("password") -> "password"
            baseName.contains("name") -> "name"
            baseName.contains("phone") -> "phone"
            baseName.contains("search") -> "query"
            else -> "text"
        }
    }

    /**
     * Generate selector variable name from selector string
     */
    private fun generateSelectorVariableName(selector: String): String {
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

    /**
     * Generate complete Page Object code with test
     */
    fun generatePageObjectCode(
        pageObjects: List<PageObject>,
        template: CodeTemplate,
        formatting: CodeFormatting,
        baseUrl: String
    ): String {
        val builder = StringBuilder()

        // Generate imports
        builder.append("from playwright.sync_api import Page, expect\n\n")

        // Generate Page Object classes
        pageObjects.forEach { pageObject ->
            builder.append(pageObject.generatePythonClass(formatting))
            builder.append("\n\n")
        }

        // Generate test using Page Objects
        builder.append(generateTestCode(pageObjects, formatting, baseUrl))

        return builder.toString()
    }

    /**
     * Generate test code that uses Page Objects
     */
    private fun generateTestCode(
        pageObjects: List<PageObject>,
        formatting: CodeFormatting,
        baseUrl: String
    ): String {
        val indent = if (formatting.useTabs) "\t" else " ".repeat(formatting.indentSize)
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        val builder = StringBuilder()

        builder.append("def test_recorded_flow(page: Page):\n")
        builder.append("${indent}${quote}${quote}${quote}Test generated from recorded actions using Page Object Model${quote}${quote}${quote}\n\n")

        pageObjects.forEach { pageObject ->
            // Instantiate page object
            val varName = pageObject.name.replaceFirstChar { it.lowercase() }
            builder.append("${indent}# ${pageObject.name}\n")
            builder.append("${indent}$varName = ${pageObject.name}(page)\n")

            // Navigate if URL is present
            if (pageObject.url != null && pageObject.url != "unknown") {
                builder.append("${indent}page.goto($quote${pageObject.url}$quote)\n")
            }

            // Call methods
            pageObject.methods.forEach { method ->
                val paramValues = method.parameters.joinToString(", ") { param ->
                    "$quote${param.originalValue ?: ""}$quote"
                }
                builder.append("${indent}$varName.${method.name}($paramValues)\n")
            }

            builder.append("\n")
        }

        return builder.toString()
    }
}

/**
 * Extension function to capitalize first character
 */
private fun String.capitalize(): String {
    return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}
