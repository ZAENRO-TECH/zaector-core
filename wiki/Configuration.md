# Configuration Guide

Complete guide to configuring ZÆCTOR CORE.

---

## Accessing Settings

`Settings → Tools → ZÆCTOR`

---

## Code Generation Settings

### Framework Selection
Choose your preferred test framework:
- **Python Sync** - Synchronous Playwright Python
- **Pytest** - Pytest with Playwright
- **TypeScript** - Playwright TypeScript
- **JavaScript** - Playwright JavaScript
- **Robot Framework** - Robot Framework with Browser library

### Indentation
- **Indent Size**: 1-8 spaces (default: 4)
- **Use Tabs**: Toggle between spaces and tabs

### Quote Style
- **Double Quotes**: `"selector"` (default)
- **Single Quotes**: `'selector'`

---

## Browser & Execution Settings

### Default Browser
Choose which browser to use for scanning:
- **chromium** (default) - Google Chrome/Chromium
- **firefox** - Mozilla Firefox
- **webkit** - Safari WebKit

### Viewport Size
Set default viewport dimensions:
- **Width**: 800-3840px (default: 1280)
- **Height**: 600-2160px (default: 720)

### Headless Mode
Run browser without GUI:
- **Disabled** (default) - Show browser window
- **Enabled** - Run headless

---

## Tool Window Settings

### Auto-Expand on Startup
Configure which tool windows open automatically when IDE starts:
- **ZÆCTOR Inspector** - DOM inspection tool
- **ZÆCTOR Library** - Framework file browser

---

## Test Suite Configuration

### Config File Location
Default: Project root directory

Custom path: `Settings → Tools → ZÆCTOR → Config directory`

### Suite Definition File

Create `.playwright-suites.json` in your config directory:

```json
{
  "suites": [
    {
      "name": "Smoke Tests",
      "pattern": "tests/**/*smoke*.py",
      "exclude": ["**/wip_*.py"],
      "browsers": ["chromium"],
      "tags": ["@smoke"],
      "parallel": 1,
      "timeout": 30000,
      "retries": 0
    }
  ]
}
```

### Suite Properties

#### name (required)
Suite display name in UI

#### pattern (required)
Glob pattern to match test files
- `tests/**/*.py` - All Python tests
- `tests/e2e/**/*` - All E2E tests
- `**/*smoke*.py` - All smoke tests

#### exclude (optional)
Array of patterns to exclude
```json
"exclude": ["**/wip_*.py", "**/*_draft.py"]
```

#### browsers (optional)
Browsers to run tests on
```json
"browsers": ["chromium", "firefox", "webkit"]
```

#### tags (optional)
Filter tests by markers/tags
```json
"tags": ["@smoke", "@critical"]
```

#### parallel (optional)
Number of parallel workers (default: 1)
```json
"parallel": 4
```

#### timeout (optional)
Test timeout in milliseconds (default: 30000)
```json
"timeout": 60000
```

#### retries (optional)
Number of retries for failed tests (default: 0)
```json
"retries": 2
```

---

## Example Configurations

### Basic Setup
```json
{
  "suites": [
    {
      "name": "All Tests",
      "pattern": "tests/**/*.py",
      "browsers": ["chromium"]
    }
  ]
}
```

### Multi-Browser Testing
```json
{
  "suites": [
    {
      "name": "Cross-Browser Tests",
      "pattern": "tests/**/*.py",
      "browsers": ["chromium", "firefox", "webkit"],
      "parallel": 3
    }
  ]
}
```

### Tagged Test Suites
```json
{
  "suites": [
    {
      "name": "Smoke Tests",
      "pattern": "tests/**/*.py",
      "tags": ["@smoke"],
      "browsers": ["chromium"],
      "timeout": 30000
    },
    {
      "name": "Regression",
      "pattern": "tests/**/*.py",
      "exclude": ["**/smoke_*.py"],
      "browsers": ["chromium", "firefox"],
      "parallel": 4,
      "retries": 2
    }
  ]
}
```

### Framework-Specific Patterns

**Pytest:**
```json
{
  "pattern": "tests/**/test_*.py",
  "tags": ["@pytest.mark.smoke"]
}
```

**Playwright Test:**
```json
{
  "pattern": "tests/**/*.spec.ts"
}
```

**Robot Framework:**
```json
{
  "pattern": "tests/**/*.robot",
  "tags": ["Smoke"]
}
```

---

## Settings Persistence

Settings are saved to:
```
~/.PyCharm/config/options/playwright_plugin_settings.xml
```

### Reset Settings
Delete the settings file and restart IDE to reset to defaults.

---

## Environment Variables

### JAVA_HOME
Required for building plugin from source:
```bash
export JAVA_HOME=/path/to/jdk-21
```

### PATH
Ensure Python and Playwright are in PATH:
```bash
python --version
playwright --version
```

---

## Troubleshooting

### Settings not saving
- Check IDE has write permissions to config directory
- Try resetting settings by deleting the XML file

### Browser not launching
- Verify Playwright installation: `playwright install`
- Check default browser setting
- Try switching to different browser

### Tests not discovered
- Check pattern in suite configuration
- Verify test framework is supported
- Ensure tests follow naming conventions

---

## Advanced Configuration

### Custom Python Interpreter
Configure in PyCharm project settings:
`Settings → Project → Python Interpreter`

### Playwright Configuration
Place `playwright.config.ts` in project root for Playwright-specific settings.

### IDE Performance
For large projects, adjust IDE memory settings:
`Help → Edit Custom VM Options`
```
-Xmx2048m
-Xms512m
```

---

**For more help, see [Quick Start](Quick-Start) or [Features Overview](Features-Overview)**
