package com.zaenrotech.zaector.core

/**
 * Code generation templates for different testing frameworks
 */
enum class TemplateFramework(val displayName: String) {
    PYTHON_SYNC("Python (Playwright Sync)"),
    PYTHON_PYTEST("Python (Pytest)"),
    TYPESCRIPT("TypeScript (Playwright)"),
    JAVASCRIPT("JavaScript (Playwright)"),
    ROBOT_FRAMEWORK("Robot Framework")
}

enum class QuoteStyle {
    SINGLE,
    DOUBLE
}

data class CodeFormatting(
    val indentSize: Int = 4,
    val useTabs: Boolean = false,
    val quoteStyle: QuoteStyle = QuoteStyle.DOUBLE
)

interface CodeTemplate {
    fun generateSkeleton(url: String, formatting: CodeFormatting): String
    fun generateAction(selector: String, actionType: String, formatting: CodeFormatting): String
    fun generateAssertion(selector: String, assertionType: String, value: String, formatting: CodeFormatting): String
}

/**
 * Python Playwright Sync Template
 */
class PythonSyncTemplate : CodeTemplate {
    override fun generateSkeleton(url: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        return """
from playwright.sync_api import sync_playwright, expect

def run(playwright):
    browser = playwright.chromium.launch(headless=False)
    context = browser.new_context()
    page = context.new_page()
    page.goto(${quote}${url}${quote})
    # TODO: Add actions here
    # page.pause()
    context.close()
    browser.close()

with sync_playwright() as playwright:
    run(playwright)
        """.trimIndent()
    }

    override fun generateAction(selector: String, actionType: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        return when (actionType) {
            "click" -> "page.locator(${quote}${selector}${quote}).click()"
            "fill" -> "page.locator(${quote}${selector}${quote}).fill(${quote}text${quote})"
            "type" -> "page.locator(${quote}${selector}${quote}).type(${quote}text${quote})"
            "press" -> "page.locator(${quote}${selector}${quote}).press(${quote}Enter${quote})"
            "check" -> "page.locator(${quote}${selector}${quote}).check()"
            "uncheck" -> "page.locator(${quote}${selector}${quote}).uncheck()"
            "select" -> "page.locator(${quote}${selector}${quote}).select_option(${quote}value${quote})"
            "hover" -> "page.locator(${quote}${selector}${quote}).hover()"
            else -> "page.locator(${quote}${selector}${quote}).click()"
        }
    }

    override fun generateAssertion(selector: String, assertionType: String, value: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        val locator = "page.locator(${quote}${selector}${quote})"
        return when (assertionType) {
            "text" -> "expect(${locator}).to_have_text(${quote}${value}${quote})"
            "value" -> "expect(${locator}).to_have_value(${quote}${value}${quote})"
            "visible" -> if (value == "true") "expect(${locator}).to_be_visible()" else "expect(${locator}).to_be_hidden()"
            "checked" -> if (value == "true") "expect(${locator}).to_be_checked()" else "expect(${locator}).not_to_be_checked()"
            "disabled" -> if (value == "true") "expect(${locator}).to_be_disabled()" else "expect(${locator}).to_be_enabled()"
            "class" -> "expect(${locator}).to_have_class(${quote}${value}${quote})"
            "attribute" -> "expect(${locator}).to_have_attribute(${quote}name${quote}, ${quote}${value}${quote})"
            "css" -> "expect(${locator}).to_have_css(${quote}property${quote}, ${quote}${value}${quote})"
            else -> "expect(${locator}).to_have_text(${quote}${value}${quote})"
        }
    }
}

/**
 * Python Pytest Template
 */
class PytestTemplate : CodeTemplate {
    override fun generateSkeleton(url: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        return """
import pytest
from playwright.sync_api import Page, expect

@pytest.fixture(scope=${quote}function${quote}, autouse=True)
def before_each_after_each(page: Page):
    # Before test
    page.goto(${quote}${url}${quote})
    yield
    # After test - cleanup if needed

def test_example(page: Page):
    # TODO: Add test steps here
    pass
        """.trimIndent()
    }

    override fun generateAction(selector: String, actionType: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        return when (actionType) {
            "click" -> "page.locator(${quote}${selector}${quote}).click()"
            "fill" -> "page.locator(${quote}${selector}${quote}).fill(${quote}text${quote})"
            "type" -> "page.locator(${quote}${selector}${quote}).type(${quote}text${quote})"
            "press" -> "page.locator(${quote}${selector}${quote}).press(${quote}Enter${quote})"
            "check" -> "page.locator(${quote}${selector}${quote}).check()"
            "uncheck" -> "page.locator(${quote}${selector}${quote}).uncheck()"
            "select" -> "page.locator(${quote}${selector}${quote}).select_option(${quote}value${quote})"
            "hover" -> "page.locator(${quote}${selector}${quote}).hover()"
            else -> "page.locator(${quote}${selector}${quote}).click()"
        }
    }

    override fun generateAssertion(selector: String, assertionType: String, value: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        val locator = "page.locator(${quote}${selector}${quote})"
        return when (assertionType) {
            "text" -> "expect(${locator}).to_have_text(${quote}${value}${quote})"
            "value" -> "expect(${locator}).to_have_value(${quote}${value}${quote})"
            "visible" -> if (value == "true") "expect(${locator}).to_be_visible()" else "expect(${locator}).to_be_hidden()"
            "checked" -> if (value == "true") "expect(${locator}).to_be_checked()" else "expect(${locator}).not_to_be_checked()"
            "disabled" -> if (value == "true") "expect(${locator}).to_be_disabled()" else "expect(${locator}).to_be_enabled()"
            "class" -> "expect(${locator}).to_have_class(${quote}${value}${quote})"
            "attribute" -> "expect(${locator}).to_have_attribute(${quote}name${quote}, ${quote}${value}${quote})"
            "css" -> "expect(${locator}).to_have_css(${quote}property${quote}, ${quote}${value}${quote})"
            else -> "expect(${locator}).to_have_text(${quote}${value}${quote})"
        }
    }
}

/**
 * TypeScript Playwright Template
 */
class TypeScriptTemplate : CodeTemplate {
    override fun generateSkeleton(url: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        return """
import { test, expect } from ${quote}@playwright/test${quote};

test(${quote}example test${quote}, async ({ page }) => {
    await page.goto(${quote}${url}${quote});
    // TODO: Add test steps here
});
        """.trimIndent()
    }

    override fun generateAction(selector: String, actionType: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        return when (actionType) {
            "click" -> "await page.locator(${quote}${selector}${quote}).click();"
            "fill" -> "await page.locator(${quote}${selector}${quote}).fill(${quote}text${quote});"
            "type" -> "await page.locator(${quote}${selector}${quote}).type(${quote}text${quote});"
            "press" -> "await page.locator(${quote}${selector}${quote}).press(${quote}Enter${quote});"
            "check" -> "await page.locator(${quote}${selector}${quote}).check();"
            "uncheck" -> "await page.locator(${quote}${selector}${quote}).uncheck();"
            "select" -> "await page.locator(${quote}${selector}${quote}).selectOption(${quote}value${quote});"
            "hover" -> "await page.locator(${quote}${selector}${quote}).hover();"
            else -> "await page.locator(${quote}${selector}${quote}).click();"
        }
    }

    override fun generateAssertion(selector: String, assertionType: String, value: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        val locator = "page.locator(${quote}${selector}${quote})"
        return when (assertionType) {
            "text" -> "await expect(${locator}).toHaveText(${quote}${value}${quote});"
            "value" -> "await expect(${locator}).toHaveValue(${quote}${value}${quote});"
            "visible" -> if (value == "true") "await expect(${locator}).toBeVisible();" else "await expect(${locator}).toBeHidden();"
            "checked" -> if (value == "true") "await expect(${locator}).toBeChecked();" else "await expect(${locator}).not.toBeChecked();"
            "disabled" -> if (value == "true") "await expect(${locator}).toBeDisabled();" else "await expect(${locator}).toBeEnabled();"
            "class" -> "await expect(${locator}).toHaveClass(${quote}${value}${quote});"
            "attribute" -> "await expect(${locator}).toHaveAttribute(${quote}name${quote}, ${quote}${value}${quote});"
            "css" -> "await expect(${locator}).toHaveCSS(${quote}property${quote}, ${quote}${value}${quote});"
            else -> "await expect(${locator}).toHaveText(${quote}${value}${quote});"
        }
    }
}

/**
 * JavaScript Playwright Template
 */
class JavaScriptTemplate : CodeTemplate {
    override fun generateSkeleton(url: String, formatting: CodeFormatting): String {
        val quote = if (formatting.quoteStyle == QuoteStyle.SINGLE) "'" else "\""
        return """
const { test, expect } = require(${quote}@playwright/test${quote});

test(${quote}example test${quote}, async ({ page }) => {
    await page.goto(${quote}${url}${quote});
    // TODO: Add test steps here
});
        """.trimIndent()
    }

    override fun generateAction(selector: String, actionType: String, formatting: CodeFormatting): String {
        return TypeScriptTemplate().generateAction(selector, actionType, formatting)
    }

    override fun generateAssertion(selector: String, assertionType: String, value: String, formatting: CodeFormatting): String {
        return TypeScriptTemplate().generateAssertion(selector, assertionType, value, formatting)
    }
}

/**
 * Robot Framework Template
 */
class RobotFrameworkTemplate : CodeTemplate {
    override fun generateSkeleton(url: String, formatting: CodeFormatting): String {
        val indent = if (formatting.useTabs) "\t" else " ".repeat(formatting.indentSize)
        return """
*** Settings ***
Library${indent}Browser

*** Test Cases ***
Example Test
${indent}New Browser${indent}chromium${indent}headless=False
${indent}New Page${indent}${url}
${indent}# TODO: Add test steps here
${indent}Close Browser
        """.trimIndent()
    }

    override fun generateAction(selector: String, actionType: String, formatting: CodeFormatting): String {
        val indent = if (formatting.useTabs) "\t" else " ".repeat(formatting.indentSize)
        return when (actionType) {
            "click" -> "Click${indent}${selector}"
            "fill" -> "Fill Text${indent}${selector}${indent}text"
            "type" -> "Type Text${indent}${selector}${indent}text"
            "press" -> "Press Keys${indent}${selector}${indent}Enter"
            "check" -> "Check Checkbox${indent}${selector}"
            "uncheck" -> "Uncheck Checkbox${indent}${selector}"
            "select" -> "Select Options By${indent}${selector}${indent}value${indent}value"
            "hover" -> "Hover${indent}${selector}"
            else -> "Click${indent}${selector}"
        }
    }

    override fun generateAssertion(selector: String, assertionType: String, value: String, formatting: CodeFormatting): String {
        val indent = if (formatting.useTabs) "\t" else " ".repeat(formatting.indentSize)
        return when (assertionType) {
            "text" -> "Get Text${indent}${selector}${indent}==${indent}${value}"
            "value" -> "Get Property${indent}${selector}${indent}value${indent}==${indent}${value}"
            "visible" -> if (value == "true") "Get Element States${indent}${selector}${indent}contains${indent}visible" else "Get Element States${indent}${selector}${indent}not contains${indent}visible"
            "checked" -> if (value == "true") "Get Checkbox State${indent}${selector}${indent}==${indent}checked" else "Get Checkbox State${indent}${selector}${indent}==${indent}unchecked"
            "disabled" -> if (value == "true") "Get Element States${indent}${selector}${indent}contains${indent}disabled" else "Get Element States${indent}${selector}${indent}not contains${indent}disabled"
            else -> "Get Text${indent}${selector}${indent}==${indent}${value}"
        }
    }
}

/**
 * Template factory
 */
object CodeTemplateFactory {
    fun getTemplate(framework: TemplateFramework): CodeTemplate {
        return when (framework) {
            TemplateFramework.PYTHON_SYNC -> PythonSyncTemplate()
            TemplateFramework.PYTHON_PYTEST -> PytestTemplate()
            TemplateFramework.TYPESCRIPT -> TypeScriptTemplate()
            TemplateFramework.JAVASCRIPT -> JavaScriptTemplate()
            TemplateFramework.ROBOT_FRAMEWORK -> RobotFrameworkTemplate()
        }
    }
}
