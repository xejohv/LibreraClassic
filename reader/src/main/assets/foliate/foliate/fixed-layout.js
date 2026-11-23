function _classPrivateMethodInitSpec(e, a) { _checkPrivateRedeclaration(e, a), a.add(e); }
function _classPrivateFieldInitSpec(e, t, a) { _checkPrivateRedeclaration(e, t), t.set(e, a); }
function _checkPrivateRedeclaration(e, t) { if (t.has(e)) throw new TypeError("Cannot initialize the same private elements twice on an object"); }
function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }
function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }
function _toPrimitive(t, e) { if ("object" != typeof t || !t) return t; var r; if ("undefined" != typeof Symbol && void 0 !== (r = t[Symbol.toPrimitive])) { var i = r.call(t, e || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === e ? String : Number)(t); }
function _classPrivateFieldSet(s, a, r) { return s.set(_assertClassBrand(s, a), r), r; }
function _classPrivateFieldGet(s, a) { return s.get(_assertClassBrand(s, a)); }
function _assertClassBrand(e, t, n) { if ("function" == typeof e ? e === t : e.has(t)) return arguments.length < 3 ? t : n; throw new TypeError("Private element is not present on this object"); }
const parseViewport = str => {
  var _str$split;
  return str === null || str === void 0 || (_str$split = str.split(/[,;\s]/) // NOTE: technically, only the comma is valid
  ) === null || _str$split === void 0 || (_str$split = _str$split.filter(x => x)) === null || _str$split === void 0 ? void 0 : _str$split.map(x => x.split('=').map(x => x.trim()));
};
const getViewport = (doc, viewport) => {
  var _doc$querySelector;
  // use `viewBox` for SVG
  if (doc.documentElement.localName === 'svg') {
    var _doc$documentElement$, _doc$documentElement$2;
    const [,, width, height] = (_doc$documentElement$ = (_doc$documentElement$2 = doc.documentElement.getAttribute('viewBox')) === null || _doc$documentElement$2 === void 0 ? void 0 : _doc$documentElement$2.split(/\s/)) !== null && _doc$documentElement$ !== void 0 ? _doc$documentElement$ : [];
    return {
      width,
      height
    };
  }

  // get `viewport` `meta` element
  const meta = parseViewport((_doc$querySelector = doc.querySelector('meta[name="viewport"]')) === null || _doc$querySelector === void 0 ? void 0 : _doc$querySelector.getAttribute('content'));
  if (meta) return Object.fromEntries(meta);

  // fallback to book's viewport
  if (typeof viewport === 'string') return parseViewport(viewport);
  if (viewport !== null && viewport !== void 0 && viewport.width && viewport.height) return viewport;

  // if no viewport (possibly with image directly in spine), get image size
  const img = doc.querySelector('img');
  if (img) return {
    width: img.naturalWidth,
    height: img.naturalHeight
  };

  // just show *something*, i guess...
  console.warn(new Error('Missing viewport properties'));
  return {
    width: 1000,
    height: 2000
  };
};
var _root = /*#__PURE__*/new WeakMap();
var _observer = /*#__PURE__*/new WeakMap();
var _spreads = /*#__PURE__*/new WeakMap();
var _index = /*#__PURE__*/new WeakMap();
var _portrait = /*#__PURE__*/new WeakMap();
var _left = /*#__PURE__*/new WeakMap();
var _right = /*#__PURE__*/new WeakMap();
var _center = /*#__PURE__*/new WeakMap();
var _side = /*#__PURE__*/new WeakMap();
var _zoom = /*#__PURE__*/new WeakMap();
var _FixedLayout_brand = /*#__PURE__*/new WeakSet();
export class FixedLayout extends HTMLElement {
  constructor() {
    super();
    _classPrivateMethodInitSpec(this, _FixedLayout_brand);
    _classPrivateFieldInitSpec(this, _root, this.attachShadow({
      mode: 'closed'
    }));
    _classPrivateFieldInitSpec(this, _observer, new ResizeObserver(() => _assertClassBrand(_FixedLayout_brand, this, _render).call(this)));
    _classPrivateFieldInitSpec(this, _spreads, void 0);
    _classPrivateFieldInitSpec(this, _index, -1);
    _defineProperty(this, "defaultViewport", void 0);
    _defineProperty(this, "spread", void 0);
    _classPrivateFieldInitSpec(this, _portrait, false);
    _classPrivateFieldInitSpec(this, _left, void 0);
    _classPrivateFieldInitSpec(this, _right, void 0);
    _classPrivateFieldInitSpec(this, _center, void 0);
    _classPrivateFieldInitSpec(this, _side, void 0);
    _classPrivateFieldInitSpec(this, _zoom, void 0);
    const sheet = new CSSStyleSheet();
    _classPrivateFieldGet(_root, this).adoptedStyleSheets = [sheet];
    sheet.replaceSync(`:host {
            width: 100%;
            height: 100%;
            display: flex;
            justify-content: center;
            align-items: center;
            overflow: auto;
        }`);
    _classPrivateFieldGet(_observer, this).observe(this);
  }
  attributeChangedCallback(name, _, value) {
    switch (name) {
      case 'zoom':
        _classPrivateFieldSet(_zoom, this, value !== 'fit-width' && value !== 'fit-page' ? parseFloat(value) : value);
        _assertClassBrand(_FixedLayout_brand, this, _render).call(this);
        break;
    }
  }
  open(book) {
    this.book = book;
    const {
      rendition
    } = book;
    this.spread = rendition === null || rendition === void 0 ? void 0 : rendition.spread;
    this.defaultViewport = rendition === null || rendition === void 0 ? void 0 : rendition.viewport;
    const rtl = book.dir === 'rtl';
    const ltr = !rtl;
    this.rtl = rtl;
    if ((rendition === null || rendition === void 0 ? void 0 : rendition.spread) === 'none') _classPrivateFieldSet(_spreads, this, book.sections.map(section => ({
      center: section
    })));else _classPrivateFieldSet(_spreads, this, book.sections.reduce((arr, section, i) => {
      const last = arr[arr.length - 1];
      const {
        pageSpread
      } = section;
      const newSpread = () => {
        const spread = {};
        arr.push(spread);
        return spread;
      };
      if (pageSpread === 'center') {
        const spread = last.left || last.right ? newSpread() : last;
        spread.center = section;
      } else if (pageSpread === 'left') {
        const spread = last.center || last.left || ltr && i ? newSpread() : last;
        spread.left = section;
      } else if (pageSpread === 'right') {
        const spread = last.center || last.right || rtl && i ? newSpread() : last;
        spread.right = section;
      } else if (ltr) {
        if (last.center || last.right) newSpread().left = section;else if (last.left || !i) last.right = section;else last.left = section;
      } else {
        if (last.center || last.left) newSpread().right = section;else if (last.right || !i) last.left = section;else last.right = section;
      }
      return arr;
    }, [{}]));
  }
  get index() {
    var _spread$center, _spread$left, _spread$right;
    const spread = _classPrivateFieldGet(_spreads, this)[_classPrivateFieldGet(_index, this)];
    const section = (_spread$center = spread === null || spread === void 0 ? void 0 : spread.center) !== null && _spread$center !== void 0 ? _spread$center : _classPrivateFieldGet(_side, this) === 'left' ? (_spread$left = spread.left) !== null && _spread$left !== void 0 ? _spread$left : spread.right : (_spread$right = spread.right) !== null && _spread$right !== void 0 ? _spread$right : spread.left;
    return this.book.sections.indexOf(section);
  }
  getSpreadOf(section) {
    const spreads = _classPrivateFieldGet(_spreads, this);
    for (let index = 0; index < spreads.length; index++) {
      const {
        left,
        right,
        center
      } = spreads[index];
      if (left === section) return {
        index,
        side: 'left'
      };
      if (right === section) return {
        index,
        side: 'right'
      };
      if (center === section) return {
        index,
        side: 'center'
      };
    }
  }
  async goToSpread(index, side, reason) {
    if (index < 0 || index > _classPrivateFieldGet(_spreads, this).length - 1) return;
    if (index === _classPrivateFieldGet(_index, this)) {
      _assertClassBrand(_FixedLayout_brand, this, _render).call(this, side);
      return;
    }
    _classPrivateFieldSet(_index, this, index);
    const spread = _classPrivateFieldGet(_spreads, this)[index];
    if (spread.center) {
      var _spread$center2, _spread$center2$load;
      const index = this.book.sections.indexOf(spread.center);
      const src = await ((_spread$center2 = spread.center) === null || _spread$center2 === void 0 || (_spread$center2$load = _spread$center2.load) === null || _spread$center2$load === void 0 ? void 0 : _spread$center2$load.call(_spread$center2));
      await _assertClassBrand(_FixedLayout_brand, this, _showSpread).call(this, {
        center: {
          index,
          src
        }
      });
    } else {
      var _spread$left2, _spread$left2$load, _spread$right2, _spread$right2$load;
      const indexL = this.book.sections.indexOf(spread.left);
      const indexR = this.book.sections.indexOf(spread.right);
      const srcL = await ((_spread$left2 = spread.left) === null || _spread$left2 === void 0 || (_spread$left2$load = _spread$left2.load) === null || _spread$left2$load === void 0 ? void 0 : _spread$left2$load.call(_spread$left2));
      const srcR = await ((_spread$right2 = spread.right) === null || _spread$right2 === void 0 || (_spread$right2$load = _spread$right2.load) === null || _spread$right2$load === void 0 ? void 0 : _spread$right2$load.call(_spread$right2));
      const left = {
        index: indexL,
        src: srcL
      };
      const right = {
        index: indexR,
        src: srcR
      };
      await _assertClassBrand(_FixedLayout_brand, this, _showSpread).call(this, {
        left,
        right,
        side
      });
    }
    _assertClassBrand(_FixedLayout_brand, this, _reportLocation).call(this, reason);
  }
  async select(target) {
    await this.goTo(target);
    // TODO
  }
  async goTo(target) {
    const {
      book
    } = this;
    const resolved = await target;
    const section = book.sections[resolved.index];
    if (!section) return;
    const {
      index,
      side
    } = this.getSpreadOf(section);
    await this.goToSpread(index, side);
  }
  async next() {
    const s = this.rtl ? _assertClassBrand(_FixedLayout_brand, this, _goLeft).call(this) : _assertClassBrand(_FixedLayout_brand, this, _goRight).call(this);
    if (!s) return this.goToSpread(_classPrivateFieldGet(_index, this) + 1, this.rtl ? 'right' : 'left', 'page');
  }
  async prev() {
    const s = this.rtl ? _assertClassBrand(_FixedLayout_brand, this, _goRight).call(this) : _assertClassBrand(_FixedLayout_brand, this, _goLeft).call(this);
    if (!s) return this.goToSpread(_classPrivateFieldGet(_index, this) - 1, this.rtl ? 'left' : 'right', 'page');
  }
  getContents() {
    return Array.from(_classPrivateFieldGet(_root, this).querySelectorAll('iframe'), frame => ({
      doc: frame.contentDocument
      // TODO: index, overlayer
    }));
  }
  destroy() {
    _classPrivateFieldGet(_observer, this).unobserve(this);
  }
}
async function _createFrame({
  index,
  src: srcOption
}) {
  const srcOptionIsString = typeof srcOption === 'string';
  const src = srcOptionIsString ? srcOption : srcOption === null || srcOption === void 0 ? void 0 : srcOption.src;
  const onZoom = srcOptionIsString ? null : srcOption === null || srcOption === void 0 ? void 0 : srcOption.onZoom;
  const element = document.createElement('div');
  element.setAttribute('dir', 'ltr');
  const iframe = document.createElement('iframe');
  element.append(iframe);
  Object.assign(iframe.style, {
    border: '0',
    display: 'none',
    overflow: 'hidden'
  });
  // `allow-scripts` is needed for events because of WebKit bug
  // https://bugs.webkit.org/show_bug.cgi?id=218086
  iframe.setAttribute('sandbox', 'allow-same-origin allow-scripts');
  iframe.setAttribute('scrolling', 'no');
  iframe.setAttribute('part', 'filter');
  _classPrivateFieldGet(_root, this).append(element);
  if (!src) return {
    blank: true,
    element,
    iframe
  };
  return new Promise(resolve => {
    iframe.addEventListener('load', () => {
      const doc = iframe.contentDocument;
      this.dispatchEvent(new CustomEvent('load', {
        detail: {
          doc,
          index
        }
      }));
      const {
        width,
        height
      } = getViewport(doc, this.defaultViewport);
      resolve({
        element,
        iframe,
        width: parseFloat(width),
        height: parseFloat(height),
        onZoom
      });
    }, {
      once: true
    });
    iframe.src = src;
  });
}
function _render(side = _classPrivateFieldGet(_side, this)) {
  var _classPrivateFieldGet2, _ref, _classPrivateFieldGet3, _ref2, _left$width, _ref3, _left$height, _target$width, _left$width2, _right$width, _target$width2, _target$height, _left$width3, _right$width2, _left$height2, _right$height;
  if (!side) return;
  const left = (_classPrivateFieldGet2 = _classPrivateFieldGet(_left, this)) !== null && _classPrivateFieldGet2 !== void 0 ? _classPrivateFieldGet2 : {};
  const right = (_ref = (_classPrivateFieldGet3 = _classPrivateFieldGet(_center, this)) !== null && _classPrivateFieldGet3 !== void 0 ? _classPrivateFieldGet3 : _classPrivateFieldGet(_right, this)) !== null && _ref !== void 0 ? _ref : {};
  const target = side === 'left' ? left : right;
  const {
    width,
    height
  } = this.getBoundingClientRect();
  const portrait = this.spread !== 'both' && this.spread !== 'portrait' && height > width;
  _classPrivateFieldSet(_portrait, this, portrait);
  const blankWidth = (_ref2 = (_left$width = left.width) !== null && _left$width !== void 0 ? _left$width : right.width) !== null && _ref2 !== void 0 ? _ref2 : 0;
  const blankHeight = (_ref3 = (_left$height = left.height) !== null && _left$height !== void 0 ? _left$height : right.height) !== null && _ref3 !== void 0 ? _ref3 : 0;
  const scale = typeof _classPrivateFieldGet(_zoom, this) === 'number' && !isNaN(_classPrivateFieldGet(_zoom, this)) ? _classPrivateFieldGet(_zoom, this) : (_classPrivateFieldGet(_zoom, this) === 'fit-width' ? portrait || _classPrivateFieldGet(_center, this) ? width / ((_target$width = target.width) !== null && _target$width !== void 0 ? _target$width : blankWidth) : width / (((_left$width2 = left.width) !== null && _left$width2 !== void 0 ? _left$width2 : blankWidth) + ((_right$width = right.width) !== null && _right$width !== void 0 ? _right$width : blankWidth)) : portrait || _classPrivateFieldGet(_center, this) ? Math.min(width / ((_target$width2 = target.width) !== null && _target$width2 !== void 0 ? _target$width2 : blankWidth), height / ((_target$height = target.height) !== null && _target$height !== void 0 ? _target$height : blankHeight)) : Math.min(width / (((_left$width3 = left.width) !== null && _left$width3 !== void 0 ? _left$width3 : blankWidth) + ((_right$width2 = right.width) !== null && _right$width2 !== void 0 ? _right$width2 : blankWidth)), height / Math.max((_left$height2 = left.height) !== null && _left$height2 !== void 0 ? _left$height2 : blankHeight, (_right$height = right.height) !== null && _right$height !== void 0 ? _right$height : blankHeight))) || 1;
  const transform = frame => {
    let {
      element,
      iframe,
      width,
      height,
      blank,
      onZoom
    } = frame;
    if (!iframe) return;
    if (onZoom) onZoom({
      doc: frame.iframe.contentDocument,
      scale
    });
    const iframeScale = onZoom ? scale : 1;
    Object.assign(iframe.style, {
      width: `${width * iframeScale}px`,
      height: `${height * iframeScale}px`,
      transform: onZoom ? 'none' : `scale(${scale})`,
      transformOrigin: 'top left',
      display: blank ? 'none' : 'block'
    });
    Object.assign(element.style, {
      width: `${(width !== null && width !== void 0 ? width : blankWidth) * scale}px`,
      height: `${(height !== null && height !== void 0 ? height : blankHeight) * scale}px`,
      overflow: 'hidden',
      display: 'block',
      flexShrink: '0',
      marginBlock: 'auto'
    });
    if (portrait && frame !== target) {
      element.style.display = 'none';
    }
  };
  if (_classPrivateFieldGet(_center, this)) {
    transform(_classPrivateFieldGet(_center, this));
  } else {
    transform(left);
    transform(right);
  }
}
async function _showSpread({
  left,
  right,
  center,
  side
}) {
  _classPrivateFieldGet(_root, this).replaceChildren();
  _classPrivateFieldSet(_left, this, null);
  _classPrivateFieldSet(_right, this, null);
  _classPrivateFieldSet(_center, this, null);
  if (center) {
    _classPrivateFieldSet(_center, this, await _assertClassBrand(_FixedLayout_brand, this, _createFrame).call(this, center));
    _classPrivateFieldSet(_side, this, 'center');
    _assertClassBrand(_FixedLayout_brand, this, _render).call(this);
  } else {
    _classPrivateFieldSet(_left, this, await _assertClassBrand(_FixedLayout_brand, this, _createFrame).call(this, left));
    _classPrivateFieldSet(_right, this, await _assertClassBrand(_FixedLayout_brand, this, _createFrame).call(this, right));
    _classPrivateFieldSet(_side, this, _classPrivateFieldGet(_left, this).blank ? 'right' : _classPrivateFieldGet(_right, this).blank ? 'left' : side);
    _assertClassBrand(_FixedLayout_brand, this, _render).call(this);
  }
}
function _goLeft() {
  var _classPrivateFieldGet4, _classPrivateFieldGet5;
  if (_classPrivateFieldGet(_center, this) || (_classPrivateFieldGet4 = _classPrivateFieldGet(_left, this)) !== null && _classPrivateFieldGet4 !== void 0 && _classPrivateFieldGet4.blank) return;
  if (_classPrivateFieldGet(_portrait, this) && ((_classPrivateFieldGet5 = _classPrivateFieldGet(_left, this)) === null || _classPrivateFieldGet5 === void 0 || (_classPrivateFieldGet5 = _classPrivateFieldGet5.element) === null || _classPrivateFieldGet5 === void 0 || (_classPrivateFieldGet5 = _classPrivateFieldGet5.style) === null || _classPrivateFieldGet5 === void 0 ? void 0 : _classPrivateFieldGet5.display) === 'none') {
    _classPrivateFieldSet(_side, this, 'left');
    _assertClassBrand(_FixedLayout_brand, this, _render).call(this);
    _assertClassBrand(_FixedLayout_brand, this, _reportLocation).call(this, 'page');
    return true;
  }
}
function _goRight() {
  var _classPrivateFieldGet6, _classPrivateFieldGet7;
  if (_classPrivateFieldGet(_center, this) || (_classPrivateFieldGet6 = _classPrivateFieldGet(_right, this)) !== null && _classPrivateFieldGet6 !== void 0 && _classPrivateFieldGet6.blank) return;
  if (_classPrivateFieldGet(_portrait, this) && ((_classPrivateFieldGet7 = _classPrivateFieldGet(_right, this)) === null || _classPrivateFieldGet7 === void 0 || (_classPrivateFieldGet7 = _classPrivateFieldGet7.element) === null || _classPrivateFieldGet7 === void 0 || (_classPrivateFieldGet7 = _classPrivateFieldGet7.style) === null || _classPrivateFieldGet7 === void 0 ? void 0 : _classPrivateFieldGet7.display) === 'none') {
    _classPrivateFieldSet(_side, this, 'right');
    _assertClassBrand(_FixedLayout_brand, this, _render).call(this);
    _assertClassBrand(_FixedLayout_brand, this, _reportLocation).call(this, 'page');
    return true;
  }
}
function _reportLocation(reason) {
  this.dispatchEvent(new CustomEvent('relocate', {
    detail: {
      reason,
      range: null,
      index: this.index,
      fraction: 0,
      size: 1
    }
  }));
}
_defineProperty(FixedLayout, "observedAttributes", ['zoom']);
customElements.define('foliate-fxl', FixedLayout);