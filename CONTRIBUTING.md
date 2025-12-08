# Contributing to ZÆCTOR CORE

Thank you for your interest in contributing to ZÆCTOR CORE!

## Code of Conduct

Be respectful, professional, and constructive in all interactions.

## How to Contribute

### Reporting Bugs

1. Check [existing issues](https://github.com/zaenro-tech/zactor-core/issues) first
2. Create a new issue with:
   - Clear title
   - Steps to reproduce
   - Expected vs actual behavior
   - Environment (OS, PyCharm version, Python version)
   - Screenshots if applicable

### Suggesting Features

1. Check [discussions](https://github.com/zaenro-tech/zactor-core/discussions) first
2. Create a feature request with:
   - Use case description
   - Proposed solution
   - Alternatives considered
   - Impact on existing features

### Code Contributions

1. **Fork the repository**
2. **Create a feature branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```

3. **Make your changes:**
   - Follow existing code style
   - Keep code clean and readable
   - Remove unnecessary comments
   - Add essential documentation only

4. **Test your changes:**
   ```bash
   cd ZaectorCore
   ./gradlew build
   ./gradlew runIde
   ```

5. **Commit with clear messages:**
   ```bash
   git commit -m "Add feature: description"
   ```

6. **Push to your fork:**
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a Pull Request**

## Development Setup

### Requirements
- Java 21+
- Gradle 8.5+
- IntelliJ IDEA (recommended)

### Build Commands
```bash
cd ZaectorCore

# Build plugin
./gradlew build

# Run in sandbox
./gradlew runIde

# Create distribution
./gradlew buildPlugin

# Run tests
./gradlew test
```

### Project Structure
```
ZaectorCore/
├── src/main/kotlin/          # Kotlin source (IDE layer)
│   └── com/zaenrotech/zaector/core/
├── src/main/resources/        # Resources
│   ├── python/runner.py       # Browser automation
│   ├── scanner/dom_scanner.js # DOM analysis
│   └── META-INF/plugin.xml    # Plugin descriptor
└── build.gradle.kts           # Build configuration
```

## Code Style

### Kotlin
- Use meaningful variable names
- Keep functions focused and concise
- Minimize comments (code should be self-documenting)
- Use IntelliJ IDEA formatter

### Python
- Follow PEP 8
- Use type hints
- Keep functions under 50 lines

### JavaScript
- Use ES6+ features
- Consistent naming conventions
- Minimal comments

## Testing

- Test all changes in PyCharm sandbox (`./gradlew runIde`)
- Verify multi-framework support (Pytest, Playwright Test, unittest)
- Test on Windows and Linux if possible

## Documentation

- Update README.md if adding features
- Update CLAUDE.md for development guidance
- Keep comments minimal and essential

## License

By contributing, you agree that your contributions will be licensed under Apache 2.0.

All contributors must agree to the terms in [LICENSE](LICENSE).

## Questions?

- Create a [discussion](https://github.com/zaenro-tech/zactor-core/discussions)
- Check [documentation](https://github.com/zaenro-tech/zactor-core/wiki)

---

**Thank you for contributing to ZÆCTOR CORE!**
