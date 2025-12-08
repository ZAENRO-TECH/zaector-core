package com.zaenrotech.playwright.playwrightpycharm

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import java.io.File

/**
 * Discovers tests in the project
 * Supports: Pytest, Playwright Test, unittest
 */
class TestDiscoveryEngine(private val project: Project) {

    /**
     * Discover all tests in the project
     */
    fun discoverTests(rootPath: String? = null): TestSuite {
        val basePath = rootPath ?: project.basePath ?: return TestSuite("No Project", "")

        println("[TestDiscovery] Scanning for tests in: $basePath")

        val suite = TestSuite(
            name = project.name,
            path = basePath
        )

        // Find all test directories
        val testDirs = findTestDirectories(basePath)
        println("[TestDiscovery] Found ${testDirs.size} test directories")

        testDirs.forEach { testDir ->
            val folder = scanDirectory(testDir, basePath)
            if (folder != null) {
                suite.folders.add(folder)
            }
        }

        suite.updateStats()
        println("[TestDiscovery] Discovery complete: ${suite.totalTests} tests found")

        return suite
    }

    /**
     * Find test directories in project
     */
    private fun findTestDirectories(basePath: String): List<File> {
        val testDirs = mutableListOf<File>()
        val root = File(basePath)

        if (!root.exists() || !root.isDirectory) {
            return testDirs
        }

        // Common test directory names
        val testDirNames = setOf("tests", "test", "e2e", "integration", "unit")

        fun scanForTestDirs(dir: File) {
            // Skip hidden directories and common excludes
            if (dir.name.startsWith(".") ||
                dir.name in setOf("node_modules", "venv", ".venv", "__pycache__", "build", "dist")
            ) {
                return
            }

            // Check if this directory is a test directory
            if (dir.name.lowercase() in testDirNames) {
                testDirs.add(dir)
                return // Don't scan subdirectories of test dirs here
            }

            // Recursively scan subdirectories
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    scanForTestDirs(file)
                }
            }
        }

        scanForTestDirs(root)

        // If no test directories found, scan root for test files
        if (testDirs.isEmpty()) {
            testDirs.add(root)
        }

        return testDirs
    }

    /**
     * Scan a directory for test files
     */
    private fun scanDirectory(dir: File, basePath: String): TestFolder? {
        if (!dir.exists() || !dir.isDirectory) return null

        val relativePath = dir.absolutePath.removePrefix(basePath).trimStart(File.separatorChar)
        val folder = TestFolder(
            name = dir.name,
            path = dir.absolutePath
        )

        // Scan for test files
        dir.listFiles()?.forEach { file ->
            when {
                file.isDirectory -> {
                    // Skip common excludes
                    if (file.name !in setOf("__pycache__", ".pytest_cache", "node_modules", ".venv")) {
                        val subfolder = scanDirectory(file, basePath)
                        if (subfolder != null && subfolder.totalTests > 0) {
                            folder.subfolders.add(subfolder)
                        }
                    }
                }
                file.isFile && isTestFile(file) -> {
                    val testFile = parseTestFile(file)
                    if (testFile != null && testFile.testCases.isNotEmpty()) {
                        folder.files.add(testFile)
                    }
                }
            }
        }

        folder.updateStats()

        return if (folder.totalTests > 0) folder else null
    }

    /**
     * Check if file is a test file
     */
    private fun isTestFile(file: File): Boolean {
        val name = file.name
        return when {
            // Python test files
            name.startsWith("test_") && name.endsWith(".py") -> true
            name.endsWith("_test.py") -> true
            // Playwright Test (TypeScript/JavaScript)
            name.endsWith(".spec.ts") -> true
            name.endsWith(".spec.js") -> true
            name.endsWith(".test.ts") -> true
            name.endsWith(".test.js") -> true
            // Robot Framework
            name.endsWith(".robot") -> true
            else -> false
        }
    }

    /**
     * Parse a test file to extract test cases
     */
    private fun parseTestFile(file: File): TestFile? {
        return when {
            file.name.endsWith(".py") -> parsePytestFile(file)
            file.name.endsWith(".ts") || file.name.endsWith(".js") -> parsePlaywrightTestFile(file)
            file.name.endsWith(".robot") -> parseRobotFrameworkFile(file)
            else -> null
        }
    }

    /**
     * Parse Python test file (pytest/unittest)
     */
    private fun parsePytestFile(file: File): TestFile {
        val testFile = TestFile(
            name = file.name,
            path = file.absolutePath,
            framework = TestFramework.PYTEST
        )

        try {
            val content = file.readText()
            val lines = content.lines()

            // Parse test functions and classes
            var currentClass: String? = null
            var currentIndent = 0

            lines.forEachIndexed { index, line ->
                val trimmed = line.trim()

                // Detect test class
                if (trimmed.startsWith("class Test") && trimmed.contains(":")) {
                    currentClass = trimmed.substringAfter("class ").substringBefore("(").substringBefore(":")
                    currentIndent = line.takeWhile { it.isWhitespace() }.length
                }

                // Detect test function
                if (trimmed.startsWith("def test_") && trimmed.contains("(")) {
                    val functionName = trimmed.substringAfter("def ").substringBefore("(")
                    val testPath = if (currentClass != null) {
                        "${file.absolutePath}::$currentClass::$functionName"
                    } else {
                        "${file.absolutePath}::$functionName"
                    }

                    // Extract markers (@pytest.mark.smoke, etc.)
                    val markers = extractMarkers(lines, index)

                    // Detect parametrized tests
                    val parameters = extractParameters(lines, index)

                    val testCase = TestCase(
                        name = functionName,
                        path = testPath,
                        filePath = file.absolutePath,
                        markers = markers,
                        parameters = parameters
                    )

                    testFile.testCases.add(testCase)
                }

                // Reset class context if we're back at root level
                if (currentClass != null && line.isNotEmpty() && !line[0].isWhitespace()) {
                    currentClass = null
                }
            }

        } catch (e: Exception) {
            println("[TestDiscovery] Error parsing ${file.name}: ${e.message}")
        }

        testFile.updateStats()
        return testFile
    }

    /**
     * Extract pytest markers from decorators
     */
    private fun extractMarkers(lines: List<String>, testIndex: Int): List<String> {
        val markers = mutableListOf<String>()

        // Look backwards for decorators
        for (i in (testIndex - 1) downTo maxOf(0, testIndex - 10)) {
            val line = lines[i].trim()
            if (line.startsWith("@pytest.mark.")) {
                val marker = line.substringAfter("@pytest.mark.").substringBefore("(")
                markers.add(marker)
            } else if (!line.startsWith("@") && line.isNotEmpty()) {
                break // Stop at non-decorator line
            }
        }

        return markers
    }

    /**
     * Extract parametrize decorator values
     */
    private fun extractParameters(lines: List<String>, testIndex: Int): Map<String, String>? {
        for (i in (testIndex - 1) downTo maxOf(0, testIndex - 10)) {
            val line = lines[i].trim()
            if (line.startsWith("@pytest.mark.parametrize")) {
                // Simple extraction (can be improved)
                return mapOf("parametrized" to "true")
            } else if (!line.startsWith("@") && line.isNotEmpty()) {
                break
            }
        }
        return null
    }

    /**
     * Parse Playwright Test file (TypeScript/JavaScript)
     */
    private fun parsePlaywrightTestFile(file: File): TestFile {
        val testFile = TestFile(
            name = file.name,
            path = file.absolutePath,
            framework = TestFramework.PLAYWRIGHT_TEST
        )

        try {
            val content = file.readText()
            val lines = content.lines()

            // Parse test() and test.describe() blocks
            var currentDescribe: String? = null

            lines.forEach { line ->
                val trimmed = line.trim()

                // Detect describe block
                if (trimmed.startsWith("test.describe(")) {
                    currentDescribe = extractString(trimmed)
                }

                // Detect test function
                if (trimmed.startsWith("test(")) {
                    val testName = extractString(trimmed)
                    if (testName != null) {
                        val fullName = if (currentDescribe != null) {
                            "$currentDescribe > $testName"
                        } else {
                            testName
                        }

                        val testCase = TestCase(
                            name = fullName,
                            path = "${file.absolutePath}::$fullName",
                            filePath = file.absolutePath
                        )

                        testFile.testCases.add(testCase)
                    }
                }
            }

        } catch (e: Exception) {
            println("[TestDiscovery] Error parsing ${file.name}: ${e.message}")
        }

        testFile.updateStats()
        return testFile
    }

    /**
     * Parse Robot Framework file
     */
    private fun parseRobotFrameworkFile(file: File): TestFile {
        val testFile = TestFile(
            name = file.name,
            path = file.absolutePath,
            framework = TestFramework.ROBOT_FRAMEWORK
        )

        try {
            val content = file.readText()
            val lines = content.lines()

            var inTestCases = false

            lines.forEach { line ->
                val trimmed = line.trim()

                if (trimmed.equals("*** Test Cases ***", ignoreCase = true)) {
                    inTestCases = true
                    return@forEach
                }

                if (inTestCases && trimmed.startsWith("***")) {
                    inTestCases = false
                    return@forEach
                }

                if (inTestCases && trimmed.isNotEmpty() && !trimmed.startsWith("#") && !line[0].isWhitespace()) {
                    val testName = trimmed
                    val testCase = TestCase(
                        name = testName,
                        path = "${file.absolutePath}::$testName",
                        filePath = file.absolutePath
                    )
                    testFile.testCases.add(testCase)
                }
            }

        } catch (e: Exception) {
            println("[TestDiscovery] Error parsing ${file.name}: ${e.message}")
        }

        testFile.updateStats()
        return testFile
    }

    /**
     * Extract string from test definition
     * e.g., test('login works', ...) -> "login works"
     */
    private fun extractString(line: String): String? {
        return try {
            val start = line.indexOf("'") + 1
            val end = line.indexOf("'", start)
            if (start > 0 && end > start) {
                line.substring(start, end)
            } else {
                val dStart = line.indexOf("\"") + 1
                val dEnd = line.indexOf("\"", dStart)
                if (dStart > 0 && dEnd > dStart) {
                    line.substring(dStart, dEnd)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Refresh test suite (re-discover)
     */
    fun refresh(suite: TestSuite) {
        val newSuite = discoverTests(suite.path)
        suite.folders.clear()
        suite.folders.addAll(newSuite.folders)
        suite.updateStats()
    }
}
