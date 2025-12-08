package com.zaenrotech.zaector.core

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel
import java.awt.Color
import java.awt.Component

/**
 * Test Explorer Panel - Main UI for test management
 * Like Squish IDE's test suite view
 */
class TestExplorerPanel(private val project: Project) {

    val panel = JPanel(BorderLayout())
    private val tree: Tree
    private val rootNode: DefaultMutableTreeNode
    private val treeModel: DefaultTreeModel

    private val discoveryEngine = TestDiscoveryEngine(project)
    private val suiteConfigManager = TestSuiteConfigManager(project)
    private val executionEngine = TestExecutionEngine(project)
    private var currentSuite: TestSuite? = null
    private var allTests: TestSuite? = null  // Full test suite (unfiltered)

    // UI Components
    private val suiteComboBox = JComboBox<String>()
    private val refreshButton = JButton(AllIcons.Actions.Refresh)
    private val runAllButton = JButton(AllIcons.Actions.Execute)
    private val runSelectedButton = JButton(AllIcons.Actions.RunToCursor)
    private val stopButton = JButton(AllIcons.Actions.Suspend)
    private val createConfigButton = JButton(AllIcons.Actions.Edit)
    private val browseConfigButton = JButton(AllIcons.General.OpenDisk)
    private val statsLabel = JLabel("No tests discovered")

    // Details panel
    private val detailsPanel = JPanel(BorderLayout())
    private val detailsTextArea = JTextArea()

    init {
        // Initialize tree
        rootNode = DefaultMutableTreeNode("Tests")
        treeModel = DefaultTreeModel(rootNode)
        tree = Tree(treeModel)
        tree.cellRenderer = TestTreeCellRenderer()
        tree.isRootVisible = true
        tree.showsRootHandles = true

        // Setup UI
        setupToolbar()
        setupTreeListeners()
        setupDetailsPanel()

        // Layout - Vertical split (tree on top, details on bottom)
        val splitPane = JSplitPane(JSplitPane.VERTICAL_SPLIT)
        splitPane.topComponent = JBScrollPane(tree)
        splitPane.bottomComponent = detailsPanel
        splitPane.dividerLocation = 600  // Give tree much more space
        splitPane.resizeWeight = 0.85  // Tree gets 85% when resizing

        panel.add(splitPane, BorderLayout.CENTER)

        // Initial discovery
        discoverTests()
    }

    /**
     * Setup toolbar with actions
     */
    private fun setupToolbar() {
        // Row 1: Suite selector + stats
        val row1 = JPanel(FlowLayout(FlowLayout.LEFT, 5, 1))
        row1.add(suiteComboBox)
        row1.add(statsLabel)

        // Row 2: First 3 buttons (Refresh, Run All, Run Selected)
        val row2 = JPanel(FlowLayout(FlowLayout.LEFT, 2, 1))
        row2.add(refreshButton)
        row2.add(runAllButton)
        row2.add(runSelectedButton)

        // Row 3: Last 3 buttons (Stop, Create Config, Browse)
        val row3 = JPanel(FlowLayout(FlowLayout.LEFT, 2, 1))
        row3.add(stopButton)
        row3.add(createConfigButton)
        row3.add(browseConfigButton)

        // Combine rows
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.Y_AXIS)
        toolbar.add(row1)
        toolbar.add(row2)
        toolbar.add(row3)

        // Suite selector
        loadSuiteConfigs()
        suiteComboBox.addActionListener { onSuiteSelected() }

        // Button states
        stopButton.isEnabled = false

        // Button actions and tooltips
        refreshButton.addActionListener { discoverTests() }
        refreshButton.toolTipText = "Refresh test discovery"

        runAllButton.addActionListener { runAllTests() }
        runAllButton.toolTipText = "Run all tests"

        runSelectedButton.addActionListener { runSelectedTest() }
        runSelectedButton.toolTipText = "Run selected test or file"

        stopButton.addActionListener { stopExecution() }
        stopButton.toolTipText = "Stop test execution"

        createConfigButton.addActionListener { createConfigFile() }
        createConfigButton.toolTipText = "Create .playwright-suites.json config file"

        browseConfigButton.addActionListener { browseConfigDirectory() }
        browseConfigButton.toolTipText = "Select config file directory"

        // Add toolbar to panel
        panel.add(toolbar, BorderLayout.NORTH)
    }

    /**
     * Load suite configurations into combo box
     */
    private fun loadSuiteConfigs() {
        val configs = suiteConfigManager.getSuiteConfigs()
        suiteComboBox.removeAllItems()
        configs.forEach { config ->
            suiteComboBox.addItem(config.name)
        }
    }

    /**
     * Handle suite selection
     */
    private fun onSuiteSelected() {
        val selectedSuite = suiteComboBox.selectedItem as? String ?: return
        println("[TestExplorer] Suite selected: $selectedSuite")

        if (allTests == null) {
            // No tests loaded yet, discover first
            discoverTests()
        } else {
            // Filter existing tests by selected suite
            applySuiteFilter()
        }
    }

    /**
     * Apply suite filter to current tests
     */
    private fun applySuiteFilter() {
        val selectedSuiteName = suiteComboBox.selectedItem as? String ?: return
        val configs = suiteConfigManager.getSuiteConfigs()
        val config = configs.find { it.name == selectedSuiteName } ?: return

        if (allTests == null) return

        println("[TestExplorer] Applying filter: $selectedSuiteName")
        statsLabel.text = "Filtering tests..."

        Thread {
            val filtered = suiteConfigManager.filterTestsBySuite(allTests!!, config)
            currentSuite = filtered

            ApplicationManager.getApplication().invokeLater {
                updateTreeFromSuite(filtered)
                updateStats(filtered)
                println("[TestExplorer] Filter applied: ${filtered.totalTests} tests")
            }
        }.start()
    }

    /**
     * Create config file
     */
    private fun createConfigFile() {
        val result = JOptionPane.showConfirmDialog(
            panel,
            "Create .playwright-suites.json config file in project root?\n\n" +
                    "This will create an example configuration with:\n" +
                    "- Smoke Tests\n" +
                    "- E2E Tests\n" +
                    "- Regression\n\n" +
                    "You can edit this file to customize your test suites.",
            "Create Config File",
            JOptionPane.YES_NO_OPTION
        )

        if (result == JOptionPane.YES_OPTION) {
            suiteConfigManager.createExampleConfigFile()

            // Refresh suite list
            loadSuiteConfigs()

            JOptionPane.showMessageDialog(
                panel,
                "Config file created!\n" +
                        "Location: ${project.basePath}/.playwright-suites.json\n\n" +
                        "Edit this file to customize your test suites.",
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    /**
     * Browse for config directory
     */
    private fun browseConfigDirectory() {
        val descriptor = FileChooserDescriptorFactory.createSingleFolderDescriptor().apply {
            title = "Select Suite Configuration Directory"
            description = "Choose directory for .playwright-suites.json file"
        }

        FileChooser.chooseFile(descriptor, project, null) { selectedDir ->
            // Save to settings
            val settings = PlaywrightSettings.instance
            settings.suiteConfigPath = selectedDir.path

            // Refresh suite configuration
            suiteConfigManager.refresh()
            loadSuiteConfigs()

            JOptionPane.showMessageDialog(
                panel,
                "Config directory updated!\n\n" +
                        "Location: ${selectedDir.path}\n\n" +
                        "The plugin will now look for .playwright-suites.json in this directory.",
                "Config Directory Updated",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    /**
     * Setup tree selection listeners
     */
    private fun setupTreeListeners() {
        tree.addTreeSelectionListener {
            val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
            val userObject = node?.userObject

            when (userObject) {
                is TestCase -> showTestCaseDetails(userObject)
                is TestFile -> showTestFileDetails(userObject)
                is TestFolder -> showTestFolderDetails(userObject)
                is TestSuite -> showTestSuiteDetails(userObject)
                else -> clearDetails()
            }
        }

        // Double-click to run test
        tree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    runSelectedTest()
                }
            }
        })

        // Right-click context menu
        tree.componentPopupMenu = createContextMenu()
    }

    /**
     * Create right-click context menu
     */
    private fun createContextMenu(): JPopupMenu {
        val menu = JPopupMenu()

        val runAction = JMenuItem("Run", AllIcons.Actions.Execute)
        runAction.addActionListener { runSelectedTest() }

        val debugAction = JMenuItem("Debug", AllIcons.Actions.StartDebugger)
        debugAction.addActionListener { debugSelectedTest() }

        val openFileAction = JMenuItem("Open in Editor", AllIcons.Actions.EditSource)
        openFileAction.addActionListener { openSelectedTestInEditor() }

        val copyPathAction = JMenuItem("Copy Path", AllIcons.Actions.Copy)
        copyPathAction.addActionListener { copySelectedTestPath() }

        menu.add(runAction)
        menu.add(debugAction)
        menu.addSeparator()
        menu.add(openFileAction)
        menu.add(copyPathAction)

        return menu
    }

    /**
     * Setup details panel
     */
    private fun setupDetailsPanel() {
        detailsPanel.border = BorderFactory.createTitledBorder("Test Details")

        detailsTextArea.isEditable = false
        detailsTextArea.lineWrap = true
        detailsTextArea.wrapStyleWord = true
        detailsTextArea.text = "Select a test to view details"

        detailsPanel.add(JBScrollPane(detailsTextArea), BorderLayout.CENTER)
    }

    /**
     * Discover tests in project
     */
    private fun discoverTests() {
        println("[TestExplorer] Starting test discovery...")
        statsLabel.text = "Discovering tests..."

        Thread {
            val suite = discoveryEngine.discoverTests()
            allTests = suite  // Store full suite

            // Apply suite filter if one is selected
            val selectedSuiteName = suiteComboBox.selectedItem as? String
            if (selectedSuiteName != null && selectedSuiteName != "All Tests") {
                val configs = suiteConfigManager.getSuiteConfigs()
                val config = configs.find { it.name == selectedSuiteName }
                if (config != null) {
                    currentSuite = suiteConfigManager.filterTestsBySuite(suite, config)
                } else {
                    currentSuite = suite
                }
            } else {
                currentSuite = suite
            }

            ApplicationManager.getApplication().invokeLater {
                updateTreeFromSuite(currentSuite!!)
                updateStats(currentSuite!!)
                println("[TestExplorer] Discovery complete: ${currentSuite!!.totalTests} tests")
            }
        }.start()
    }

    /**
     * Update tree from test suite
     */
    private fun updateTreeFromSuite(suite: TestSuite) {
        rootNode.removeAllChildren()
        rootNode.userObject = suite

        // Add folders
        suite.folders.forEach { folder ->
            addFolderNode(rootNode, folder)
        }

        treeModel.reload()
        expandTopLevel()
    }

    /**
     * Add folder node to tree
     */
    private fun addFolderNode(parent: DefaultMutableTreeNode, folder: TestFolder) {
        val folderNode = DefaultMutableTreeNode(folder)

        // Add subfolders
        folder.subfolders.forEach { subfolder ->
            addFolderNode(folderNode, subfolder)
        }

        // Add files
        folder.files.forEach { file ->
            addFileNode(folderNode, file)
        }

        parent.add(folderNode)
    }

    /**
     * Add file node to tree
     */
    private fun addFileNode(parent: DefaultMutableTreeNode, file: TestFile) {
        val fileNode = DefaultMutableTreeNode(file)

        // Add test cases
        file.testCases.forEach { testCase ->
            val testNode = DefaultMutableTreeNode(testCase)
            fileNode.add(testNode)
        }

        parent.add(fileNode)
    }

    /**
     * Expand top-level nodes
     */
    private fun expandTopLevel() {
        for (i in 0 until tree.getRowCount()) {
            tree.expandRow(i)
            if (i > 10) break // Don't expand too many
        }
    }

    /**
     * Update statistics label
     */
    private fun updateStats(suite: TestSuite) {
        val stats = TestStatistics.fromSuite(suite)
        statsLabel.text = "Tests: ${stats.totalTests} | " +
                          "✓ ${stats.passed} | " +
                          "✗ ${stats.failed} | " +
                          "⏸ ${stats.skipped} | " +
                          "⚠️ ${stats.flaky}"
    }

    /**
     * Show test case details
     */
    private fun showTestCaseDetails(testCase: TestCase) {
        val details = buildString {
            appendLine("Test Case: ${testCase.name}")
            appendLine("Path: ${testCase.path}")
            appendLine("Status: ${testCase.status}")

            if (testCase.duration > 0) {
                appendLine("Duration: ${testCase.duration / 1000.0}s")
            }

            if (testCase.lastRun != null) {
                appendLine("Last Run: ${testCase.lastRun}")
            }

            if (testCase.markers.isNotEmpty()) {
                appendLine("Markers: ${testCase.markers.joinToString(", ")}")
            }

            val flakiness = testCase.getFlakinessScore()
            if (flakiness > 0) {
                appendLine("Flakiness: ${String.format("%.1f", flakiness)}%")
            }

            if (testCase.errorMessage != null) {
                appendLine("\nError Message:")
                appendLine(testCase.errorMessage)
            }

            if (testCase.stackTrace != null) {
                appendLine("\nStack Trace:")
                appendLine(testCase.stackTrace)
            }
        }

        detailsTextArea.text = details
    }

    /**
     * Show test file details
     */
    private fun showTestFileDetails(file: TestFile) {
        val details = buildString {
            appendLine("Test File: ${file.name}")
            appendLine("Path: ${file.path}")
            appendLine("Framework: ${file.framework}")
            appendLine("Total Tests: ${file.totalTests}")
            appendLine("Passed: ${file.passedTests}")
            appendLine("Failed: ${file.failedTests}")
            appendLine("Skipped: ${file.skippedTests}")
        }

        detailsTextArea.text = details
    }

    /**
     * Show test folder details
     */
    private fun showTestFolderDetails(folder: TestFolder) {
        val details = buildString {
            appendLine("Test Folder: ${folder.name}")
            appendLine("Path: ${folder.path}")
            appendLine("Total Tests: ${folder.totalTests}")
            appendLine("Passed: ${folder.passedTests}")
            appendLine("Failed: ${folder.failedTests}")
            appendLine("Skipped: ${folder.skippedTests}")
            appendLine("Subfolders: ${folder.subfolders.size}")
            appendLine("Files: ${folder.files.size}")
        }

        detailsTextArea.text = details
    }

    /**
     * Show test suite details
     */
    private fun showTestSuiteDetails(suite: TestSuite) {
        val stats = TestStatistics.fromSuite(suite)

        val details = buildString {
            appendLine("Test Suite: ${suite.name}")
            appendLine("Path: ${suite.path}")
            appendLine()
            appendLine("=== Statistics ===")
            appendLine("Total Tests: ${stats.totalTests}")
            appendLine("Passed: ${stats.passed} (${String.format("%.1f", stats.passRate)}%)")
            appendLine("Failed: ${stats.failed}")
            appendLine("Skipped: ${stats.skipped}")
            appendLine("Flaky: ${stats.flaky}")
            appendLine()
            appendLine("Total Duration: ${stats.totalDuration / 1000.0}s")
            appendLine("Average Duration: ${stats.averageDuration / 1000.0}s")
        }

        detailsTextArea.text = details
    }

    /**
     * Clear details panel
     */
    private fun clearDetails() {
        detailsTextArea.text = "Select a test to view details"
    }

    /**
     * Run all tests
     */
    private fun runAllTests() {
        println("[TestExplorer] Running all tests...")

        if (currentSuite == null) {
            JOptionPane.showMessageDialog(
                panel,
                "No tests discovered. Click Refresh to discover tests.",
                "No Tests",
                JOptionPane.WARNING_MESSAGE
            )
            return
        }

        // Clear output
        detailsTextArea.text = "Running all tests...\n\n"

        // Enable stop button, disable run buttons
        runAllButton.isEnabled = false
        runSelectedButton.isEnabled = false
        stopButton.isEnabled = true

        executionEngine.runTestSuite(currentSuite!!, object : ExecutionCallback {
            override fun onStart(executionId: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("Execution started (ID: $executionId)\n")
                }
            }

            override fun onOutput(executionId: String, output: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append(output)
                    detailsTextArea.caretPosition = detailsTextArea.document.length
                }
            }

            override fun onTestPassed(executionId: String, testName: String) {
                ApplicationManager.getApplication().invokeLater {
                    updateTestStatus(testName, TestStatus.PASSED)
                }
            }

            override fun onTestFailed(executionId: String, testName: String, error: String) {
                ApplicationManager.getApplication().invokeLater {
                    updateTestStatus(testName, TestStatus.FAILED)
                }
            }

            override fun onTestSkipped(executionId: String, testName: String) {
                ApplicationManager.getApplication().invokeLater {
                    updateTestStatus(testName, TestStatus.SKIPPED)
                }
            }

            override fun onSuccess(executionId: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("\n✓ All tests completed successfully!\n")
                    resetButtonStates()
                }
            }

            override fun onError(executionId: String, error: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("\n✗ Error: $error\n")
                    resetButtonStates()
                }
            }

            override fun onStopped(executionId: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("\n⊗ Execution stopped by user\n")
                    resetButtonStates()
                }
            }

            override fun onComplete(executionId: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("\nExecution complete.\n")
                    allTests?.let { updateStats(it) }
                    resetButtonStates()
                }
            }
        })
    }

    /**
     * Run selected test
     */
    private fun runSelectedTest() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val userObject = node?.userObject

        // Clear output
        detailsTextArea.text = ""

        // Enable stop button, disable run buttons
        runAllButton.isEnabled = false
        runSelectedButton.isEnabled = false
        stopButton.isEnabled = true

        val callback = object : ExecutionCallback {
            override fun onStart(executionId: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("Execution started (ID: $executionId)\n\n")
                }
            }

            override fun onOutput(executionId: String, output: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append(output)
                    detailsTextArea.caretPosition = detailsTextArea.document.length
                }
            }

            override fun onTestPassed(executionId: String, testName: String) {
                ApplicationManager.getApplication().invokeLater {
                    updateTestStatus(testName, TestStatus.PASSED)
                }
            }

            override fun onTestFailed(executionId: String, testName: String, error: String) {
                ApplicationManager.getApplication().invokeLater {
                    updateTestStatus(testName, TestStatus.FAILED)
                }
            }

            override fun onTestSkipped(executionId: String, testName: String) {
                ApplicationManager.getApplication().invokeLater {
                    updateTestStatus(testName, TestStatus.SKIPPED)
                }
            }

            override fun onSuccess(executionId: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("\n✓ Tests completed successfully!\n")
                    resetButtonStates()
                }
            }

            override fun onError(executionId: String, error: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("\n✗ Error: $error\n")
                    resetButtonStates()
                }
            }

            override fun onStopped(executionId: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("\n⊗ Execution stopped by user\n")
                    resetButtonStates()
                }
            }

            override fun onComplete(executionId: String) {
                ApplicationManager.getApplication().invokeLater {
                    detailsTextArea.append("\nExecution complete.\n")
                    allTests?.let { updateStats(it) }
                    resetButtonStates()
                }
            }
        }

        when (userObject) {
            is TestCase -> {
                // Get framework from parent TestFile node
                val parentNode = node.parent as? DefaultMutableTreeNode
                val parentFile = parentNode?.userObject as? TestFile

                if (parentFile != null) {
                    println("[TestExplorer] Running test: ${userObject.name}")
                    detailsTextArea.text = "Running test: ${userObject.name}\n\n"
                    executionEngine.runTest(userObject, parentFile.framework, callback)
                } else {
                    resetButtonStates()
                    JOptionPane.showMessageDialog(
                        panel,
                        "Could not determine test framework",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            }
            is TestFile -> {
                println("[TestExplorer] Running file: ${userObject.name}")
                detailsTextArea.text = "Running file: ${userObject.name}\n\n"
                executionEngine.runTestFile(userObject, callback)
            }
            else -> {
                resetButtonStates()
                JOptionPane.showMessageDialog(
                    panel,
                    "Please select a test or test file",
                    "No Selection",
                    JOptionPane.WARNING_MESSAGE
                )
            }
        }
    }

    /**
     * Debug selected test
     */
    private fun debugSelectedTest() {
        println("[TestExplorer] Debug not yet implemented")
    }

    /**
     * Open selected test in editor
     */
    private fun openSelectedTestInEditor() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val userObject = node?.userObject

        val filePath = when (userObject) {
            is TestCase -> userObject.path
            is TestFile -> userObject.path
            is TestFolder -> userObject.path
            else -> null
        }

        if (filePath != null) {
            val file = java.io.File(filePath)
            if (file.exists()) {
                val virtualFile = com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByIoFile(file)
                if (virtualFile != null) {
                    com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(virtualFile, true)
                } else {
                    JOptionPane.showMessageDialog(
                        panel,
                        "Could not find file in project: $filePath",
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                    )
                }
            } else {
                JOptionPane.showMessageDialog(
                    panel,
                    "File does not exist: $filePath",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                )
            }
        }
    }

    /**
     * Copy selected test path
     */
    private fun copySelectedTestPath() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode
        val userObject = node?.userObject

        val path = when (userObject) {
            is TestCase -> userObject.path
            is TestFile -> userObject.path
            is TestFolder -> userObject.path
            else -> null
        }

        if (path != null) {
            val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
            clipboard.setContents(java.awt.datatransfer.StringSelection(path), null)

            JOptionPane.showMessageDialog(
                panel,
                "Path copied to clipboard!",
                "Success",
                JOptionPane.INFORMATION_MESSAGE
            )
        }
    }

    /**
     * Stop test execution
     */
    private fun stopExecution() {
        println("[TestExplorer] Stopping execution...")
        executionEngine.stopExecution()
    }

    /**
     * Reset button states after execution
     */
    private fun resetButtonStates() {
        runAllButton.isEnabled = true
        runSelectedButton.isEnabled = true
        stopButton.isEnabled = false
    }

    /**
     * Update test status in tree
     */
    private fun updateTestStatus(testName: String, status: TestStatus) {
        // Find the test case node in the tree and update its status
        findAndUpdateTestNode(rootNode, testName, status)
        treeModel.reload()
    }

    /**
     * Recursively find and update test node
     */
    private fun findAndUpdateTestNode(node: DefaultMutableTreeNode, testName: String, status: TestStatus): Boolean {
        val userObject = node.userObject

        if (userObject is TestCase && userObject.name == testName) {
            userObject.status = status
            return true
        }

        // Search children
        for (i in 0 until node.childCount) {
            val child = node.getChildAt(i) as DefaultMutableTreeNode
            if (findAndUpdateTestNode(child, testName, status)) {
                return true
            }
        }

        return false
    }
}

/**
 * Custom tree cell renderer with status icons
 */
class TestTreeCellRenderer : DefaultTreeCellRenderer() {

    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        val node = value as? DefaultMutableTreeNode
        val userObject = node?.userObject

        // Set icon and text based on node type
        when (userObject) {
            is TestCase -> {
                icon = getTestCaseIcon(userObject.status)
                text = userObject.getDisplayName()

                // Color based on status
                foreground = when (userObject.status) {
                    TestStatus.PASSED -> Color(0, 150, 0)
                    TestStatus.FAILED -> Color(200, 0, 0)
                    TestStatus.SKIPPED -> Color(128, 128, 128)
                    TestStatus.FLAKY -> Color(255, 140, 0)
                    TestStatus.RUNNING -> Color(0, 0, 200)
                    else -> Color.BLACK
                }
            }
            is TestFile -> {
                icon = AllIcons.FileTypes.Any_type
                text = userObject.getDisplayName()
            }
            is TestFolder -> {
                icon = if (expanded) AllIcons.Nodes.Folder else AllIcons.Nodes.Folder
                text = userObject.getDisplayName()
            }
            is TestSuite -> {
                icon = AllIcons.Nodes.Module
                text = userObject.getDisplayName()
            }
        }

        return this
    }

    private fun getTestCaseIcon(status: TestStatus): Icon {
        return when (status) {
            TestStatus.PASSED -> AllIcons.RunConfigurations.TestPassed
            TestStatus.FAILED -> AllIcons.RunConfigurations.TestFailed
            TestStatus.SKIPPED -> AllIcons.RunConfigurations.TestIgnored
            TestStatus.FLAKY -> AllIcons.RunConfigurations.TestError
            TestStatus.RUNNING -> AllIcons.Process.Step_1
            TestStatus.NOT_RUN -> AllIcons.RunConfigurations.TestNotRan
        }
    }
}
