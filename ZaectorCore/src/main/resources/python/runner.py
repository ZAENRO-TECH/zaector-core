import sys
import json
import time
import threading
import os
from playwright.sync_api import sync_playwright

DEBUG_PORT = 9222

# --- Parent Watchdog Thread ---
# This thread monitors whether the "parent" (PyCharm/Java) is still alive.
# If Java terminates or crashes, the standard input pipe (stdin) is closed.
# Python detects this (EOF) and immediately terminates itself.
def parent_watchdog():
    try:
        # Read 1 byte. Since Java doesn't send anything, this blocks until Java closes.
        # As soon as Java closes, read() returns (EOF).
        sys.stdin.read()

        # If we get here, Java is gone.
        # os._exit(0) is a hard kill without cleanup (prevents hanging)
        os._exit(0)
    except Exception:
        os._exit(1)

def run_scan(js_file_path, target_url, headless_mode, keep_open):
    print(f"[SCANNER] Starting scan - URL: {target_url}, Headless: {headless_mode}, Keep Open: {keep_open}", file=sys.stderr)

    # Start watchdog (daemon dies when main thread dies)
    t = threading.Thread(target=parent_watchdog, daemon=True)
    t.start()

    try:
        # Load JS code
        with open(js_file_path, 'r', encoding='utf-8') as f:
            js_scanner_code = f.read()

        with sync_playwright() as p:
            browser = None
            page = None
            is_existing_browser = False

            # --- PHASE 1: Find or start browser ---
            print(f"[SCANNER] Attempting to connect to existing browser on port {DEBUG_PORT}...", file=sys.stderr)
            try:
                browser = p.chromium.connect_over_cdp(f"http://localhost:{DEBUG_PORT}")
                is_existing_browser = True
                print(f"[SCANNER] SUCCESS - Connected to existing browser", file=sys.stderr)

                if len(browser.contexts) > 0 and len(browser.contexts[0].pages) > 0:
                    page = browser.contexts[0].pages[0]
                    print(f"[SCANNER] Using existing page: {page.url}", file=sys.stderr)
                else:
                    page = browser.new_page()
                    print(f"[SCANNER] Created new page in existing browser", file=sys.stderr)

            except Exception as e:
                # Start new browser
                print(f"[SCANNER] No existing browser found ({e}), launching new one...", file=sys.stderr)
                is_existing_browser = False
                is_headless = headless_mode.lower() == 'true'
                args = [f"--remote-debugging-port={DEBUG_PORT}"]

                browser = p.chromium.launch(headless=is_headless, args=args)
                page = browser.new_page()
                print(f"[SCANNER] Browser launched successfully", file=sys.stderr)

                if target_url:
                    print(f"[SCANNER] Navigating to {target_url}...", file=sys.stderr)
                    page.goto(target_url)
                    try:
                        page.wait_for_load_state("networkidle", timeout=5000)
                        print(f"[SCANNER] Page loaded", file=sys.stderr)
                    except:
                        print(f"[SCANNER] Page load timeout (continuing anyway)", file=sys.stderr)
                        pass

            # --- PHASE 2: Scan ---
            print(f"[SCANNER] Starting DOM scan...", file=sys.stderr)
            try:
                page.evaluate(js_scanner_code)
                elements = page.evaluate("window.__PLAYWRIGHT_SCANNER__.scan()")
                print(f"[SCANNER] Scan complete - found {len(elements)} elements", file=sys.stderr)

                try:
                    print(json.dumps(elements, indent=2))
                    sys.stdout.flush()
                except Exception:
                    pass

            except Exception as e:
                print(f"[SCANNER] ERROR - Scan failed: {str(e)}", file=sys.stderr)

            # --- PHASE 3: Intelligent keep-alive ---
            should_keep_open = keep_open.lower() == 'true'
            print(f"[SCANNER] Browser lifecycle - is_existing: {is_existing_browser}, keep_open: {should_keep_open}", file=sys.stderr)

            if is_existing_browser:
                # IMPORTANT: Do NOT call browser.close() when connected via CDP!
                # It will actually CLOSE the browser, not just disconnect.
                # We want to leave the browser running for highlighting and future scans.
                print(f"[SCANNER] Connected to existing browser - leaving it open (not calling close())", file=sys.stderr)
                # Do nothing - let the context manager handle cleanup

            else:
                # We launched a new browser
                if should_keep_open:
                    print("[SCANNER] Browser launched. Keeping open for CDP access...", file=sys.stderr)
                    print("[SCANNER] Browser will stay open until manually closed by user", file=sys.stderr)
                    try:
                        # Actively check if browser is still alive
                        while browser.is_connected():
                            time.sleep(1)
                        print("[SCANNER] Browser was closed by user", file=sys.stderr)
                    except KeyboardInterrupt:
                        print("[SCANNER] Scan interrupted by user", file=sys.stderr)
                        pass
                    except Exception as e:
                        print(f"[SCANNER] Browser monitoring error: {e}", file=sys.stderr)
                        pass
                else:
                    print("[SCANNER] Keep Open disabled - closing browser...", file=sys.stderr)
                    browser.close()
                    print("[SCANNER] Browser closed", file=sys.stderr)

    except Exception as e:
        print(f"CRITICAL ERROR: {str(e)}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    headless = "false"
    keep_open = "true"
    if len(sys.argv) > 3: headless = sys.argv[3]
    if len(sys.argv) > 4: keep_open = sys.argv[4]

    run_scan(sys.argv[1], sys.argv[2], headless, keep_open)