package com.zaenrotech.zaector.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for Test Explorer tool window (left side)
 */
class PlaywrightTestExplorerFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val testExplorer = TestExplorerPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(testExplorer.panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
