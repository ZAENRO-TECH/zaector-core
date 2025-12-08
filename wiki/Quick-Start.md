# Quick Start Guide

Get up and running with ZÆCTOR CORE in 5 minutes!

---

## 1. Open ZÆCTOR Inspector

1. Go to `View → Tool Windows → ZÆCTOR Inspector`
2. The inspector opens at the bottom of your IDE

---

## 2. Inspect a Webpage

1. **Enter URL** in the Inspector (e.g., `https://example.com`)
2. Click **"Scan Page"**
3. Wait for the DOM tree to load

---

## 3. Generate Code

1. **Select an element** in the tree (e.g., a button)
2. **View selector** in the properties panel
3. **Choose action** (Click, Fill, Type, etc.)
4. Click **"Generate Code"**
5. Code is copied to clipboard!

**Paste** it into your test file.

---

## 4. Record Browser Interactions

1. Switch to the **"Recorder"** tab in Inspector
2. Click **"Start Recording"**
3. **Interact** with your webpage (click, type, navigate)
4. Click **"Stop Recording"**
5. Click **"Generate Code"** to get the test code

---

## 5. Generate Page Objects

1. **Record** your interactions (see step 4)
2. Click **"Generate Page Objects"**
3. Professional POM code is generated!
4. Code includes:
   - Page class with selectors
   - Methods for actions
   - Clean, maintainable structure

---

## 6. Discover Tests

1. Go to `View → Tool Windows → ZÆCTOR Tests`
2. Click **Refresh** button
3. All tests in your project are discovered automatically!
4. Supports: Pytest, Playwright Test, unittest, Robot Framework

---

## 7. Run Tests

1. In **ZÆCTOR Tests** window, select a test
2. **Right-click → Run** or double-click
3. Watch real-time output in the details panel
4. Test status updates live (✓ Passed, ✗ Failed)

---

## 8. Configure Test Suites

1. In **ZÆCTOR Tests**, click **"Create Config"**
2. Edit `.playwright-suites.json` in your project root
3. Define suites:
   ```json
   {
     "suites": [
       {
         "name": "Smoke Tests",
         "pattern": "tests/**/*smoke*.py",
         "tags": ["@smoke"],
         "browsers": ["chromium"]
       }
     ]
   }
   ```
4. Select suite from dropdown to filter tests

---

## 9. Customize Settings

Go to `Settings → Tools → ZÆCTOR` to configure:

- **Code Generation**: Framework, indent style, quotes
- **Browser**: Default browser (Chromium/Firefox/WebKit)
- **Viewport**: Width and height
- **Tool Windows**: Auto-expand on startup

---

## Next Steps

- [Features Overview](Features-Overview) - Explore all features
- [Configuration](Configuration) - Advanced settings
- [Building from Source](Building-from-Source) - Contribute!

---

## Example Workflow

```python
# 1. Inspect page and generate selector
page.locator('[data-testid="login-button"]').click()

# 2. Record interactions and generate Page Object
class LoginPage:
    def __init__(self, page):
        self.page = page
        self.username_input = '[data-testid="username"]'
        self.password_input = '[data-testid="password"]'
        self.login_button = '[data-testid="login-button"]'

    def login(self, username, password):
        self.page.locator(self.username_input).fill(username)
        self.page.locator(self.password_input).fill(password)
        self.page.locator(self.login_button).click()

# 3. Discover and run tests
# Use ZÆCTOR Tests to find and execute all your tests!
```

---

**You're ready to go! Happy testing! 🚀**
