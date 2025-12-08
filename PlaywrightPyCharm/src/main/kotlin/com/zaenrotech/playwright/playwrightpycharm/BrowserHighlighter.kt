package com.zaenrotech.playwright.playwrightpycharm

import com.intellij.execution.configurations.GeneralCommandLine
import java.nio.charset.StandardCharsets

/**
 * Manages browser highlighting via Chrome DevTools Protocol
 */
class BrowserHighlighter {
    private var currentProcess: Process? = null
    private var cdpPort: Int = 9222
    @Volatile private var isHighlighting = false

    /**
     * Highlight an element in the browser
     */
    fun highlightElement(selector: String, url: String) {
        // Prevent multiple simultaneous highlights
        if (isHighlighting) {
            println("[BrowserHighlighter] Already highlighting, skipping...")
            return
        }

        Thread {
            try {
                isHighlighting = true
                println("[BrowserHighlighter] Starting highlight for selector: $selector")

                val pythonScript = createHighlightScript()
                val commandLine = GeneralCommandLine(
                    "python", "-c", pythonScript, selector, cdpPort.toString()
                )
                commandLine.charset = StandardCharsets.UTF_8

                println("[BrowserHighlighter] Executing Python script...")
                currentProcess?.destroy()
                val process = commandLine.createProcess()
                currentProcess = process

                // Capture output in separate threads
                val stdoutReader = Thread {
                    process.inputStream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            println("[Python STDOUT] $line")
                        }
                    }
                }
                val stderrReader = Thread {
                    process.errorStream.bufferedReader().use { reader ->
                        reader.forEachLine { line ->
                            println("[Python STDERR] $line")
                        }
                    }
                }
                stdoutReader.start()
                stderrReader.start()

                // Wait max 5 seconds (increased for debugging)
                println("[BrowserHighlighter] Waiting for process (max 5 seconds)...")
                val finished = process.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)
                if (!finished) {
                    println("[BrowserHighlighter] WARNING - Process timeout! Force killing...")
                    process.destroyForcibly()
                } else {
                    val exitCode = process.exitValue()
                    println("[BrowserHighlighter] Process finished with exit code: $exitCode")
                }

                // Wait for output readers to finish
                stdoutReader.join(500)
                stderrReader.join(500)

            } catch (e: Exception) {
                println("[BrowserHighlighter] ERROR - Exception: ${e.message}")
                e.printStackTrace()
            } finally {
                isHighlighting = false
                println("[BrowserHighlighter] Highlight complete")
            }
        }.start()
    }

    /**
     * Remove highlight from browser
     */
    fun clearHighlight() {
        if (isHighlighting) return

        Thread {
            try {
                isHighlighting = true
                val pythonScript = createClearHighlightScript()
                val commandLine = GeneralCommandLine(
                    "python", "-c", pythonScript, cdpPort.toString()
                )
                commandLine.charset = StandardCharsets.UTF_8
                currentProcess?.destroy()
                val process = commandLine.createProcess()
                currentProcess = process

                val finished = process.waitFor(2, java.util.concurrent.TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                }
            } catch (e: Exception) {
                // Silently fail if CDP not available
            } finally {
                isHighlighting = false
            }
        }.start()
    }

    /**
     * Check if browser is available via CDP
     */
    fun isBrowserAvailable(): Boolean {
        return try {
            // Quick check: try to connect to CDP port
            val socket = java.net.Socket()
            socket.connect(java.net.InetSocketAddress("localhost", cdpPort), 500)
            socket.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createHighlightScript(): String {
        return """
import sys
from playwright.sync_api import sync_playwright

selector = sys.argv[1]
port = int(sys.argv[2])

print(f'[DEBUG] Starting highlight for selector: {selector}')
print(f'[DEBUG] Connecting to CDP on port: {port}')

try:
    with sync_playwright() as p:
        print('[DEBUG] Playwright started')
        browser = p.chromium.connect_over_cdp(f'http://localhost:{port}')
        print(f'[DEBUG] Connected to browser, contexts: {len(browser.contexts)}')

        if len(browser.contexts) > 0 and len(browser.contexts[0].pages) > 0:
            page = browser.contexts[0].pages[0]
            print(f'[DEBUG] Got page: {page.url}')

            # Clear previous highlights
            print('[DEBUG] Clearing previous highlights...')
            page.evaluate('''() => {
                const count = document.querySelectorAll('.playwright-highlight').length;
                document.querySelectorAll('.playwright-highlight').forEach(el => {
                    el.classList.remove('playwright-highlight');
                });
                console.log('Cleared ' + count + ' previous highlights');
            }''')

            # Add style if needed
            print('[DEBUG] Adding highlight styles...')
            page.evaluate('''() => {
                if (!document.getElementById('playwright-highlight-style')) {
                    const style = document.createElement('style');
                    style.id = 'playwright-highlight-style';
                    style.textContent = '.playwright-highlight { outline: 3px solid #FF6B6B !important; outline-offset: 2px !important; background-color: rgba(255, 107, 107, 0.1) !important; box-shadow: 0 0 10px rgba(255, 107, 107, 0.5) !important; }';
                    document.head.appendChild(style);
                    console.log('Highlight style added');
                } else {
                    console.log('Highlight style already exists');
                }
            }''')

            # Highlight element using Playwright locator
            print(f'[DEBUG] Finding element with selector: {selector}')
            try:
                locator = page.locator(selector)
                count = locator.count()
                print(f'[DEBUG] Found {count} elements matching selector')

                if count > 0:
                    print('[DEBUG] Scrolling element into view...')
                    locator.first.scroll_into_view_if_needed()

                    print('[DEBUG] Adding highlight class...')
                    locator.first.evaluate('''element => {
                        element.classList.add('playwright-highlight');
                        console.log('Highlight added to element:', element);
                    }''')
                    print('[DEBUG] SUCCESS - Highlight applied successfully!')
                else:
                    print('[DEBUG] ERROR - No elements found for selector!')
            except Exception as e:
                print(f'[DEBUG] ERROR - Highlight error: {e}')
                import traceback
                traceback.print_exc()
        else:
            print('[DEBUG] ERROR - No pages available in browser context')

        # IMPORTANT: Do NOT call browser.close()!
        # When connected via CDP, browser.close() will actually CLOSE the browser,
        # not just disconnect. We want to leave the browser running.
        print('[DEBUG] Leaving browser open (not calling browser.close())')
        print('[DEBUG] Done!')

except Exception as e:
    print(f'[DEBUG] FATAL ERROR - Script error: {e}')
    import traceback
    traceback.print_exc()
        """.trimIndent()
    }

    private fun createClearHighlightScript(): String {
        return """
import sys
from playwright.sync_api import sync_playwright

port = int(sys.argv[1])

print('[DEBUG] Clearing highlights...')

try:
    with sync_playwright() as p:
        browser = p.chromium.connect_over_cdp(f"http://localhost:{port}")
        print(f'[DEBUG] Connected to browser on port {port}')

        if len(browser.contexts) > 0 and len(browser.contexts[0].pages) > 0:
            page = browser.contexts[0].pages[0]
            print('[DEBUG] Got page, removing highlights...')

            # Remove all highlights using page.evaluate
            page.evaluate('''() => {
                const count = document.querySelectorAll('.playwright-highlight').length;
                document.querySelectorAll('.playwright-highlight').forEach(el => {
                    el.classList.remove('playwright-highlight');
                });
                console.log('Removed ' + count + ' highlights');
            }''')
            print('[DEBUG] Highlights cleared successfully')

        # Do NOT close browser - leave it running!
        print('[DEBUG] Leaving browser open')

except Exception as e:
    print(f'[DEBUG] Clear highlight error: {e}')
    pass
        """.trimIndent()
    }

    fun dispose() {
        currentProcess?.destroy()
    }
}
