# ZÆCTOR CORE

**Professional test automation for PyCharm & IntelliJ IDEA**

Built by [ZÆNRO TECH](https://zaenro.tech)

[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)
[![JetBrains](https://img.shields.io/badge/JetBrains-Plugin-000000.svg?logo=jetbrains)](https://plugins.jetbrains.com/)

---

## Overview

ZÆCTOR CORE is an open-source PyCharm plugin that supercharges your Playwright test automation workflow. It combines visual DOM inspection, intelligent code generation, live recording, and comprehensive test management in one seamless IDE experience.

## Features

### DOM Inspection & Code Generation
- Visual element inspector with tree view
- Smart selector generation with quality scoring (1-10)
- Multi-framework code generation (Python, TypeScript, JavaScript, Robot Framework)
- Browser highlighting via Chrome DevTools Protocol
- Copy-to-clipboard selectors

### Live Recording
- Record browser interactions in real-time
- Smart wait injection based on network activity
- Input debouncing for cleaner code
- Action parametrization for reusable functions
- Multi-tab recording support

### Page Object Generator
- Professional Page Object Model architecture
- Smart page boundary detection
- Intelligent method extraction
- Pattern recognition (forms, buttons, navigation)
- Python & TypeScript support

### Test Explorer
- Multi-framework test discovery (Pytest, Playwright Test, unittest, Robot Framework)
- Hierarchical test tree view
- Real-time status updates during execution
- Run single tests, files, or entire suites
- Test suite configuration with filtering

### Configuration
- Browser selection (Chromium, Firefox, WebKit)
- Viewport size customization
- Code formatting preferences
- Suite definitions (Smoke, E2E, Regression)
- Tag-based test filtering

## Installation

### From JetBrains Marketplace
1. Open PyCharm/IntelliJ IDEA
2. Go to `Settings → Plugins → Marketplace`
3. Search for "ZÆCTOR CORE"
4. Click `Install`

### From Source
```bash
git clone https://github.com/zaenro-tech/zactor-core.git
cd zactor-core/PlaywrightPyCharm
./gradlew buildPlugin
```

The plugin will be in `build/distributions/`

## Requirements

- PyCharm 2025.2+ or IntelliJ IDEA 2025.2+
- Java 21+
- Python 3.8+
- Playwright (install via `pip install playwright`)

## Quick Start

1. **Install Playwright:**
   ```bash
   pip install playwright
   playwright install
   ```

2. **Open Tool Windows:**
   - `View → Tool Windows → Playwright Inspector`
   - `View → Tool Windows → Playwright Tests`

3. **Inspect Elements:**
   - Enter URL in Inspector
   - Click "Scan Page"
   - Select elements to generate code

4. **Run Tests:**
   - Open Test Explorer
   - Click refresh to discover tests
   - Right-click test → Run

## Configuration

Access settings via `Settings → Tools → Playwright`

**Code Generation:**
- Framework (Python Sync, Pytest, TypeScript, JavaScript, Robot)
- Indent size and style
- Quote preferences

**Browser & Execution:**
- Default browser
- Viewport dimensions
- Headless mode

**Test Suites:**
- Custom suite definitions
- Pattern matching
- Tag filtering

## Architecture

ZÆCTOR CORE uses a three-layer architecture:

1. **IDE Layer (Kotlin)**: UI components, IntelliJ Platform integration
2. **Automation Layer (Python)**: Browser control via Playwright
3. **Scanner Layer (JavaScript)**: DOM analysis via CDP injection

## Contributing

We welcome contributions! See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

## License

Copyright 2025 ZÆNRO TECH

Licensed under the Apache License, Version 2.0. See [LICENSE](LICENSE) for details.

## Business Model

ZÆCTOR CORE follows an **Open Core** model:

- **CORE (Free & Open Source)**: All features in this repository
- **PRO (€15/month)**: Visual regression, advanced analytics, mobile testing
- **ENTERPRISE (€75/month)**: Team collaboration, ALM integrations, AI features, priority support

See [LICENSE_STRATEGY.md](LICENSE_STRATEGY.md) for complete details.

## Support

- **Issues**: [GitHub Issues](https://github.com/zaenro-tech/zactor-core/issues)
- **Documentation**: [Wiki](https://github.com/zaenro-tech/zactor-core/wiki)
- **Community**: [Discussions](https://github.com/zaenro-tech/zactor-core/discussions)

## Links

- [ZÆNRO TECH](https://zaenro.tech)
- [JetBrains Plugin Page](https://plugins.jetbrains.com/)
- [Documentation](https://github.com/zaenro-tech/zactor-core/wiki)

---

**Made with ❤️ by ZÆNRO TECH**
