package com.zaenrotech.zaector.core

import java.time.Instant

/**
 * Test status enumeration
 */
enum class TestStatus {
    NOT_RUN,      // Never executed
    RUNNING,      // Currently executing
    PASSED,       // Last run passed
    FAILED,       // Last run failed
    SKIPPED,      // Skipped (e.g., @skip decorator)
    FLAKY         // Inconsistent results (auto-detected)
}

/**
 * Base interface for all test tree nodes
 */
interface TestTreeNode {
    val name: String
    val path: String
    fun getDisplayName(): String = name
    fun getChildCount(): Int = 0
    fun getChildren(): List<TestTreeNode> = emptyList()
}

/**
 * Represents a test suite (collection of test folders/files)
 */
data class TestSuite(
    override val name: String,
    override val path: String,
    val folders: MutableList<TestFolder> = mutableListOf(),
    var totalTests: Int = 0,
    var passedTests: Int = 0,
    var failedTests: Int = 0,
    var skippedTests: Int = 0
) : TestTreeNode {

    override fun getChildCount(): Int = folders.size

    override fun getChildren(): List<TestTreeNode> = folders

    override fun getDisplayName(): String {
        return "$name ($totalTests tests)"
    }

    fun getPassRate(): Double {
        return if (totalTests > 0) (passedTests.toDouble() / totalTests * 100) else 0.0
    }

    fun updateStats() {
        totalTests = 0
        passedTests = 0
        failedTests = 0
        skippedTests = 0

        folders.forEach { folder ->
            folder.updateStats()
            totalTests += folder.totalTests
            passedTests += folder.passedTests
            failedTests += folder.failedTests
            skippedTests += folder.skippedTests
        }
    }
}

/**
 * Represents a folder containing test files
 */
data class TestFolder(
    override val name: String,
    override val path: String,
    val files: MutableList<TestFile> = mutableListOf(),
    val subfolders: MutableList<TestFolder> = mutableListOf(),
    var totalTests: Int = 0,
    var passedTests: Int = 0,
    var failedTests: Int = 0,
    var skippedTests: Int = 0
) : TestTreeNode {

    override fun getChildCount(): Int = subfolders.size + files.size

    override fun getChildren(): List<TestTreeNode> = subfolders + files

    override fun getDisplayName(): String {
        return "$name ($totalTests)"
    }

    fun updateStats() {
        totalTests = 0
        passedTests = 0
        failedTests = 0
        skippedTests = 0

        subfolders.forEach { subfolder ->
            subfolder.updateStats()
            totalTests += subfolder.totalTests
            passedTests += subfolder.passedTests
            failedTests += subfolder.failedTests
            skippedTests += subfolder.skippedTests
        }

        files.forEach { file ->
            file.updateStats()
            totalTests += file.totalTests
            passedTests += file.passedTests
            failedTests += file.failedTests
            skippedTests += file.skippedTests
        }
    }
}

/**
 * Represents a test file (e.g., test_login.py)
 */
data class TestFile(
    override val name: String,
    override val path: String,
    val testCases: MutableList<TestCase> = mutableListOf(),
    var framework: TestFramework = TestFramework.PYTEST,
    var totalTests: Int = 0,
    var passedTests: Int = 0,
    var failedTests: Int = 0,
    var skippedTests: Int = 0
) : TestTreeNode {

    override fun getChildCount(): Int = testCases.size

    override fun getChildren(): List<TestTreeNode> = testCases

    override fun getDisplayName(): String {
        val statusEmoji = when {
            failedTests > 0 -> "✗"
            passedTests == totalTests && totalTests > 0 -> "✓"
            else -> ""
        }
        return "$statusEmoji $name ($totalTests)"
    }

    fun updateStats() {
        totalTests = testCases.size
        passedTests = testCases.count { it.status == TestStatus.PASSED }
        failedTests = testCases.count { it.status == TestStatus.FAILED }
        skippedTests = testCases.count { it.status == TestStatus.SKIPPED }
    }
}

/**
 * Represents a single test case
 */
data class TestCase(
    override val name: String,
    override val path: String,          // Full path: file.py::test_name
    val filePath: String,                // Just the file path
    var status: TestStatus = TestStatus.NOT_RUN,
    var duration: Long = 0,              // in milliseconds
    var lastRun: Instant? = null,
    var errorMessage: String? = null,
    var stackTrace: String? = null,
    var screenshot: String? = null,
    var video: String? = null,
    var trace: String? = null,
    var markers: List<String> = emptyList(),        // @pytest.mark.smoke, etc.
    var parameters: Map<String, String>? = null,    // For parametrized tests
    val executionHistory: MutableList<TestExecution> = mutableListOf()
) : TestTreeNode {

    override fun getDisplayName(): String {
        val statusIcon = when (status) {
            TestStatus.PASSED -> "✓"
            TestStatus.FAILED -> "✗"
            TestStatus.SKIPPED -> "⏸"
            TestStatus.FLAKY -> "⚠️"
            TestStatus.RUNNING -> "▶"
            TestStatus.NOT_RUN -> "○"
        }

        val durationStr = if (duration > 0) " (${duration / 1000.0}s)" else ""
        return "$statusIcon $name$durationStr"
    }

    /**
     * Calculate flakiness score (0-100%)
     * Based on last 50 executions
     */
    fun getFlakinessScore(): Double {
        if (executionHistory.size < 5) return 0.0

        val recentRuns = executionHistory.takeLast(50)
        val failures = recentRuns.count { it.status == TestStatus.FAILED }

        return (failures.toDouble() / recentRuns.size) * 100
    }

    /**
     * Check if test is flaky (inconsistent results)
     */
    fun isFlaky(): Boolean {
        val score = getFlakinessScore()
        return score in 5.0..50.0  // Flaky if 5-50% failure rate
    }

    /**
     * Get average duration over last N runs
     */
    fun getAverageDuration(runs: Int = 10): Long {
        if (executionHistory.isEmpty()) return 0

        val recentRuns = executionHistory.takeLast(runs)
        return recentRuns.map { it.duration }.average().toLong()
    }
}

/**
 * Represents a single test execution
 */
data class TestExecution(
    val timestamp: Instant,
    val status: TestStatus,
    val duration: Long,
    val errorMessage: String? = null,
    val browser: String? = null
)

/**
 * Test framework enumeration
 */
enum class TestFramework {
    PYTEST,
    PLAYWRIGHT_TEST,
    UNITTEST,
    ROBOT_FRAMEWORK
}

/**
 * Test suite configuration
 */
data class TestSuiteConfig(
    val name: String,
    val pattern: String,                    // e.g., "tests/**/*_test.py"
    val exclude: List<String> = emptyList(),
    val browsers: List<String> = listOf("chromium"),
    val tags: List<String> = emptyList(),   // e.g., ["@smoke", "@regression"]
    val parallel: Int = 1,
    val timeout: Long = 30000,
    val retries: Int = 0
)

/**
 * Statistics for test suite
 */
data class TestStatistics(
    val totalTests: Int,
    val passed: Int,
    val failed: Int,
    val skipped: Int,
    val flaky: Int,
    val totalDuration: Long,
    val averageDuration: Long,
    val passRate: Double,
    val flakinessRate: Double
) {
    companion object {
        fun fromSuite(suite: TestSuite): TestStatistics {
            suite.updateStats()

            // Collect all test cases
            val allTests = mutableListOf<TestCase>()
            fun collectTests(node: TestTreeNode) {
                when (node) {
                    is TestCase -> allTests.add(node)
                    is TestFolder -> node.getChildren().forEach { collectTests(it) }
                    is TestFile -> node.testCases.forEach { allTests.add(it) }
                    is TestSuite -> node.folders.forEach { collectTests(it) }
                }
            }
            collectTests(suite)

            val flaky = allTests.count { it.isFlaky() }
            val totalDuration = allTests.sumOf { it.duration }
            val avgDuration = if (allTests.isNotEmpty()) totalDuration / allTests.size else 0

            return TestStatistics(
                totalTests = suite.totalTests,
                passed = suite.passedTests,
                failed = suite.failedTests,
                skipped = suite.skippedTests,
                flaky = flaky,
                totalDuration = totalDuration,
                averageDuration = avgDuration,
                passRate = suite.getPassRate(),
                flakinessRate = if (allTests.isNotEmpty()) (flaky.toDouble() / allTests.size * 100) else 0.0
            )
        }
    }
}
