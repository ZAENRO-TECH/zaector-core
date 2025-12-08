@echo off
REM Script to publish wiki pages to GitHub
REM Prerequisites: Wiki must be enabled on GitHub first

echo Publishing ZAECTOR CORE Wiki...
echo.

REM Clone wiki repository
cd %TEMP%
if exist zaector-core.wiki rmdir /s /q zaector-core.wiki
git clone https://github.com/ZAENRO-TECH/zaector-core.wiki.git

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ❌ Wiki repository not found!
    echo.
    echo Please enable the wiki on GitHub first:
    echo 1. Go to: https://github.com/ZAENRO-TECH/zaector-core/settings
    echo 2. Under 'Features', enable 'Wikis'
    echo 3. Go to: https://github.com/ZAENRO-TECH/zaector-core/wiki
    echo 4. Click 'Create the first page'
    echo 5. Add any content (will be overwritten^)
    echo 6. Save the page
    echo 7. Run this script again
    exit /b 1
)

cd zaector-core.wiki

REM Copy wiki files
copy "%~dp0wiki\*.md" .

REM Commit and push
git add .
git commit -m "Add comprehensive wiki documentation"
git push origin master

echo.
echo ✅ Wiki published successfully!
echo View at: https://github.com/ZAENRO-TECH/zaector-core/wiki
