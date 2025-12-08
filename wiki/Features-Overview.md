# Features Overview

Complete guide to ZÆCTOR CORE capabilities.

---

## 🔍 DOM Inspector

**Visual element inspection with intelligent selector generation**

### Key Features
- Hierarchical element tree view
- Shadow DOM support
- Real-time browser highlighting via CDP
- Selector quality scoring (1-10 scale)
- Multiple selector types (data-testid, id, name, aria-label, text, CSS)

### How to Use
1. Open `ZÆCTOR Inspector` tool window
2. Enter URL and click "Scan Page"
3. Browse element tree
4. Select element to view properties
5. Click element to highlight in browser

### Selector Quality Scores
- **10**: data-testid, data-cy, data-test (Best)
- **9**: Stable ID
- **8**: Name attribute
- **7**: Placeholder, aria-label
- **6**: Text content
- **5**: Title attribute
- **3**: CSS class (Fragile)
- **1**: Tag only (Avoid)

---

## 💻 Code Generation

**Multi-framework test code generation**

### Supported Frameworks
- Python Sync
- Pytest
- TypeScript
- JavaScript
- Robot Framework

### Features
- Action generation (click, fill, type, press, hover)
- Assertion generation (text, visibility, count)
- Customizable formatting (indentation, quotes)
- Copy to clipboard

### Example Output

**Python Sync:**
```python
page.locator('[data-testid="login-button"]').click()
page.locator('[data-testid="username"]').fill('user@example.com')
expect(page.locator('[data-testid="welcome"]')).to_have_text('Welcome')
```

**TypeScript:**
```typescript
await page.locator('[data-testid="login-button"]').click();
await page.locator('[data-testid="username"]').fill('user@example.com');
await expect(page.locator('[data-testid="welcome"]')).toHaveText('Welcome');
```

---

## 🎬 Live Recorder

**Record browser interactions and generate test code**

### Features
- Real-time action capture via CDP
- Smart wait injection (network activity detection)
- Input debouncing (800ms) for cleaner code
- Multi-tab recording support
- Action parametrization

### Smart Waits
- Navigation waits (`wait_for_load_state`)
- Selector waits (`wait_for_selector`)
- Network activity detection
- Automatic timeout handling

### How to Use
1. Open `ZÆCTOR Inspector → Recorder` tab
2. Click "Start Recording"
3. Interact with browser (click, type, navigate)
4. Click "Stop Recording"
5. Click "Generate Code"

---

## 🏗️ Page Object Generator

**Professional Page Object Model architecture**

### Features
- Smart page boundary detection (URL-based)
- Intelligent method extraction
- Pattern recognition (forms, buttons, navigation)
- Meaningful naming conventions
- Python & TypeScript support

### Generated Structure
```python
class LoginPage:
    def __init__(self, page):
        self.page = page
        self.username_field = '[data-testid="username"]'
        self.password_field = '[data-testid="password"]'
        self.submit_button = '[data-testid="submit"]'

    def login(self, username, password):
        self.page.locator(self.username_field).fill(username)
        self.page.locator(self.password_field).fill(password)
        self.page.locator(self.submit_button).click()
```

---

## 🧪 Test Explorer

**Discover, manage, and execute tests**

### Supported Frameworks
- Pytest
- Playwright Test
- unittest
- Robot Framework

### Features
- Automatic test discovery
- Hierarchical tree view (Folders → Files → Tests)
- Real-time status updates (✓ Passed, ✗ Failed, ⏸ Skipped)
- Run single test, file, or suite
- Stop execution
- Test details panel with output

### Context Menu
- Run
- Debug
- Open in Editor
- Copy Path

---

## 📋 Test Suite Management

**Configure and filter test suites**

### Configuration File
`.playwright-suites.json` in project root:

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
    },
    {
      "name": "E2E Tests",
      "pattern": "tests/e2e/**/*.py",
      "browsers": ["chromium", "firefox"],
      "tags": ["@e2e"],
      "parallel": 2,
      "timeout": 60000,
      "retries": 1
    }
  ]
}
```

### Features
- Pattern matching (glob-style)
- Tag-based filtering
- Browser selection per suite
- Exclude patterns
- Parallel execution configuration
- Retry settings

---

## ⚙️ Configuration

**Comprehensive settings**

### Code Generation
- Framework selection
- Indent size (1-8 spaces)
- Tabs vs spaces
- Quote style (single/double)

### Browser & Execution
- Default browser (Chromium, Firefox, WebKit)
- Viewport width (800-3840px)
- Viewport height (600-2160px)
- Headless mode

### Tool Windows
- Auto-expand Inspector on startup
- Auto-expand Library on startup

Access via: `Settings → Tools → ZÆCTOR`

---

## 📊 Test Results

### Real-time Updates
- Live output during execution
- Status updates (Passed/Failed/Skipped)
- Duration tracking
- Error messages and stack traces

### Details Panel
- Test status
- Execution time
- Last run timestamp
- Error messages
- Pass/fail statistics

---

## 🎯 Best Practices

### Selector Strategy
1. **Prefer data-testid** attributes (score: 10)
2. Use **stable IDs** when available (score: 9)
3. Avoid **CSS classes** (score: 3)
4. Never use **tag-only** selectors (score: 1)

### Recording Tips
1. Wait for page loads before recording
2. Use deliberate, slow interactions
3. Review generated code before saving
4. Parametrize repeated actions

### Page Objects
1. One page object per logical page/component
2. Keep methods focused (single responsibility)
3. Use meaningful names
4. Group related selectors

---

## 🔗 Integration

### Browser Integration
- Chrome DevTools Protocol (CDP)
- Remote debugging on port 9222
- Browser reuse (doesn't close existing browsers)

### IDE Integration
- Tool windows (Inspector, Tests, Library)
- Settings page
- Copy to clipboard
- Code insertion at cursor

---

**Explore more in the [Configuration](Configuration) guide!**
