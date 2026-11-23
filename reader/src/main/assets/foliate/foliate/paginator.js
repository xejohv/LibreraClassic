function _classPrivateMethodInitSpec(e, a) { _checkPrivateRedeclaration(e, a), a.add(e); }
function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }
function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }
function _toPrimitive(t, e) { if ("object" != typeof t || !t) return t; var r; if ("undefined" != typeof Symbol && void 0 !== (r = t[Symbol.toPrimitive])) { var i = r.call(t, e || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === e ? String : Number)(t); }
function _classPrivateFieldInitSpec(e, t, a) { _checkPrivateRedeclaration(e, t), t.set(e, a); }
function _checkPrivateRedeclaration(e, t) { if (t.has(e)) throw new TypeError("Cannot initialize the same private elements twice on an object"); }
function _classPrivateFieldSet(s, a, r) { return s.set(_assertClassBrand(s, a), r), r; }
function _classPrivateFieldGet(s, a) { return s.get(_assertClassBrand(s, a)); }
function _assertClassBrand(e, t, n) { if ("function" == typeof e ? e === t : e.has(t)) return arguments.length < 3 ? t : n; throw new TypeError("Private element is not present on this object"); }
const wait = ms => new Promise(resolve => setTimeout(resolve, ms));
const debounce = (f, wait, immediate) => {
  let timeout;
  return (...args) => {
    const later = () => {
      timeout = null;
      if (!immediate) f(...args);
    };
    const callNow = immediate && !timeout;
    if (timeout) clearTimeout(timeout);
    timeout = setTimeout(later, wait);
    if (callNow) f(...args);
  };
};
const lerp = (min, max, x) => x * (max - min) + min;
const easeOutQuad = x => 1 - (1 - x) * (1 - x);
const animate = (a, b, duration, ease, render) => new Promise(resolve => {
  let start;
  const step = now => {
    if (document.hidden) {
      render(lerp(a, b, 1));
      return resolve();
    }
    start !== null && start !== void 0 ? start : start = now;
    const fraction = Math.min(1, (now - start) / duration);
    render(lerp(a, b, ease(fraction)));
    if (fraction < 1) requestAnimationFrame(step);else resolve();
  };
  if (document.hidden) {
    render(lerp(a, b, 1));
    return resolve();
  }
  requestAnimationFrame(step);
});

// collapsed range doesn't return client rects sometimes (or always?)
// try make get a non-collapsed range or element
const uncollapse = range => {
  if (!(range !== null && range !== void 0 && range.collapsed)) return range;
  const {
    endOffset,
    endContainer
  } = range;
  if (endContainer.nodeType === 1) {
    const node = endContainer.childNodes[endOffset];
    if ((node === null || node === void 0 ? void 0 : node.nodeType) === 1) return node;
    return endContainer;
  }
  if (endOffset + 1 < endContainer.length) range.setEnd(endContainer, endOffset + 1);else if (endOffset > 1) range.setStart(endContainer, endOffset - 1);else return endContainer.parentNode;
  return range;
};
const makeRange = (doc, node, start, end = start) => {
  const range = doc.createRange();
  range.setStart(node, start);
  range.setEnd(node, end);
  return range;
};

// use binary search to find an offset value in a text node
const bisectNode = (doc, node, cb, start = 0, end = node.nodeValue.length) => {
  if (end - start === 1) {
    const result = cb(makeRange(doc, node, start), makeRange(doc, node, end));
    return result < 0 ? start : end;
  }
  const mid = Math.floor(start + (end - start) / 2);
  const result = cb(makeRange(doc, node, start, mid), makeRange(doc, node, mid, end));
  return result < 0 ? bisectNode(doc, node, cb, start, mid) : result > 0 ? bisectNode(doc, node, cb, mid, end) : mid;
};
const {
  SHOW_ELEMENT,
  SHOW_TEXT,
  SHOW_CDATA_SECTION,
  FILTER_ACCEPT,
  FILTER_REJECT,
  FILTER_SKIP
} = NodeFilter;
const filter = SHOW_ELEMENT | SHOW_TEXT | SHOW_CDATA_SECTION;

// needed cause there seems to be a bug in `getBoundingClientRect()` in Firefox
// where it fails to include rects that have zero width and non-zero height
// (CSSOM spec says "rectangles [...] of which the height or width is not zero")
// which makes the visible range include an extra space at column boundaries
const getBoundingClientRect = target => {
  let top = Infinity,
    right = -Infinity,
    left = Infinity,
    bottom = -Infinity;
  for (const rect of target.getClientRects()) {
    left = Math.min(left, rect.left);
    top = Math.min(top, rect.top);
    right = Math.max(right, rect.right);
    bottom = Math.max(bottom, rect.bottom);
  }
  return new DOMRect(left, top, right - left, bottom - top);
};
const getVisibleRange = (doc, start, end, mapRect) => {
  var _nodes$, _nodes;
  // first get all visible nodes
  const acceptNode = node => {
    var _node$localName;
    const name = (_node$localName = node.localName) === null || _node$localName === void 0 ? void 0 : _node$localName.toLowerCase();
    // ignore all scripts, styles, and their children
    if (name === 'script' || name === 'style') return FILTER_REJECT;
    if (node.nodeType === 1) {
      const {
        left,
        right
      } = mapRect(node.getBoundingClientRect());
      // no need to check child nodes if it's completely out of view
      if (right < start || left > end) return FILTER_REJECT;
      // elements must be completely in view to be considered visible
      // because you can't specify offsets for elements
      if (left >= start && right <= end) return FILTER_ACCEPT;
      // TODO: it should probably allow elements that do not contain text
      // because they can exceed the whole viewport in both directions
      // especially in scrolled mode
    } else {
      var _node$nodeValue;
      // ignore empty text nodes
      if (!((_node$nodeValue = node.nodeValue) !== null && _node$nodeValue !== void 0 && _node$nodeValue.trim())) return FILTER_SKIP;
      // create range to get rect
      const range = doc.createRange();
      range.selectNodeContents(node);
      const {
        left,
        right
      } = mapRect(range.getBoundingClientRect());
      // it's visible if any part of it is in view
      if (right >= start && left <= end) return FILTER_ACCEPT;
    }
    return FILTER_SKIP;
  };
  const walker = doc.createTreeWalker(doc.body, filter, {
    acceptNode
  });
  const nodes = [];
  for (let node = walker.nextNode(); node; node = walker.nextNode()) nodes.push(node);

  // we're only interested in the first and last visible nodes
  const from = (_nodes$ = nodes[0]) !== null && _nodes$ !== void 0 ? _nodes$ : doc.body;
  const to = (_nodes = nodes[nodes.length - 1]) !== null && _nodes !== void 0 ? _nodes : from;

  // find the offset at which visibility changes
  const startOffset = from.nodeType === 1 ? 0 : bisectNode(doc, from, (a, b) => {
    const p = mapRect(getBoundingClientRect(a));
    const q = mapRect(getBoundingClientRect(b));
    if (p.right < start && q.left > start) return 0;
    return q.left > start ? -1 : 1;
  });
  const endOffset = to.nodeType === 1 ? 0 : bisectNode(doc, to, (a, b) => {
    const p = mapRect(getBoundingClientRect(a));
    const q = mapRect(getBoundingClientRect(b));
    if (p.right < end && q.left > end) return 0;
    return q.left > end ? -1 : 1;
  });
  const range = doc.createRange();
  range.setStart(from, startOffset);
  range.setEnd(to, endOffset);
  return range;
};
const selectionIsBackward = sel => {
  const range = document.createRange();
  range.setStart(sel.anchorNode, sel.anchorOffset);
  range.setEnd(sel.focusNode, sel.focusOffset);
  return range.collapsed;
};
const setSelectionTo = (target, collapse) => {
  let range;
  if (target.startContainer) range = target.cloneRange();else if (target.nodeType) {
    range = document.createRange();
    range.selectNode(target);
  }
  if (range) {
    const sel = range.startContainer.ownerDocument.defaultView.getSelection();
    if (sel) {
      sel.removeAllRanges();
      if (collapse === -1) range.collapse(true);else if (collapse === 1) range.collapse();
      sel.addRange(range);
    }
  }
};
const getDirection = doc => {
  const {
    defaultView
  } = doc;
  const {
    writingMode,
    direction
  } = defaultView.getComputedStyle(doc.body);
  const vertical = writingMode === 'vertical-rl' || writingMode === 'vertical-lr';
  const rtl = doc.body.dir === 'rtl' || direction === 'rtl' || doc.documentElement.dir === 'rtl';
  return {
    vertical,
    rtl
  };
};
const getBackground = doc => {
  const bodyStyle = doc.defaultView.getComputedStyle(doc.body);
  return bodyStyle.backgroundColor === 'rgba(0, 0, 0, 0)' && bodyStyle.backgroundImage === 'none' ? doc.defaultView.getComputedStyle(doc.documentElement).background : bodyStyle.background;
};
const makeMarginals = (length, part) => Array.from({
  length
}, () => {
  const div = document.createElement('div');
  const child = document.createElement('div');
  div.append(child);
  child.setAttribute('part', part);
  return div;
});
const setStylesImportant = (el, styles) => {
  const {
    style
  } = el;
  for (const [k, v] of Object.entries(styles)) style.setProperty(k, v, 'important');
};
var _observer = /*#__PURE__*/new WeakMap();
var _element = /*#__PURE__*/new WeakMap();
var _iframe = /*#__PURE__*/new WeakMap();
var _contentRange = /*#__PURE__*/new WeakMap();
var _overlayer = /*#__PURE__*/new WeakMap();
var _vertical = /*#__PURE__*/new WeakMap();
var _rtl = /*#__PURE__*/new WeakMap();
var _column = /*#__PURE__*/new WeakMap();
var _size = /*#__PURE__*/new WeakMap();
var _layout = /*#__PURE__*/new WeakMap();
class View {
  constructor({
    container,
    onExpand
  }) {
    _classPrivateFieldInitSpec(this, _observer, new ResizeObserver(() => this.expand()));
    _classPrivateFieldInitSpec(this, _element, document.createElement('div'));
    _classPrivateFieldInitSpec(this, _iframe, document.createElement('iframe'));
    _classPrivateFieldInitSpec(this, _contentRange, document.createRange());
    _classPrivateFieldInitSpec(this, _overlayer, void 0);
    _classPrivateFieldInitSpec(this, _vertical, false);
    _classPrivateFieldInitSpec(this, _rtl, false);
    _classPrivateFieldInitSpec(this, _column, true);
    _classPrivateFieldInitSpec(this, _size, void 0);
    _classPrivateFieldInitSpec(this, _layout, {});
    this.container = container;
    this.onExpand = onExpand;
    _classPrivateFieldGet(_iframe, this).setAttribute('part', 'filter');
    _classPrivateFieldGet(_element, this).append(_classPrivateFieldGet(_iframe, this));
    Object.assign(_classPrivateFieldGet(_element, this).style, {
      boxSizing: 'content-box',
      position: 'relative',
      overflow: 'hidden',
      flex: '0 0 auto',
      width: '100%',
      height: '100%',
      display: 'flex',
      justifyContent: 'center',
      alignItems: 'center'
    });
    Object.assign(_classPrivateFieldGet(_iframe, this).style, {
      overflow: 'hidden',
      border: '0',
      display: 'none',
      width: '100%',
      height: '100%'
    });
    // `allow-scripts` is needed for events because of WebKit bug
    // https://bugs.webkit.org/show_bug.cgi?id=218086
    _classPrivateFieldGet(_iframe, this).setAttribute('sandbox', 'allow-same-origin allow-scripts');
    _classPrivateFieldGet(_iframe, this).setAttribute('scrolling', 'no');
  }
  get element() {
    return _classPrivateFieldGet(_element, this);
  }
  get document() {
    return _classPrivateFieldGet(_iframe, this).contentDocument;
  }
  async load(src, afterLoad, beforeRender) {
    if (typeof src !== 'string') throw new Error(`${src} is not string`);
    return new Promise(resolve => {
      _classPrivateFieldGet(_iframe, this).addEventListener('load', () => {
        const doc = this.document;
        afterLoad === null || afterLoad === void 0 || afterLoad(doc);

        // it needs to be visible for Firefox to get computed style
        _classPrivateFieldGet(_iframe, this).style.display = 'block';
        const {
          vertical,
          rtl
        } = getDirection(doc);
        const background = getBackground(doc);
        _classPrivateFieldGet(_iframe, this).style.display = 'none';
        _classPrivateFieldSet(_vertical, this, vertical);
        _classPrivateFieldSet(_rtl, this, rtl);
        _classPrivateFieldGet(_contentRange, this).selectNodeContents(doc.body);
        const layout = beforeRender === null || beforeRender === void 0 ? void 0 : beforeRender({
          vertical,
          rtl,
          background
        });
        _classPrivateFieldGet(_iframe, this).style.display = 'block';
        this.render(layout);
        _classPrivateFieldGet(_observer, this).observe(doc.body);

        // the resize observer above doesn't work in Firefox
        // (see https://bugzilla.mozilla.org/show_bug.cgi?id=1832939)
        // until the bug is fixed we can at least account for font load
        doc.fonts.ready.then(() => this.expand());
        resolve();
      }, {
        once: true
      });
      _classPrivateFieldGet(_iframe, this).src = src;
    });
  }
  render(layout) {
    if (!layout) return;
    _classPrivateFieldSet(_column, this, layout.flow !== 'scrolled');
    _classPrivateFieldSet(_layout, this, layout);
    if (_classPrivateFieldGet(_column, this)) this.columnize(layout);else this.scrolled(layout);
  }
  scrolled({
    gap,
    columnWidth
  }) {
    const vertical = _classPrivateFieldGet(_vertical, this);
    const doc = this.document;
    setStylesImportant(doc.documentElement, {
      'box-sizing': 'border-box',
      'padding': vertical ? `${gap}px 0` : `0 ${gap}px`,
      'column-width': 'auto',
      'height': 'auto',
      'width': 'auto'
    });
    setStylesImportant(doc.body, {
      [vertical ? 'max-height' : 'max-width']: `${columnWidth}px`,
      'margin': 'auto'
    });
    this.setImageSize();
    this.expand();
  }
  columnize({
    width,
    height,
    gap,
    columnWidth
  }) {
    const vertical = _classPrivateFieldGet(_vertical, this);
    _classPrivateFieldSet(_size, this, vertical ? height : width);
    const doc = this.document;
    setStylesImportant(doc.documentElement, {
      'box-sizing': 'border-box',
      'column-width': `${Math.trunc(columnWidth)}px`,
      'column-gap': `${gap}px`,
      'column-fill': 'auto',
      ...(vertical ? {
        'width': `${width}px`
      } : {
        'height': `${height}px`
      }),
      'padding': vertical ? `${gap / 2}px 0` : `0 ${gap / 2}px`,
      'overflow': 'hidden',
      // force wrap long words
      'overflow-wrap': 'break-word',
      // reset some potentially problematic props
      'position': 'static',
      'border': '0',
      'margin': '0',
      'max-height': 'none',
      'max-width': 'none',
      'min-height': 'none',
      'min-width': 'none',
      // fix glyph clipping in WebKit
      '-webkit-line-box-contain': 'block glyphs replaced'
    });
    setStylesImportant(doc.body, {
      'max-height': 'none',
      'max-width': 'none',
      'margin': '0'
    });
    this.setImageSize();
    this.expand();
  }
  setImageSize() {
    const {
      width,
      height,
      margin
    } = _classPrivateFieldGet(_layout, this);
    const vertical = _classPrivateFieldGet(_vertical, this);
    const doc = this.document;
    for (const el of doc.body.querySelectorAll('img, svg, video')) {
      // preserve max size if they are already set
      const {
        maxHeight,
        maxWidth
      } = doc.defaultView.getComputedStyle(el);
      setStylesImportant(el, {
        'max-height': vertical ? maxHeight !== 'none' && maxHeight !== '0px' ? maxHeight : '100%' : `${height - margin * 2}px`,
        'max-width': vertical ? `${width - margin * 2}px` : maxWidth !== 'none' && maxWidth !== '0px' ? maxWidth : '100%',
        'object-fit': 'contain',
        'page-break-inside': 'avoid',
        'break-inside': 'avoid',
        'box-sizing': 'border-box'
      });
    }
  }
  expand() {
    const {
      documentElement
    } = this.document;
    if (_classPrivateFieldGet(_column, this)) {
      const side = _classPrivateFieldGet(_vertical, this) ? 'height' : 'width';
      const otherSide = _classPrivateFieldGet(_vertical, this) ? 'width' : 'height';
      const contentRect = _classPrivateFieldGet(_contentRange, this).getBoundingClientRect();
      const rootRect = documentElement.getBoundingClientRect();
      // offset caused by column break at the start of the page
      // which seem to be supported only by WebKit and only for horizontal writing
      const contentStart = _classPrivateFieldGet(_vertical, this) ? 0 : _classPrivateFieldGet(_rtl, this) ? rootRect.right - contentRect.right : contentRect.left - rootRect.left;
      const contentSize = contentStart + contentRect[side];
      const pageCount = Math.ceil(contentSize / _classPrivateFieldGet(_size, this));
      const expandedSize = pageCount * _classPrivateFieldGet(_size, this);
      _classPrivateFieldGet(_element, this).style.padding = '0';
      _classPrivateFieldGet(_iframe, this).style[side] = `${expandedSize}px`;
      _classPrivateFieldGet(_element, this).style[side] = `${expandedSize + _classPrivateFieldGet(_size, this) * 2}px`;
      _classPrivateFieldGet(_iframe, this).style[otherSide] = '100%';
      _classPrivateFieldGet(_element, this).style[otherSide] = '100%';
      documentElement.style[side] = `${_classPrivateFieldGet(_size, this)}px`;
      if (_classPrivateFieldGet(_overlayer, this)) {
        _classPrivateFieldGet(_overlayer, this).element.style.margin = '0';
        _classPrivateFieldGet(_overlayer, this).element.style.left = _classPrivateFieldGet(_vertical, this) ? '0' : `${_classPrivateFieldGet(_size, this)}px`;
        _classPrivateFieldGet(_overlayer, this).element.style.top = _classPrivateFieldGet(_vertical, this) ? `${_classPrivateFieldGet(_size, this)}px` : '0';
        _classPrivateFieldGet(_overlayer, this).element.style[side] = `${expandedSize}px`;
        _classPrivateFieldGet(_overlayer, this).redraw();
      }
    } else {
      const side = _classPrivateFieldGet(_vertical, this) ? 'width' : 'height';
      const otherSide = _classPrivateFieldGet(_vertical, this) ? 'height' : 'width';
      const contentSize = documentElement.getBoundingClientRect()[side];
      const expandedSize = contentSize;
      const {
        margin
      } = _classPrivateFieldGet(_layout, this);
      const padding = _classPrivateFieldGet(_vertical, this) ? `0 ${margin}px` : `${margin}px 0`;
      _classPrivateFieldGet(_element, this).style.padding = padding;
      _classPrivateFieldGet(_iframe, this).style[side] = `${expandedSize}px`;
      _classPrivateFieldGet(_element, this).style[side] = `${expandedSize}px`;
      _classPrivateFieldGet(_iframe, this).style[otherSide] = '100%';
      _classPrivateFieldGet(_element, this).style[otherSide] = '100%';
      if (_classPrivateFieldGet(_overlayer, this)) {
        _classPrivateFieldGet(_overlayer, this).element.style.margin = padding;
        _classPrivateFieldGet(_overlayer, this).element.style.left = '0';
        _classPrivateFieldGet(_overlayer, this).element.style.top = '0';
        _classPrivateFieldGet(_overlayer, this).element.style[side] = `${expandedSize}px`;
        _classPrivateFieldGet(_overlayer, this).redraw();
      }
    }
    this.onExpand();
  }
  set overlayer(overlayer) {
    _classPrivateFieldSet(_overlayer, this, overlayer);
    _classPrivateFieldGet(_element, this).append(overlayer.element);
  }
  get overlayer() {
    return _classPrivateFieldGet(_overlayer, this);
  }
  destroy() {
    if (this.document) _classPrivateFieldGet(_observer, this).unobserve(this.document.body);
  }
}

// NOTE: everything here assumes the so-called "negative scroll type" for RTL
var _root = /*#__PURE__*/new WeakMap();
var _observer2 = /*#__PURE__*/new WeakMap();
var _top = /*#__PURE__*/new WeakMap();
var _background = /*#__PURE__*/new WeakMap();
var _container = /*#__PURE__*/new WeakMap();
var _header = /*#__PURE__*/new WeakMap();
var _footer = /*#__PURE__*/new WeakMap();
var _view = /*#__PURE__*/new WeakMap();
var _vertical2 = /*#__PURE__*/new WeakMap();
var _rtl2 = /*#__PURE__*/new WeakMap();
var _margin = /*#__PURE__*/new WeakMap();
var _index = /*#__PURE__*/new WeakMap();
var _anchor = /*#__PURE__*/new WeakMap();
var _justAnchored = /*#__PURE__*/new WeakMap();
var _locked = /*#__PURE__*/new WeakMap();
var _styles = /*#__PURE__*/new WeakMap();
var _styleMap = /*#__PURE__*/new WeakMap();
var _mediaQuery = /*#__PURE__*/new WeakMap();
var _mediaQueryListener = /*#__PURE__*/new WeakMap();
var _scrollBounds = /*#__PURE__*/new WeakMap();
var _touchState = /*#__PURE__*/new WeakMap();
var _touchScrolled = /*#__PURE__*/new WeakMap();
var _lastVisibleRange = /*#__PURE__*/new WeakMap();
var _Paginator_brand = /*#__PURE__*/new WeakSet();
export class Paginator extends HTMLElement {
  constructor() {
    super();
    _classPrivateMethodInitSpec(this, _Paginator_brand);
    _classPrivateFieldInitSpec(this, _root, this.attachShadow({
      mode: 'closed'
    }));
    _classPrivateFieldInitSpec(this, _observer2, new ResizeObserver(() => this.render()));
    _classPrivateFieldInitSpec(this, _top, void 0);
    _classPrivateFieldInitSpec(this, _background, void 0);
    _classPrivateFieldInitSpec(this, _container, void 0);
    _classPrivateFieldInitSpec(this, _header, void 0);
    _classPrivateFieldInitSpec(this, _footer, void 0);
    _classPrivateFieldInitSpec(this, _view, void 0);
    _classPrivateFieldInitSpec(this, _vertical2, false);
    _classPrivateFieldInitSpec(this, _rtl2, false);
    _classPrivateFieldInitSpec(this, _margin, 0);
    _classPrivateFieldInitSpec(this, _index, -1);
    _classPrivateFieldInitSpec(this, _anchor, 0);
    // anchor view to a fraction (0-1), Range, or Element
    _classPrivateFieldInitSpec(this, _justAnchored, false);
    _classPrivateFieldInitSpec(this, _locked, false);
    // while true, prevent any further navigation
    _classPrivateFieldInitSpec(this, _styles, void 0);
    _classPrivateFieldInitSpec(this, _styleMap, new WeakMap());
    _classPrivateFieldInitSpec(this, _mediaQuery, matchMedia('(prefers-color-scheme: dark)'));
    _classPrivateFieldInitSpec(this, _mediaQueryListener, void 0);
    _classPrivateFieldInitSpec(this, _scrollBounds, void 0);
    _classPrivateFieldInitSpec(this, _touchState, void 0);
    _classPrivateFieldInitSpec(this, _touchScrolled, void 0);
    _classPrivateFieldInitSpec(this, _lastVisibleRange, void 0);
    _classPrivateFieldGet(_root, this).innerHTML = `<style>
        :host {
            display: block;
            container-type: size;
        }
        :host, #top {
            box-sizing: border-box;
            position: relative;
            overflow: hidden;
            width: 100%;
            height: 100%;
        }
        #top {
            --_gap: 7%;
            --_margin: 48px;
            --_max-inline-size: 720px;
            --_max-block-size: 1440px;
            --_max-column-count: 2;
            --_max-column-count-portrait: 1;
            --_max-column-count-spread: var(--_max-column-count);
            --_half-gap: calc(var(--_gap) / 2);
            --_max-width: calc(var(--_max-inline-size) * var(--_max-column-count-spread));
            --_max-height: var(--_max-block-size);
            display: grid;
            grid-template-columns:
                minmax(var(--_half-gap), 1fr)
                var(--_half-gap)
                minmax(0, calc(var(--_max-width) - var(--_gap)))
                var(--_half-gap)
                minmax(var(--_half-gap), 1fr);
            grid-template-rows:
                minmax(var(--_margin), 1fr)
                minmax(0, var(--_max-height))
                minmax(var(--_margin), 1fr);
            &.vertical {
                --_max-column-count-spread: var(--_max-column-count-portrait);
                --_max-width: var(--_max-block-size);
                --_max-height: calc(var(--_max-inline-size) * var(--_max-column-count-spread));
            }
            @container (orientation: portrait) {
                & {
                    --_max-column-count-spread: var(--_max-column-count-portrait);
                }
                &.vertical {
                    --_max-column-count-spread: var(--_max-column-count);
                }
            }
        }
        #background {
            grid-column: 1 / -1;
            grid-row: 1 / -1;
        }
        #container {
            grid-column: 2 / 5;
            grid-row: 2;
            overflow: hidden;
        }
        :host([flow="scrolled"]) #container {
            grid-column: 1 / -1;
            grid-row: 1 / -1;
            overflow: auto;
        }
        #header {
            grid-column: 3 / 4;
            grid-row: 1;
        }
        #footer {
            grid-column: 3 / 4;
            grid-row: 3;
            align-self: end;
        }
        #header, #footer {
            display: grid;
            height: var(--_margin);
        }
        :is(#header, #footer) > * {
            display: flex;
            align-items: center;
            min-width: 0;
        }
        :is(#header, #footer) > * > * {
            width: 100%;
            overflow: hidden;
            white-space: nowrap;
            text-overflow: ellipsis;
            text-align: center;
            font-size: .75em;
            opacity: .6;
        }
        </style>
        <div id="top">
            <div id="background" part="filter"></div>
            <div id="header"></div>
            <div id="container"></div>
            <div id="footer"></div>
        </div>
        `;
    _classPrivateFieldSet(_top, this, _classPrivateFieldGet(_root, this).getElementById('top'));
    _classPrivateFieldSet(_background, this, _classPrivateFieldGet(_root, this).getElementById('background'));
    _classPrivateFieldSet(_container, this, _classPrivateFieldGet(_root, this).getElementById('container'));
    _classPrivateFieldSet(_header, this, _classPrivateFieldGet(_root, this).getElementById('header'));
    _classPrivateFieldSet(_footer, this, _classPrivateFieldGet(_root, this).getElementById('footer'));
    _classPrivateFieldGet(_observer2, this).observe(_classPrivateFieldGet(_container, this));
    _classPrivateFieldGet(_container, this).addEventListener('scroll', () => this.dispatchEvent(new Event('scroll')));
    _classPrivateFieldGet(_container, this).addEventListener('scroll', debounce(() => {
      if (this.scrolled) {
        if (_classPrivateFieldGet(_justAnchored, this)) _classPrivateFieldSet(_justAnchored, this, false);else _assertClassBrand(_Paginator_brand, this, _afterScroll).call(this, 'scroll');
      }
    }, 250));
    const opts = {
      passive: false
    };
    this.addEventListener('touchstart', _assertClassBrand(_Paginator_brand, this, _onTouchStart).bind(this), opts);
    this.addEventListener('touchmove', _assertClassBrand(_Paginator_brand, this, _onTouchMove).bind(this), opts);
    this.addEventListener('touchend', _assertClassBrand(_Paginator_brand, this, _onTouchEnd).bind(this));
    this.addEventListener('load', ({
      detail: {
        doc
      }
    }) => {
      doc.addEventListener('touchstart', _assertClassBrand(_Paginator_brand, this, _onTouchStart).bind(this), opts);
      doc.addEventListener('touchmove', _assertClassBrand(_Paginator_brand, this, _onTouchMove).bind(this), opts);
      doc.addEventListener('touchend', _assertClassBrand(_Paginator_brand, this, _onTouchEnd).bind(this));
    });
    this.addEventListener('relocate', ({
      detail
    }) => {
      if (detail.reason === 'selection') setSelectionTo(_classPrivateFieldGet(_anchor, this), 0);else if (detail.reason === 'navigation') {
        if (_classPrivateFieldGet(_anchor, this) === 1) setSelectionTo(detail.range, 1);else if (typeof _classPrivateFieldGet(_anchor, this) === 'number') setSelectionTo(detail.range, -1);else setSelectionTo(_classPrivateFieldGet(_anchor, this), -1);
      }
    });
    const checkPointerSelection = debounce((range, sel) => {
      if (!sel.rangeCount) return;
      const selRange = sel.getRangeAt(0);
      const backward = selectionIsBackward(sel);
      if (backward && selRange.compareBoundaryPoints(Range.START_TO_START, range) < 0) this.prev();else if (!backward && selRange.compareBoundaryPoints(Range.END_TO_END, range) > 0) this.next();
    }, 700);
    this.addEventListener('load', ({
      detail: {
        doc
      }
    }) => {
      let isPointerSelecting = false;
      doc.addEventListener('pointerdown', () => isPointerSelecting = true);
      doc.addEventListener('pointerup', () => isPointerSelecting = false);
      let isKeyboardSelecting = false;
      doc.addEventListener('keydown', () => isKeyboardSelecting = true);
      doc.addEventListener('keyup', () => isKeyboardSelecting = false);
      doc.addEventListener('selectionchange', () => {
        if (this.scrolled) return;
        const range = _classPrivateFieldGet(_lastVisibleRange, this);
        if (!range) return;
        const sel = doc.getSelection();
        if (!sel.rangeCount) return;
        if (isPointerSelecting && sel.type === 'Range') checkPointerSelection(range, sel);else if (isKeyboardSelecting) {
          const selRange = sel.getRangeAt(0).cloneRange();
          const backward = selectionIsBackward(sel);
          if (!backward) selRange.collapse();
          _assertClassBrand(_Paginator_brand, this, _scrollToAnchor).call(this, selRange);
        }
      });
      doc.addEventListener('focusin', e => this.scrolled ? null :
      // NOTE: `requestAnimationFrame` is needed in WebKit
      requestAnimationFrame(() => _assertClassBrand(_Paginator_brand, this, _scrollToAnchor).call(this, e.target)));
    });
    _classPrivateFieldSet(_mediaQueryListener, this, () => {
      if (!_classPrivateFieldGet(_view, this)) return;
      _classPrivateFieldGet(_background, this).style.background = getBackground(_classPrivateFieldGet(_view, this).document);
    });
    _classPrivateFieldGet(_mediaQuery, this).addEventListener('change', _classPrivateFieldGet(_mediaQueryListener, this));
  }
  attributeChangedCallback(name, _, value) {
    switch (name) {
      case 'flow':
        this.render();
        break;
      case 'gap':
      case 'margin':
      case 'max-block-size':
      case 'max-column-count':
        _classPrivateFieldGet(_top, this).style.setProperty('--_' + name, value);
        break;
      case 'max-inline-size':
        // needs explicit `render()` as it doesn't necessarily resize
        _classPrivateFieldGet(_top, this).style.setProperty('--_' + name, value);
        this.render();
        break;
    }
  }
  open(book) {
    var _book$transformTarget;
    this.bookDir = book.dir;
    this.sections = book.sections;
    (_book$transformTarget = book.transformTarget) === null || _book$transformTarget === void 0 || _book$transformTarget.addEventListener('data', ({
      detail
    }) => {
      if (detail.type !== 'text/css') return;
      const w = innerWidth;
      const h = innerHeight;
      detail.data = Promise.resolve(detail.data).then(data => data
      // unprefix as most of the props are (only) supported unprefixed
      .replace(/(?<=[{\s;])-epub-/gi, '')
      // replace vw and vh as they cause problems with layout
      .replace(/(\d*\.?\d+)vw/gi, (_, d) => parseFloat(d) * w / 100 + 'px').replace(/(\d*\.?\d+)vh/gi, (_, d) => parseFloat(d) * h / 100 + 'px')
      // `page-break-*` unsupported in columns; replace with `column-break-*`
      .replace(/page-break-(after|before|inside)\s*:/gi, (_, x) => `-webkit-column-break-${x}:`).replace(/break-(after|before|inside)\s*:\s*(avoid-)?page/gi, (_, x, y) => `break-${x}: ${y !== null && y !== void 0 ? y : ''}column`));
    });
  }
  render() {
    if (!_classPrivateFieldGet(_view, this)) return;
    _classPrivateFieldGet(_view, this).render(_assertClassBrand(_Paginator_brand, this, _beforeRender).call(this, {
      vertical: _classPrivateFieldGet(_vertical2, this),
      rtl: _classPrivateFieldGet(_rtl2, this)
    }));
    _assertClassBrand(_Paginator_brand, this, _scrollToAnchor).call(this, _classPrivateFieldGet(_anchor, this));
  }
  get scrolled() {
    return this.getAttribute('flow') === 'scrolled';
  }
  get scrollProp() {
    const {
      scrolled
    } = this;
    return _classPrivateFieldGet(_vertical2, this) ? scrolled ? 'scrollLeft' : 'scrollTop' : scrolled ? 'scrollTop' : 'scrollLeft';
  }
  get sideProp() {
    const {
      scrolled
    } = this;
    return _classPrivateFieldGet(_vertical2, this) ? scrolled ? 'width' : 'height' : scrolled ? 'height' : 'width';
  }
  get size() {
    return _classPrivateFieldGet(_container, this).getBoundingClientRect()[this.sideProp];
  }
  get viewSize() {
    return _classPrivateFieldGet(_view, this).element.getBoundingClientRect()[this.sideProp];
  }
  get start() {
    return Math.abs(_classPrivateFieldGet(_container, this)[this.scrollProp]);
  }
  get end() {
    return this.start + this.size;
  }
  get page() {
    return Math.floor((this.start + this.end) / 2 / this.size);
  }
  get pages() {
    return Math.round(this.viewSize / this.size);
  }
  scrollBy(dx, dy) {
    const delta = _classPrivateFieldGet(_vertical2, this) ? dy : dx;
    const element = _classPrivateFieldGet(_container, this);
    const {
      scrollProp
    } = this;
    const [offset, a, b] = _classPrivateFieldGet(_scrollBounds, this);
    const rtl = _classPrivateFieldGet(_rtl2, this);
    const min = rtl ? offset - b : offset - a;
    const max = rtl ? offset + a : offset + b;
    element[scrollProp] = Math.max(min, Math.min(max, element[scrollProp] + delta));
  }
  snap(vx, vy) {
    const velocity = _classPrivateFieldGet(_vertical2, this) ? vy : vx;
    const [offset, a, b] = _classPrivateFieldGet(_scrollBounds, this);
    const {
      start,
      end,
      pages,
      size
    } = this;
    const min = Math.abs(offset) - a;
    const max = Math.abs(offset) + b;
    const d = velocity * (_classPrivateFieldGet(_rtl2, this) ? -size : size);
    const page = Math.floor(Math.max(min, Math.min(max, (start + end) / 2 + (isNaN(d) ? 0 : d))) / size);
    _assertClassBrand(_Paginator_brand, this, _scrollToPage).call(this, page, 'snap').then(() => {
      const dir = page <= 0 ? -1 : page >= pages - 1 ? 1 : null;
      if (dir) return _assertClassBrand(_Paginator_brand, this, _goTo).call(this, {
        index: _assertClassBrand(_Paginator_brand, this, _adjacentIndex).call(this, dir),
        anchor: dir < 0 ? () => 1 : () => 0
      });
    });
  }
  async scrollToAnchor(anchor, select) {
    return _assertClassBrand(_Paginator_brand, this, _scrollToAnchor).call(this, anchor, select ? 'selection' : 'navigation');
  }
  async goTo(target) {
    if (_classPrivateFieldGet(_locked, this)) return;
    const resolved = await target;
    if (_assertClassBrand(_Paginator_brand, this, _canGoToIndex).call(this, resolved.index)) return _assertClassBrand(_Paginator_brand, this, _goTo).call(this, resolved);
  }
  get atStart() {
    return _assertClassBrand(_Paginator_brand, this, _adjacentIndex).call(this, -1) == null && this.page <= 1;
  }
  get atEnd() {
    return _assertClassBrand(_Paginator_brand, this, _adjacentIndex).call(this, 1) == null && this.page >= this.pages - 2;
  }
  prev(distance) {
    return _assertClassBrand(_Paginator_brand, this, _turnPage).call(this, -1, distance);
  }
  next(distance) {
    return _assertClassBrand(_Paginator_brand, this, _turnPage).call(this, 1, distance);
  }
  prevSection() {
    return this.goTo({
      index: _assertClassBrand(_Paginator_brand, this, _adjacentIndex).call(this, -1)
    });
  }
  nextSection() {
    return this.goTo({
      index: _assertClassBrand(_Paginator_brand, this, _adjacentIndex).call(this, 1)
    });
  }
  firstSection() {
    const index = this.sections.findIndex(section => section.linear !== 'no');
    return this.goTo({
      index
    });
  }
  lastSection() {
    const index = this.sections.findLastIndex(section => section.linear !== 'no');
    return this.goTo({
      index
    });
  }
  getContents() {
    if (_classPrivateFieldGet(_view, this)) return [{
      index: _classPrivateFieldGet(_index, this),
      overlayer: _classPrivateFieldGet(_view, this).overlayer,
      doc: _classPrivateFieldGet(_view, this).document
    }];
    return [];
  }
  setStyles(styles) {
    var _classPrivateFieldGet2, _classPrivateFieldGet3;
    _classPrivateFieldSet(_styles, this, styles);
    const $$styles = _classPrivateFieldGet(_styleMap, this).get((_classPrivateFieldGet2 = _classPrivateFieldGet(_view, this)) === null || _classPrivateFieldGet2 === void 0 ? void 0 : _classPrivateFieldGet2.document);
    if (!$$styles) return;
    const [$beforeStyle, $style] = $$styles;
    if (Array.isArray(styles)) {
      const [beforeStyle, style] = styles;
      $beforeStyle.textContent = beforeStyle;
      $style.textContent = style;
    } else $style.textContent = styles;

    // NOTE: needs `requestAnimationFrame` in Chromium
    requestAnimationFrame(() => _classPrivateFieldGet(_background, this).style.background = getBackground(_classPrivateFieldGet(_view, this).document));

    // needed because the resize observer doesn't work in Firefox
    (_classPrivateFieldGet3 = _classPrivateFieldGet(_view, this)) === null || _classPrivateFieldGet3 === void 0 || (_classPrivateFieldGet3 = _classPrivateFieldGet3.document) === null || _classPrivateFieldGet3 === void 0 || (_classPrivateFieldGet3 = _classPrivateFieldGet3.fonts) === null || _classPrivateFieldGet3 === void 0 || (_classPrivateFieldGet3 = _classPrivateFieldGet3.ready) === null || _classPrivateFieldGet3 === void 0 || _classPrivateFieldGet3.then(() => _classPrivateFieldGet(_view, this).expand());
  }
  focusView() {
    _classPrivateFieldGet(_view, this).document.defaultView.focus();
  }
  destroy() {
    var _this$sections$_class, _this$sections$_class2;
    _classPrivateFieldGet(_observer2, this).unobserve(this);
    _classPrivateFieldGet(_view, this).destroy();
    _classPrivateFieldSet(_view, this, null);
    (_this$sections$_class = this.sections[_classPrivateFieldGet(_index, this)]) === null || _this$sections$_class === void 0 || (_this$sections$_class2 = _this$sections$_class.unload) === null || _this$sections$_class2 === void 0 || _this$sections$_class2.call(_this$sections$_class);
    _classPrivateFieldGet(_mediaQuery, this).removeEventListener('change', _classPrivateFieldGet(_mediaQueryListener, this));
  }
}
function _createView() {
  if (_classPrivateFieldGet(_view, this)) {
    _classPrivateFieldGet(_view, this).destroy();
    _classPrivateFieldGet(_container, this).removeChild(_classPrivateFieldGet(_view, this).element);
  }
  _classPrivateFieldSet(_view, this, new View({
    container: this,
    onExpand: () => _assertClassBrand(_Paginator_brand, this, _scrollToAnchor).call(this, _classPrivateFieldGet(_anchor, this))
  }));
  _classPrivateFieldGet(_container, this).append(_classPrivateFieldGet(_view, this).element);
  return _classPrivateFieldGet(_view, this);
}
function _beforeRender({
  vertical,
  rtl,
  background
}) {
  _classPrivateFieldSet(_vertical2, this, vertical);
  _classPrivateFieldSet(_rtl2, this, rtl);
  _classPrivateFieldGet(_top, this).classList.toggle('vertical', vertical);

  // set background to `doc` background
  // this is needed because the iframe does not fill the whole element
  _classPrivateFieldGet(_background, this).style.background = background;
  const {
    width,
    height
  } = _classPrivateFieldGet(_container, this).getBoundingClientRect();
  const size = vertical ? height : width;
  const style = getComputedStyle(_classPrivateFieldGet(_top, this));
  const maxInlineSize = parseFloat(style.getPropertyValue('--_max-inline-size'));
  const maxColumnCount = parseInt(style.getPropertyValue('--_max-column-count-spread'));
  const margin = parseFloat(style.getPropertyValue('--_margin'));
  _classPrivateFieldSet(_margin, this, margin);
  const g = parseFloat(style.getPropertyValue('--_gap')) / 100;
  // The gap will be a percentage of the #container, not the whole view.
  // This means the outer padding will be bigger than the column gap. Let
  // `a` be the gap percentage. The actual percentage for the column gap
  // will be (1 - a) * a. Let us call this `b`.
  //
  // To make them the same, we start by shrinking the outer padding
  // setting to `b`, but keep the column gap setting the same at `a`. Then
  // the actual size for the column gap will be (1 - b) * a. Repeating the
  // process again and again, we get the sequence
  //     x₁ = (1 - b) * a
  //     x₂ = (1 - x₁) * a
  //     ...
  // which converges to x = (1 - x) * a. Solving for x, x = a / (1 + a).
  // So to make the spacing even, we must shrink the outer padding with
  //     f(x) = x / (1 + x).
  // But we want to keep the outer padding, and make the inner gap bigger.
  // So we apply the inverse, f⁻¹ = -x / (x - 1) to the column gap.
  const gap = -g / (g - 1) * size;
  const flow = this.getAttribute('flow');
  if (flow === 'scrolled') {
    // FIXME: vertical-rl only, not -lr
    this.setAttribute('dir', vertical ? 'rtl' : 'ltr');
    _classPrivateFieldGet(_top, this).style.padding = '0';
    const columnWidth = maxInlineSize;
    this.heads = null;
    this.feet = null;
    _classPrivateFieldGet(_header, this).replaceChildren();
    _classPrivateFieldGet(_footer, this).replaceChildren();
    return {
      flow,
      margin,
      gap,
      columnWidth
    };
  }
  const divisor = Math.min(maxColumnCount, Math.ceil(size / maxInlineSize));
  const columnWidth = size / divisor - gap;
  this.setAttribute('dir', rtl ? 'rtl' : 'ltr');
  const marginalDivisor = vertical ? Math.min(2, Math.ceil(width / maxInlineSize)) : divisor;
  const marginalStyle = {
    gridTemplateColumns: `repeat(${marginalDivisor}, 1fr)`,
    gap: `${gap}px`,
    direction: this.bookDir === 'rtl' ? 'rtl' : 'ltr'
  };
  Object.assign(_classPrivateFieldGet(_header, this).style, marginalStyle);
  Object.assign(_classPrivateFieldGet(_footer, this).style, marginalStyle);
  const heads = makeMarginals(marginalDivisor, 'head');
  const feet = makeMarginals(marginalDivisor, 'foot');
  this.heads = heads.map(el => el.children[0]);
  this.feet = feet.map(el => el.children[0]);
  _classPrivateFieldGet(_header, this).replaceChildren(...heads);
  _classPrivateFieldGet(_footer, this).replaceChildren(...feet);
  return {
    height,
    width,
    margin,
    gap,
    columnWidth
  };
}
function _onTouchStart(e) {
  const touch = e.changedTouches[0];
  _classPrivateFieldSet(_touchState, this, {
    x: touch === null || touch === void 0 ? void 0 : touch.screenX,
    y: touch === null || touch === void 0 ? void 0 : touch.screenY,
    t: e.timeStamp,
    vx: 0,
    xy: 0
  });
}
function _onTouchMove(e) {
  const state = _classPrivateFieldGet(_touchState, this);
  if (state.pinched) return;
  state.pinched = globalThis.visualViewport.scale > 1;
  if (this.scrolled || state.pinched) return;
  if (e.touches.length > 1) {
    if (_classPrivateFieldGet(_touchScrolled, this)) e.preventDefault();
    return;
  }
  e.preventDefault();
  const touch = e.changedTouches[0];
  const x = touch.screenX,
    y = touch.screenY;
  const dx = state.x - x,
    dy = state.y - y;
  const dt = e.timeStamp - state.t;
  state.x = x;
  state.y = y;
  state.t = e.timeStamp;
  state.vx = dx / dt;
  state.vy = dy / dt;
  _classPrivateFieldSet(_touchScrolled, this, true);
  this.scrollBy(dx, dy);
}
function _onTouchEnd() {
  _classPrivateFieldSet(_touchScrolled, this, false);
  if (this.scrolled) return;

  // XXX: Firefox seems to report scale as 1... sometimes...?
  // at this point I'm basically throwing `requestAnimationFrame` at
  // anything that doesn't work
  requestAnimationFrame(() => {
    if (globalThis.visualViewport.scale === 1) this.snap(_classPrivateFieldGet(_touchState, this).vx, _classPrivateFieldGet(_touchState, this).vy);
  });
}
// allows one to process rects as if they were LTR and horizontal
function _getRectMapper() {
  if (this.scrolled) {
    const size = this.viewSize;
    const margin = _classPrivateFieldGet(_margin, this);
    return _classPrivateFieldGet(_vertical2, this) ? ({
      left,
      right
    }) => ({
      left: size - right - margin,
      right: size - left - margin
    }) : ({
      top,
      bottom
    }) => ({
      left: top + margin,
      right: bottom + margin
    });
  }
  const pxSize = this.pages * this.size;
  return _classPrivateFieldGet(_rtl2, this) ? ({
    left,
    right
  }) => ({
    left: pxSize - right,
    right: pxSize - left
  }) : _classPrivateFieldGet(_vertical2, this) ? ({
    top,
    bottom
  }) => ({
    left: top,
    right: bottom
  }) : f => f;
}
async function _scrollToRect(rect, reason) {
  if (this.scrolled) {
    const offset = _assertClassBrand(_Paginator_brand, this, _getRectMapper).call(this)(rect).left - _classPrivateFieldGet(_margin, this);
    return _assertClassBrand(_Paginator_brand, this, _scrollTo).call(this, offset, reason);
  }
  const offset = _assertClassBrand(_Paginator_brand, this, _getRectMapper).call(this)(rect).left;
  return _assertClassBrand(_Paginator_brand, this, _scrollToPage).call(this, Math.floor(offset / this.size) + (_classPrivateFieldGet(_rtl2, this) ? -1 : 1), reason);
}
async function _scrollTo(offset, reason, smooth) {
  const element = _classPrivateFieldGet(_container, this);
  const {
    scrollProp,
    size
  } = this;
  if (element[scrollProp] === offset) {
    _classPrivateFieldSet(_scrollBounds, this, [offset, this.atStart ? 0 : size, this.atEnd ? 0 : size]);
    _assertClassBrand(_Paginator_brand, this, _afterScroll).call(this, reason);
    return;
  }
  // FIXME: vertical-rl only, not -lr
  if (this.scrolled && _classPrivateFieldGet(_vertical2, this)) offset = -offset;
  if ((reason === 'snap' || smooth) && this.hasAttribute('animated')) return animate(element[scrollProp], offset, 300, easeOutQuad, x => element[scrollProp] = x).then(() => {
    _classPrivateFieldSet(_scrollBounds, this, [offset, this.atStart ? 0 : size, this.atEnd ? 0 : size]);
    _assertClassBrand(_Paginator_brand, this, _afterScroll).call(this, reason);
  });else {
    element[scrollProp] = offset;
    _classPrivateFieldSet(_scrollBounds, this, [offset, this.atStart ? 0 : size, this.atEnd ? 0 : size]);
    _assertClassBrand(_Paginator_brand, this, _afterScroll).call(this, reason);
  }
}
async function _scrollToPage(page, reason, smooth) {
  const offset = this.size * (_classPrivateFieldGet(_rtl2, this) ? -page : page);
  return _assertClassBrand(_Paginator_brand, this, _scrollTo).call(this, offset, reason, smooth);
}
async function _scrollToAnchor(anchor, reason = 'anchor') {
  var _uncollapse, _uncollapse$getClient;
  _classPrivateFieldSet(_anchor, this, anchor);
  const rects = (_uncollapse = uncollapse(anchor)) === null || _uncollapse === void 0 || (_uncollapse$getClient = _uncollapse.getClientRects) === null || _uncollapse$getClient === void 0 ? void 0 : _uncollapse$getClient.call(_uncollapse);
  // if anchor is an element or a range
  if (rects) {
    // when the start of the range is immediately after a hyphen in the
    // previous column, there is an extra zero width rect in that column
    const rect = Array.from(rects).find(r => r.width > 0 && r.height > 0) || rects[0];
    if (!rect) return;
    await _assertClassBrand(_Paginator_brand, this, _scrollToRect).call(this, rect, reason);
    return;
  }
  // if anchor is a fraction
  if (this.scrolled) {
    await _assertClassBrand(_Paginator_brand, this, _scrollTo).call(this, anchor * this.viewSize, reason);
    return;
  }
  const {
    pages
  } = this;
  if (!pages) return;
  const textPages = pages - 2;
  const newPage = Math.round(anchor * (textPages - 1));
  await _assertClassBrand(_Paginator_brand, this, _scrollToPage).call(this, newPage + 1, reason);
}
function _getVisibleRange() {
  if (this.scrolled) return getVisibleRange(_classPrivateFieldGet(_view, this).document, this.start + _classPrivateFieldGet(_margin, this), this.end - _classPrivateFieldGet(_margin, this), _assertClassBrand(_Paginator_brand, this, _getRectMapper).call(this));
  const size = _classPrivateFieldGet(_rtl2, this) ? -this.size : this.size;
  return getVisibleRange(_classPrivateFieldGet(_view, this).document, this.start - size, this.end - size, _assertClassBrand(_Paginator_brand, this, _getRectMapper).call(this));
}
function _afterScroll(reason) {
  const range = _assertClassBrand(_Paginator_brand, this, _getVisibleRange).call(this);
  _classPrivateFieldSet(_lastVisibleRange, this, range);
  // don't set new anchor if relocation was to scroll to anchor
  if (reason !== 'selection' && reason !== 'navigation' && reason !== 'anchor') _classPrivateFieldSet(_anchor, this, range);else _classPrivateFieldSet(_justAnchored, this, true);
  const index = _classPrivateFieldGet(_index, this);
  const detail = {
    reason,
    range,
    index
  };
  if (this.scrolled) detail.fraction = this.start / this.viewSize;else if (this.pages > 0) {
    const {
      page,
      pages
    } = this;
    _classPrivateFieldGet(_header, this).style.visibility = page > 1 ? 'visible' : 'hidden';
    detail.fraction = (page - 1) / (pages - 2);
    detail.size = 1 / (pages - 2);
  }
  this.dispatchEvent(new CustomEvent('relocate', {
    detail
  }));
}
async function _display(promise) {
  var _classPrivateFieldGet4, _ref;
  const {
    index,
    src,
    anchor,
    onLoad,
    select
  } = await promise;
  _classPrivateFieldSet(_index, this, index);
  const hasFocus = (_classPrivateFieldGet4 = _classPrivateFieldGet(_view, this)) === null || _classPrivateFieldGet4 === void 0 || (_classPrivateFieldGet4 = _classPrivateFieldGet4.document) === null || _classPrivateFieldGet4 === void 0 ? void 0 : _classPrivateFieldGet4.hasFocus();
  if (src) {
    const view = _assertClassBrand(_Paginator_brand, this, _createView).call(this);
    const afterLoad = doc => {
      if (doc.head) {
        const $styleBefore = doc.createElement('style');
        doc.head.prepend($styleBefore);
        const $style = doc.createElement('style');
        doc.head.append($style);
        _classPrivateFieldGet(_styleMap, this).set(doc, [$styleBefore, $style]);
      }
      onLoad === null || onLoad === void 0 || onLoad({
        doc,
        index
      });
    };
    const beforeRender = _assertClassBrand(_Paginator_brand, this, _beforeRender).bind(this);
    await view.load(src, afterLoad, beforeRender);
    this.dispatchEvent(new CustomEvent('create-overlayer', {
      detail: {
        doc: view.document,
        index,
        attach: overlayer => view.overlayer = overlayer
      }
    }));
    _classPrivateFieldSet(_view, this, view);
  }
  await this.scrollToAnchor((_ref = typeof anchor === 'function' ? anchor(_classPrivateFieldGet(_view, this).document) : anchor) !== null && _ref !== void 0 ? _ref : 0, select);
  if (hasFocus) this.focusView();
}
function _canGoToIndex(index) {
  return index >= 0 && index <= this.sections.length - 1;
}
async function _goTo({
  index,
  anchor,
  select
}) {
  if (index === _classPrivateFieldGet(_index, this)) await _assertClassBrand(_Paginator_brand, this, _display).call(this, {
    index,
    anchor,
    select
  });else {
    const oldIndex = _classPrivateFieldGet(_index, this);
    const onLoad = detail => {
      var _this$sections$oldInd, _this$sections$oldInd2;
      (_this$sections$oldInd = this.sections[oldIndex]) === null || _this$sections$oldInd === void 0 || (_this$sections$oldInd2 = _this$sections$oldInd.unload) === null || _this$sections$oldInd2 === void 0 || _this$sections$oldInd2.call(_this$sections$oldInd);
      this.setStyles(_classPrivateFieldGet(_styles, this));
      this.dispatchEvent(new CustomEvent('load', {
        detail
      }));
    };
    await _assertClassBrand(_Paginator_brand, this, _display).call(this, Promise.resolve(this.sections[index].load()).then(src => ({
      index,
      src,
      anchor,
      onLoad,
      select
    })).catch(e => {
      console.warn(e);
      console.warn(new Error(`Failed to load section ${index}`));
      return {};
    }));
  }
}
function _scrollPrev(distance) {
  if (!_classPrivateFieldGet(_view, this)) return true;
  if (this.scrolled) {
    if (this.start > 0) return _assertClassBrand(_Paginator_brand, this, _scrollTo).call(this, Math.max(0, this.start - (distance !== null && distance !== void 0 ? distance : this.size)), null, true);
    return true;
  }
  if (this.atStart) return;
  const page = this.page - 1;
  return _assertClassBrand(_Paginator_brand, this, _scrollToPage).call(this, page, 'page', true).then(() => page <= 0);
}
function _scrollNext(distance) {
  if (!_classPrivateFieldGet(_view, this)) return true;
  if (this.scrolled) {
    if (this.viewSize - this.end > 2) return _assertClassBrand(_Paginator_brand, this, _scrollTo).call(this, Math.min(this.viewSize, distance ? this.start + distance : this.end), null, true);
    return true;
  }
  if (this.atEnd) return;
  const page = this.page + 1;
  const pages = this.pages;
  return _assertClassBrand(_Paginator_brand, this, _scrollToPage).call(this, page, 'page', true).then(() => page >= pages - 1);
}
function _adjacentIndex(dir) {
  for (let index = _classPrivateFieldGet(_index, this) + dir; _assertClassBrand(_Paginator_brand, this, _canGoToIndex).call(this, index); index += dir) {
    var _this$sections$index;
    if (((_this$sections$index = this.sections[index]) === null || _this$sections$index === void 0 ? void 0 : _this$sections$index.linear) !== 'no') return index;
  }
}
async function _turnPage(dir, distance) {
  if (_classPrivateFieldGet(_locked, this)) return;
  _classPrivateFieldSet(_locked, this, true);
  const prev = dir === -1;
  const shouldGo = await (prev ? _assertClassBrand(_Paginator_brand, this, _scrollPrev).call(this, distance) : _assertClassBrand(_Paginator_brand, this, _scrollNext).call(this, distance));
  if (shouldGo) await _assertClassBrand(_Paginator_brand, this, _goTo).call(this, {
    index: _assertClassBrand(_Paginator_brand, this, _adjacentIndex).call(this, dir),
    anchor: prev ? () => 1 : () => 0
  });
  if (shouldGo || !this.hasAttribute('animated')) await wait(100);
  _classPrivateFieldSet(_locked, this, false);
}
_defineProperty(Paginator, "observedAttributes", ['flow', 'gap', 'margin', 'max-inline-size', 'max-block-size', 'max-column-count']);
customElements.define('foliate-paginator', Paginator);