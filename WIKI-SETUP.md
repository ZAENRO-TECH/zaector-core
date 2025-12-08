# Wiki Setup Instructions

The wiki is enabled on GitHub, but needs to be initialized before we can publish the documentation pages.

## Steps to Initialize Wiki

1. **Go to the Wiki tab:**
   https://github.com/ZAENRO-TECH/zaector-core/wiki

2. **Create the first page:**
   - Click **"Create the first page"**
   - Title: `Home`
   - Content: (Add anything, it will be replaced)
   ```markdown
   # ZÆCTOR CORE Wiki

   Documentation is being set up...
   ```
   - Click **"Save Page"**

3. **Run the publish script:**
   ```bash
   cd C:/Users/bogdan/Documents/GitHub/zaector-core-clean
   ./publish-wiki.bat
   ```

This will upload all 5 wiki pages:
- **Home.md** - Landing page with overview
- **Installation-Guide.md** - Installation methods and troubleshooting
- **Quick-Start.md** - 5-minute getting started guide
- **Features-Overview.md** - Complete feature reference
- **Configuration.md** - Detailed settings documentation

## Alternative: Manual Upload

If the script doesn't work, you can manually create each page on GitHub:

1. Go to: https://github.com/ZAENRO-TECH/zaector-core/wiki/_new
2. For each file in `wiki/` directory:
   - Set page title (filename without .md)
   - Copy file content
   - Save page

## Wiki Pages Ready

All wiki pages are in the `wiki/` directory:
```
wiki/
├── Home.md
├── Installation-Guide.md
├── Quick-Start.md
├── Features-Overview.md
└── Configuration.md
```

Total: 5 pages, 1,500+ lines of comprehensive documentation.
