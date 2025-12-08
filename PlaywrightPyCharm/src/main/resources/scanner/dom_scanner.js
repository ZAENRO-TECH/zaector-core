(() => {
    window.__PLAYWRIGHT_SCANNER__ = {

        scan: function() {
            const interestingElements = [];
            const seenElements = new Set();

            const traverse = (root) => {
                if (!root) return;

                const children = root.children;

                if (children) {
                    for (let i = 0; i < children.length; i++) {
                        const element = children[i];
                        if (seenElements.has(element)) continue;
                        seenElements.add(element);

                        const meta = this.analyzeElement(element);
                        if (meta) {
                            interestingElements.push(meta);
                        }

                        if (element.shadowRoot) traverse(element.shadowRoot);
                        if (element.children.length > 0) traverse(element);
                    }
                }
            };

            traverse(document.body);
            return interestingElements;
        },

        analyzeElement: function(el) {
            const tag = el.tagName.toLowerCase();
            const role = el.getAttribute('role');

            // --- FILTER ---
            const isInteractive = (
                tag === 'button' || tag === 'a' || tag === 'select' || tag === 'textarea' ||
                tag === 'input' ||
                role === 'button' || role === 'link' || role === 'checkbox' || role === 'radio' ||
                el.onclick != null ||
                el.getAttribute('tabindex')
            );
            const hasId = el.id && el.id.length > 0;

            if (!isInteractive && !hasId) return null;

            // --- PROPERTIES SAMMELN (DEEP INSPECTION) ---
            const props = {};

            // 1. HTML Attribute (Basis)
            for (let i = 0; i < el.attributes.length; i++) {
                const attr = el.attributes[i];
                props[attr.name] = attr.value;
            }

            // 2. Wichtige DOM States (Live-Werte)
            // Wir wandeln alles in Strings um, damit Kotlin nicht meckert
            const liveProps = ['value', 'checked', 'disabled', 'readOnly', 'required', 'selected', 'selectedIndex', 'tabIndex'];
            liveProps.forEach(p => {
                if (el[p] !== undefined && el[p] !== null && el[p] !== '') {
                    props[p] = String(el[p]);
                }
            });

            // 3. Geometrie (Bounding Rect)
            const rect = el.getBoundingClientRect();
            props['geometry.x'] = String(Math.round(rect.x));
            props['geometry.y'] = String(Math.round(rect.y));
            props['geometry.width'] = String(Math.round(rect.width));
            props['geometry.height'] = String(Math.round(rect.height));

            // 4. Computed Styles (Was der User wirklich sieht)
            const style = window.getComputedStyle(el);
            const interestingStyles = [
                'display', 'visibility', 'opacity', 'z-index',
                'color', 'background-color', 'font-family', 'font-size',
                'cursor', 'position', 'overflow'
            ];

            interestingStyles.forEach(s => {
                const val = style.getPropertyValue(s);
                if (val) props['style.' + s] = val;
            });

            // 5. Text Content
            props['innerText'] = this.cleanText(el.innerText);
            props['tagName'] = tag;

            // 6. DOM Path (Breadcrumb)
            props['domPath'] = this.getDomPath(el);

            // 7. Visibility Status
            const isVisible = this.isElementVisible(el);
            props['visible'] = String(isVisible);

            // Selektor generieren
            const selectorInfo = this.generateBestSelector(el);

            // Add selector robustness score to attributes
            props['selectorScore'] = String(selectorInfo.score);
            props['selectorRating'] = selectorInfo.rating;

            return {
                tagName: tag,
                text: props['innerText'],
                id: el.id || null,
                selector: selectorInfo.selector,
                attributes: props
            };
        },

        generateBestSelector: function(el) {
            // Score system:
            // 10 = Excellent (test attributes)
            // 9 = Very Good (stable ID)
            // 8 = Good (name attribute)
            // 7 = Acceptable (placeholder, aria-label)
            // 6 = Fair (text content)
            // 5 = Moderate (title)
            // 3 = Weak (CSS class)
            // 1 = Fragile (tag only)

            // 1. Test-IDs (Score: 10)
            const testIdAttrs = ['data-testid', 'data-cy', 'data-test', 'data-qa'];
            for (const attr of testIdAttrs) {
                if (el.hasAttribute(attr)) {
                    return {
                        selector: `[${attr}="${el.getAttribute(attr)}"]`,
                        score: 10,
                        rating: 'Excellent'
                    };
                }
            }

            // 2. ID (Score: 9 if stable, 4 if dynamic)
            if (el.id) {
                const isDynamic = /\d{5,}/.test(el.id);
                return {
                    selector: `#${el.id}`,
                    score: isDynamic ? 4 : 9,
                    rating: isDynamic ? 'Moderate' : 'Very Good'
                };
            }

            // 3. Name (Score: 8)
            if (el.name) {
                return {
                    selector: `[name="${el.name}"]`,
                    score: 8,
                    rating: 'Good'
                };
            }

            // 4. Placeholder (Score: 7)
            if (el.getAttribute('placeholder')) {
                return {
                    selector: `[placeholder="${el.getAttribute('placeholder')}"]`,
                    score: 7,
                    rating: 'Acceptable'
                };
            }

            // 5. Text (Score: 6 if short, 4 if long)
            let text = el.innerText || '';
            text = text.trim();
            if (text.length > 0 && text.length < 30) {
                const tag = el.tagName.toLowerCase();
                if (['button', 'a', 'label', 'span', 'div'].includes(tag) || el.getAttribute('role') === 'button') {
                    const safeText = text.replace(/"/g, '\\"').replace(/\n/g, ' ');
                    return {
                        selector: `text="${safeText}"`,
                        score: 6,
                        rating: 'Fair'
                    };
                }
            } else if (text.length >= 30) {
                const tag = el.tagName.toLowerCase();
                if (['button', 'a'].includes(tag)) {
                    const safeText = text.substring(0, 30).replace(/"/g, '\\"').replace(/\n/g, ' ');
                    return {
                        selector: `text="${safeText}"`,
                        score: 4,
                        rating: 'Moderate'
                    };
                }
            }

            // 6. Aria / Title (Score: 7 for aria, 5 for title)
            if (el.getAttribute('aria-label')) {
                return {
                    selector: `[aria-label="${el.getAttribute('aria-label')}"]`,
                    score: 7,
                    rating: 'Acceptable'
                };
            }
            if (el.title) {
                return {
                    selector: `[title="${el.title}"]`,
                    score: 5,
                    rating: 'Moderate'
                };
            }

            // 7. Class (Score: 3)
            if (el.className && typeof el.className === 'string' && el.className.trim().length > 0) {
                const classes = el.className.split(' ').filter(c => c.length > 2 && !/[:/]/.test(c) && !c.includes('active') && !c.includes('hover'));
                if (classes.length > 0) {
                    return {
                        selector: `.${classes[0]}`,
                        score: 3,
                        rating: 'Weak'
                    };
                }
            }

            // 8. Tag only (Score: 1)
            return {
                selector: el.tagName.toLowerCase(),
                score: 1,
                rating: 'Fragile'
            };
        },

        cleanText: function(text) {
            if (!text) return '';
            return text.replace(/[\n\r\t]/g, ' ').trim().substring(0, 50);
        },

        getDomPath: function(el) {
            const path = [];
            let current = el;

            while (current && current !== document.body && current !== document.documentElement) {
                let selector = current.tagName.toLowerCase();

                if (current.id) {
                    selector += '#' + current.id;
                    path.unshift(selector);
                    break; // Stop at ID, it's unique
                } else if (current.className && typeof current.className === 'string') {
                    const classes = current.className.split(' ').filter(c => c.trim().length > 0);
                    if (classes.length > 0) {
                        selector += '.' + classes[0];
                    }
                }

                // Add index if there are siblings with same tag
                const siblings = current.parentNode ? Array.from(current.parentNode.children) : [];
                const sameTagSiblings = siblings.filter(s => s.tagName === current.tagName);
                if (sameTagSiblings.length > 1) {
                    const index = sameTagSiblings.indexOf(current) + 1;
                    selector += `:nth-of-type(${index})`;
                }

                path.unshift(selector);
                current = current.parentNode;
            }

            return path.join(' > ');
        },

        isElementVisible: function(el) {
            const rect = el.getBoundingClientRect();
            const style = window.getComputedStyle(el);

            return rect.width > 0 &&
                   rect.height > 0 &&
                   style.display !== 'none' &&
                   style.visibility !== 'hidden' &&
                   parseFloat(style.opacity) > 0;
        }
    };
})();