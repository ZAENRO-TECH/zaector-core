# Installation Guide

## Prerequisites

Before installing ZÆCTOR CORE, ensure you have:

- **PyCharm 2025.2+** or **IntelliJ IDEA 2025.2+**
- **Java 21+**
- **Python 3.8+**
- **Playwright** (install via pip)

---

## Method 1: JetBrains Marketplace (Recommended)

1. Open PyCharm or IntelliJ IDEA
2. Go to `Settings → Plugins → Marketplace`
3. Search for **"ZÆCTOR CORE"**
4. Click **Install**
5. Restart IDE

---

## Method 2: Install from ZIP

1. Download the latest release from [GitHub Releases](https://github.com/ZAENRO-TECH/zaector-core/releases)
2. In PyCharm/IntelliJ: `Settings → Plugins → ⚙️ → Install Plugin from Disk...`
3. Select the downloaded ZIP file
4. Restart IDE

---

## Method 3: Build from Source

```bash
# Clone repository
git clone https://github.com/ZAENRO-TECH/zaector-core.git
cd zaector-core/ZaectorCore

# Build plugin
./gradlew buildPlugin

# Plugin ZIP will be in: build/distributions/
```

Then install the ZIP file using Method 2.

---

## Install Playwright

After installing the plugin, install Playwright:

```bash
pip install playwright
playwright install
```

---

## Verify Installation

1. Restart PyCharm/IntelliJ
2. Check `View → Tool Windows` - you should see:
   - **ZÆCTOR Inspector**
   - **ZÆCTOR Tests**
   - **ZÆCTOR Library**

3. Go to `Settings → Tools → ZÆCTOR` to configure

---

## Next Steps

- [Quick Start Guide](Quick-Start) - Get started in 5 minutes
- [Configuration](Configuration) - Customize settings
- [Features Overview](Features-Overview) - Explore capabilities

---

## Troubleshooting

### Plugin not appearing

- Verify IDE version is 2025.2 or newer
- Check Java version: `java -version` (should be 21+)
- Restart IDE after installation

### Playwright not found

```bash
# Install Playwright
pip install playwright

# Install browsers
playwright install
```

### Permission errors

Run IDE as administrator (Windows) or check file permissions (Linux/Mac)
