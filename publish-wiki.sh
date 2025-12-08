#!/bin/bash

# Script to publish wiki pages to GitHub
# Prerequisites: Wiki must be enabled on GitHub first

echo "Publishing ZÆCTOR CORE Wiki..."

# Clone wiki repository
cd /tmp
rm -rf zaector-core.wiki
git clone https://github.com/ZAENRO-TECH/zaector-core.wiki.git

if [ $? -ne 0 ]; then
    echo ""
    echo "❌ Wiki repository not found!"
    echo ""
    echo "Please enable the wiki on GitHub first:"
    echo "1. Go to: https://github.com/ZAENRO-TECH/zaector-core/settings"
    echo "2. Under 'Features', enable 'Wikis'"
    echo "3. Go to: https://github.com/ZAENRO-TECH/zaector-core/wiki"
    echo "4. Click 'Create the first page'"
    echo "5. Add any content (will be overwritten)"
    echo "6. Save the page"
    echo "7. Run this script again"
    exit 1
fi

cd zaector-core.wiki

# Copy wiki files
cp "$1/wiki/"*.md .

# Commit and push
git add .
git commit -m "Add comprehensive wiki documentation"
git push origin master

echo ""
echo "✅ Wiki published successfully!"
echo "View at: https://github.com/ZAENRO-TECH/zaector-core/wiki"
