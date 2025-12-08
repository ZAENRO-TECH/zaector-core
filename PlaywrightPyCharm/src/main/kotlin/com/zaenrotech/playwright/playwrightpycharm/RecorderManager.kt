package com.zaenrotech.playwright.playwrightpycharm

import com.intellij.execution.configurations.GeneralCommandLine
import java.nio.charset.StandardCharsets

/**
 * Manages browser action recording via CDP
 */
class RecorderManager {
    private val recordedActions = mutableListOf<RecordedAction>()
    private var recordingProcess: Process? = null
    private var cdpPort: Int = 9222
    @Volatile private var isRecording = false

    // Smart wait tracking
    private var lastActionTimestamp: Long = 0
    private var lastActionWasNavigation: Boolean = false

    // Navigation deduplication
    private var lastNavigationUrl: String? = null

    // Input debouncing - track pending input actions
    private val pendingInputs = mutableMapOf<String, RecordedAction>()
    private val inputTimers = mutableMapOf<String, java.util.Timer>()

    /**
     * Start recording browser actions
     */
    fun startRecording(onActionRecorded: (RecordedAction) -> Unit) {
        if (isRecording) {
            println("[RecorderManager] Already recording!")
            return
        }

        println("[RecorderManager] Starting recording session...")
        isRecording = true

        Thread {
            try {
                val pythonScript = createRecorderScript()
                val commandLine = GeneralCommandLine(
                    "python", "-c", pythonScript, cdpPort.toString()
                )
                commandLine.charset = StandardCharsets.UTF_8

                println("[RecorderManager] Launching CDP event listener...")
                val process = commandLine.createProcess()
                recordingProcess = process

                // Read events from stdout
                val reader = process.inputStream.bufferedReader()
                reader.use {
                    it.forEachLine { line ->
                        println("[RecorderManager] Event: $line")

                        // Parse event and create RecordedAction
                        val action = parseEventLine(line)
                        if (action != null) {
                            // Debounce input/fill actions
                            if (action.type == ActionType.FILL || action.type == ActionType.TYPE) {
                                handleDebouncedInput(action, onActionRecorded)
                            } else {
                                // Non-input actions are added immediately
                                recordedActions.add(action)
                                onActionRecorded(action)
                            }
                        }
                    }
                }

                println("[RecorderManager] Recording stopped")
            } catch (e: Exception) {
                println("[RecorderManager] Error during recording: ${e.message}")
                e.printStackTrace()
            } finally {
                isRecording = false
            }
        }.start()
    }

    /**
     * Handle debounced input - only record final value after typing stops
     */
    private fun handleDebouncedInput(action: RecordedAction, onActionRecorded: (RecordedAction) -> Unit) {
        val key = action.selector

        // Cancel existing timer for this selector
        inputTimers[key]?.cancel()

        // Update pending action
        pendingInputs[key] = action

        // Start new timer (800ms)
        val timer = java.util.Timer()
        timer.schedule(object : java.util.TimerTask() {
            override fun run() {
                // Timer expired - add the final value
                val finalAction = pendingInputs.remove(key)
                if (finalAction != null) {
                    recordedActions.add(finalAction)
                    onActionRecorded(finalAction)
                }
                inputTimers.remove(key)
            }
        }, 800) // 800ms debounce delay

        inputTimers[key] = timer
    }

    /**
     * Stop recording
     */
    fun stopRecording() {
        println("[RecorderManager] Stopping recording...")
        isRecording = false

        // Flush any pending inputs immediately
        inputTimers.values.forEach { it.cancel() }
        inputTimers.clear()
        pendingInputs.values.forEach { action ->
            recordedActions.add(action)
        }
        pendingInputs.clear()

        recordingProcess?.destroy()
        recordingProcess = null
    }

    /**
     * Clear all recorded actions
     */
    fun clearActions() {
        println("[RecorderManager] Clearing ${recordedActions.size} recorded actions")
        recordedActions.clear()
        inputTimers.values.forEach { it.cancel() }
        inputTimers.clear()
        pendingInputs.clear()
        lastActionTimestamp = 0
        lastActionWasNavigation = false
        lastNavigationUrl = null
    }

    /**
     * Get all recorded actions
     */
    fun getActions(): List<RecordedAction> = recordedActions.toList()

    /**
     * Check if currently recording
     */
    fun isRecording(): Boolean = isRecording

    /**
     * Generate test code from recorded actions
     */
    fun generateCode(template: CodeTemplate, formatting: CodeFormatting, baseUrl: String): String {
        val skeleton = template.generateSkeleton(baseUrl, formatting)
        val indent = getIndent(formatting)

        // Build recorded actions code
        val actionsCode = StringBuilder()
        actionsCode.append("${indent}# Recorded Actions (${recordedActions.size} steps)\n")
        for ((index, action) in recordedActions.withIndex()) {
            actionsCode.append("${indent}# Step ${index + 1}: ${action}\n")
            actionsCode.append("${indent}${action.toCode(template, formatting)}\n")
        }

        // Replace the TODO placeholder with actual recorded actions
        return skeleton.replace("# TODO: Add actions here", actionsCode.toString().trimEnd())
    }

    /**
     * Generate Page Object Model code from recorded actions
     */
    fun generatePageObjectCode(template: CodeTemplate, formatting: CodeFormatting, baseUrl: String): String {
        val generator = PageObjectGenerator()
        val pageObjects = generator.extractPageObjects(recordedActions)
        return generator.generatePageObjectCode(pageObjects, template, formatting, baseUrl)
    }

    /**
     * Generate parameterized test code with reusable functions
     */
    fun generateParameterizedCode(template: CodeTemplate, formatting: CodeFormatting, baseUrl: String): String {
        val indent = getIndent(formatting)
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""

        // 1. Generate helper functions for parametrizable actions
        val functions = StringBuilder()
        val functionCalls = StringBuilder()
        val generatedFunctions = mutableSetOf<String>()

        functionCalls.append("${indent}# Recorded Actions (${recordedActions.size} steps)\n")

        for ((index, action) in recordedActions.withIndex()) {
            val funcName = generateFunctionName(action)

            when (action.type) {
                ActionType.FILL, ActionType.TYPE -> {
                    // Generate function if not already generated
                    if (!generatedFunctions.contains(funcName)) {
                        functions.append("def ${funcName}(page, text: str):\n")
                        functions.append("${indent}${quote}${quote}${quote}Fill ${action.selector} with text${quote}${quote}${quote}\n")

                        // Generate function body without the value
                        if (action.needsSelectorWait) {
                            functions.append("${indent}page.wait_for_selector($quote${action.selector}$quote, state=${quote}visible$quote)\n")
                        }
                        functions.append("${indent}page.locator($quote${action.selector}$quote).fill(text)\n")
                        functions.append("\n")

                        generatedFunctions.add(funcName)
                    }

                    // Add function call
                    functionCalls.append("${indent}# Step ${index + 1}: ${action}\n")
                    functionCalls.append("${indent}${funcName}(page, $quote${action.value ?: ""}$quote)\n")
                }
                ActionType.NAVIGATE -> {
                    // Generate navigation function if not already generated
                    if (!generatedFunctions.contains("navigate_to")) {
                        functions.append("def navigate_to(page, url: str):\n")
                        functions.append("${indent}${quote}${quote}${quote}Navigate to URL and wait for network idle${quote}${quote}${quote}\n")
                        functions.append("${indent}page.goto(url)\n")
                        functions.append("${indent}page.wait_for_load_state(${quote}networkidle$quote)\n")
                        functions.append("\n")

                        generatedFunctions.add("navigate_to")
                    }

                    // Add function call
                    functionCalls.append("${indent}# Step ${index + 1}: ${action}\n")
                    if (action.url != null) {
                        functionCalls.append("${indent}navigate_to(page, $quote${action.url}$quote)\n")
                    }
                }
                else -> {
                    // Non-parametrizable actions (click, hover, etc.) - inline
                    functionCalls.append("${indent}# Step ${index + 1}: ${action}\n")
                    functionCalls.append("${indent}${action.toCode(template, formatting)}\n")
                }
            }
        }

        // 2. Build final code structure
        val skeleton = template.generateSkeleton(baseUrl, formatting)

        // Insert functions before run() function
        val withFunctions = if (functions.isNotEmpty()) {
            val runFunctionIndex = skeleton.indexOf("def run(")
            if (runFunctionIndex > 0) {
                skeleton.substring(0, runFunctionIndex) +
                "# Parameterized action functions\n" +
                functions.toString() +
                "\n" +
                skeleton.substring(runFunctionIndex)
            } else {
                skeleton
            }
        } else {
            skeleton
        }

        // Replace TODO with function calls
        return withFunctions.replace("# TODO: Add actions here", functionCalls.toString().trimEnd())
    }

    private fun getIndent(formatting: CodeFormatting): String {
        return if (formatting.useTabs) "\t" else " ".repeat(formatting.indentSize)
    }

    /**
     * Generate a meaningful function name from selector
     * Examples:
     * - "#search-input" -> "search_input"
     * - "[data-testid='login-btn']" -> "login_btn"
     * - ".submit-button" -> "submit_button"
     */
    private fun generateFunctionName(action: RecordedAction): String {
        val selector = action.selector
        val actionType = action.type.name.lowercase()

        // Extract meaningful name from selector
        val baseName = when {
            // data-testid
            selector.contains("data-testid") -> {
                val match = Regex("""data-testid['"=\s]+([^'"\]]+)""").find(selector)
                match?.groupValues?.get(1)?.replace("-", "_")?.lowercase()
            }
            // ID selector
            selector.startsWith("#") -> {
                selector.substring(1).replace("-", "_").replace(".", "_").lowercase()
            }
            // Name attribute
            selector.contains("[name=") -> {
                val match = Regex("""name['"=\s]+([^'"\]]+)""").find(selector)
                match?.groupValues?.get(1)?.replace("-", "_")?.lowercase()
            }
            // Class selector (take first class)
            selector.startsWith(".") -> {
                selector.substring(1).split(".").first().replace("-", "_").lowercase()
            }
            // Fallback: sanitize selector
            else -> {
                selector.replace(Regex("[^a-zA-Z0-9_]"), "_").take(20).lowercase()
            }
        }

        return "${actionType}_${baseName ?: "element"}"
    }

    /**
     * Parse event line from Python CDP listener with smart wait detection
     * Format: TYPE|selector|value|url
     */
    private fun parseEventLine(line: String): RecordedAction? {
        if (!line.startsWith("EVENT:")) return null

        try {
            val parts = line.substring(6).split("|")
            if (parts.isEmpty()) return null

            val type = ActionType.valueOf(parts[0].uppercase())
            val selector = parts.getOrNull(1) ?: ""
            val value = parts.getOrNull(2)?.takeIf { it.isNotBlank() }
            val url = parts.getOrNull(3)?.takeIf { it.isNotBlank() }
            val currentTimestamp = System.currentTimeMillis()

            // Filter unwanted navigation events
            if (type == ActionType.NAVIGATE) {
                // Skip about:blank URLs
                if (url == "about:blank") {
                    return null
                }
                // Skip duplicate navigations to same URL
                if (url == lastNavigationUrl) {
                    return null
                }
                // Update last navigation URL
                lastNavigationUrl = url
            }

            // Detect if there was network activity (significant delay between actions)
            val timeSinceLastAction = currentTimestamp - lastActionTimestamp
            val hasNetworkActivity = timeSinceLastAction > 2000 // More than 2000ms suggests async activity

            // Detect if we need a navigation wait (action follows navigation)
            val needsNavigationWait = lastActionWasNavigation

            // Most actions need selector waits (except navigation)
            val needsSelectorWait = type != ActionType.NAVIGATE

            val action = RecordedAction(
                type = type,
                selector = selector,
                value = value,
                url = url,
                needsNavigationWait = needsNavigationWait,
                needsSelectorWait = needsSelectorWait,
                hasNetworkActivity = hasNetworkActivity && !lastActionWasNavigation
            )

            // Update tracking
            lastActionTimestamp = currentTimestamp
            lastActionWasNavigation = (type == ActionType.NAVIGATE)

            return action
        } catch (e: Exception) {
            println("[RecorderManager] Failed to parse event: $line - ${e.message}")
            return null
        }
    }

    /**
     * Create Python script for CDP event recording
     */
    private fun createRecorderScript(): String {
        return """
import sys
import json
from playwright.sync_api import sync_playwright

port = int(sys.argv[1])

print('[RECORDER] Connecting to browser on port ' + str(port), file=sys.stderr)

try:
    with sync_playwright() as p:
        browser = p.chromium.connect_over_cdp(f'http://localhost:{port}')
        print('[RECORDER] Connected successfully', file=sys.stderr)

        if len(browser.contexts) == 0 or len(browser.contexts[0].pages) == 0:
            print('[RECORDER] ERROR - No pages available', file=sys.stderr)
            sys.exit(1)

        page = browser.contexts[0].pages[0]
        print(f'[RECORDER] Monitoring page: {page.url}', file=sys.stderr)

        # Track last URL for navigation detection
        last_url = page.url

        # Listen for click events
        def on_click(event):
            try:
                target = event.get('target', {})
                selector = target.get('selector', 'unknown')
                print(f'EVENT:CLICK|{selector}||', flush=True)
            except Exception as e:
                print(f'[RECORDER] Click handler error: {e}', file=sys.stderr)

        # Listen for input events
        def on_input(event):
            try:
                target = event.get('target', {})
                selector = target.get('selector', 'unknown')
                value = event.get('value', '')
                print(f'EVENT:FILL|{selector}|{value}|', flush=True)
            except Exception as e:
                print(f'[RECORDER] Input handler error: {e}', file=sys.stderr)

        # Monitor navigation
        def on_navigation(frame):
            try:
                url = frame.url
                print(f'EVENT:NAVIGATE|||{url}', flush=True)
            except Exception as e:
                print(f'[RECORDER] Navigation handler error: {e}', file=sys.stderr)

        # Attach listeners
        page.on('framenavigated', on_navigation)

        print('[RECORDER] Event listeners attached. Recording started...', file=sys.stderr)
        print('[RECORDER] Press Ctrl+C or stop recording to finish', file=sys.stderr)

        # Keep script running and inject event listeners via JavaScript
        page.evaluate('''
            (() => {
                console.log('[RECORDER] Injecting event listeners...');

                // Track all clicks
                document.addEventListener('click', (e) => {
                    const target = e.target;
                    let selector = '';

                    // Generate selector for clicked element
                    if (target.id) {
                        selector = '#' + target.id;
                    } else if (target.getAttribute('data-testid')) {
                        selector = '[data-testid="' + target.getAttribute('data-testid') + '"]';
                    } else if (target.name) {
                        selector = '[name="' + target.name + '"]';
                    } else if (target.className) {
                        selector = '.' + target.className.split(' ')[0];
                    } else {
                        selector = target.tagName.toLowerCase();
                    }

                    // Send to console (Python will capture this)
                    console.log('RECORDER_EVENT:CLICK|' + selector);
                }, true);

                // Track all inputs
                document.addEventListener('input', (e) => {
                    const target = e.target;
                    let selector = '';

                    if (target.id) {
                        selector = '#' + target.id;
                    } else if (target.getAttribute('data-testid')) {
                        selector = '[data-testid="' + target.getAttribute('data-testid') + '"]';
                    } else if (target.name) {
                        selector = '[name="' + target.name + '"]';
                    } else {
                        selector = target.tagName.toLowerCase();
                    }

                    console.log('RECORDER_EVENT:FILL|' + selector + '|' + target.value);
                }, true);

                console.log('[RECORDER] Event listeners injected successfully');
            })();
        ''')

        # Listen to console messages to capture events
        page.on('console', lambda msg: handle_console_message(msg))

        def handle_console_message(msg):
            text = msg.text
            if text.startswith('RECORDER_EVENT:'):
                # Extract event data and print to stdout
                event_data = text[15:]  # Remove 'RECORDER_EVENT:' prefix
                print(f'EVENT:{event_data}', flush=True)

        # Keep running until interrupted
        try:
            while True:
                page.wait_for_timeout(1000)
        except KeyboardInterrupt:
            print('[RECORDER] Recording interrupted by user', file=sys.stderr)

        print('[RECORDER] Stopping recording...', file=sys.stderr)

except Exception as e:
    print(f'[RECORDER] FATAL ERROR: {e}', file=sys.stderr)
    import traceback
    traceback.print_exc()
    sys.exit(1)
        """.trimIndent()
    }

    fun dispose() {
        stopRecording()
    }
}
