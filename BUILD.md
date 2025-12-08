# Build Instructions

## Prerequisites

- **Java 21+** (JDK)
  Download from: https://adoptium.net/
- **Gradle 8.5+** (included via wrapper)

## Set JAVA_HOME

### Windows (PowerShell)
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
```

### Windows (CMD)
```cmd
set JAVA_HOME=C:\Program Files\Java\jdk-21
```

### Linux/Mac
```bash
export JAVA_HOME=/path/to/jdk-21
```

## Build Plugin

```bash
cd PlaywrightPyCharm
./gradlew buildPlugin
```

**Output**: `build/distributions/PlaywrightPyCharm-1.0.0.zip`

## Verify Plugin

```bash
./gradlew verifyPlugin
```

## Run in Sandbox

```bash
./gradlew runIde
```

## Create GitHub Release

1. Build the plugin (see above)
2. Go to: https://github.com/ZAENRO-TECH/zaector-core/releases/new
3. Create tag: `v1.0.0`
4. Title: **ZÆCTOR CORE v1.0.0 - Initial Release**
5. Upload: `build/distributions/PlaywrightPyCharm-1.0.0.zip`
6. Add release notes:

```markdown
# ZÆCTOR CORE v1.0.0

First open source release of ZÆCTOR CORE - Professional test automation plugin for PyCharm and IntelliJ IDEA.

## Features

- **DOM Inspector** - Visual element inspection with smart selector generation
- **Code Generator** - Multi-framework code generation (Python, TypeScript, JavaScript, Robot Framework)
- **Live Recorder** - Record browser interactions with intelligent wait injection
- **Page Object Generator** - Professional POM architecture from recordings
- **Test Explorer** - Discover, manage, and execute tests across multiple frameworks
- **Suite Management** - Configure and run test suites with filtering and tagging

## Installation

1. Download `PlaywrightPyCharm-1.0.0.zip`
2. In PyCharm: `Settings → Plugins → ⚙️ → Install Plugin from Disk...`
3. Select the downloaded ZIP file
4. Restart IDE

## Requirements

- PyCharm 2025.2+ or IntelliJ IDEA 2025.2+
- Java 21+
- Python 3.8+
- Playwright (`pip install playwright`)

## Documentation

See the [Wiki](https://github.com/ZAENRO-TECH/zaector-core/wiki) for complete documentation.

## License

Apache 2.0 - Free and open source forever.

---

**Built with ❤️ by ZÆNRO TECH**
```

7. Publish release

## Troubleshooting

### JAVA_HOME not set
```
ERROR: JAVA_HOME is not set
```
**Fix**: Set JAVA_HOME environment variable (see above)

### Gradle wrapper not executable
```bash
chmod +x gradlew
```

### Build fails
```bash
# Clean and rebuild
./gradlew clean buildPlugin
```
