function _classPrivateMethodInitSpec(e, a) { _checkPrivateRedeclaration(e, a), a.add(e); }
function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }
function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }
function _toPrimitive(t, e) { if ("object" != typeof t || !t) return t; var r; if ("undefined" != typeof Symbol && void 0 !== (r = t[Symbol.toPrimitive])) { var i = r.call(t, e || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === e ? String : Number)(t); }
function _classPrivateFieldInitSpec(e, t, a) { _checkPrivateRedeclaration(e, t), t.set(e, a); }
function _checkPrivateRedeclaration(e, t) { if (t.has(e)) throw new TypeError("Cannot initialize the same private elements twice on an object"); }
function _classPrivateFieldGet(s, a) { return s.get(_assertClassBrand(s, a)); }
function _classPrivateFieldSet(s, a, r) { return s.set(_assertClassBrand(s, a), r), r; }
function _assertClassBrand(e, t, n) { if ("function" == typeof e ? e === t : e.has(t)) return arguments.length < 3 ? t : n; throw new TypeError("Private element is not present on this object"); }
import * as CFI from './epubcfi.js';
import { TOCProgress, SectionProgress } from './progress.js';
import { Overlayer } from './overlayer.js';
import { textWalker } from './text-walker.js';
const SEARCH_PREFIX = 'foliate-search:';
const isZip = async file => {
  const arr = new Uint8Array(await file.slice(0, 4).arrayBuffer());
  return arr[0] === 0x50 && arr[1] === 0x4b && arr[2] === 0x03 && arr[3] === 0x04;
};
const isPDF = async file => {
  const arr = new Uint8Array(await file.slice(0, 5).arrayBuffer());
  return arr[0] === 0x25 && arr[1] === 0x50 && arr[2] === 0x44 && arr[3] === 0x46 && arr[4] === 0x2d;
};
const isCBZ = ({
  name,
  type
}) => type === 'application/vnd.comicbook+zip' || name.endsWith('.cbz');
const isFB2 = ({
  name,
  type
}) => type === 'application/x-fictionbook+xml' || name.endsWith('.fb2');
const isFBZ = ({
  name,
  type
}) => type === 'application/x-zip-compressed-fb2' || name.endsWith('.fb2.zip') || name.endsWith('.fbz');
const makeZipLoader = async file => {
  const {
    configure,
    ZipReader,
    BlobReader,
    TextWriter,
    BlobWriter
  } = await import('./vendor/zip.js');
  configure({
    useWebWorkers: false
  });
  const reader = new ZipReader(new BlobReader(file));
  const entries = await reader.getEntries();
  const map = new Map(entries.map(entry => [entry.filename, entry]));
  const load = f => (name, ...args) => map.has(name) ? f(map.get(name), ...args) : null;
  const loadText = load(entry => entry.getData(new TextWriter()));
  const loadBlob = load((entry, type) => entry.getData(new BlobWriter(type)));
  const getSize = name => {
    var _map$get$uncompressed, _map$get;
    return (_map$get$uncompressed = (_map$get = map.get(name)) === null || _map$get === void 0 ? void 0 : _map$get.uncompressedSize) !== null && _map$get$uncompressed !== void 0 ? _map$get$uncompressed : 0;
  };
  return {
    entries,
    loadText,
    loadBlob,
    getSize
  };
};
const getFileEntries = async entry => entry.isFile ? entry : (await Promise.all(Array.from(await new Promise((resolve, reject) => entry.createReader().readEntries(entries => resolve(entries), error => reject(error))), getFileEntries))).flat();
const makeDirectoryLoader = async entry => {
  const entries = await getFileEntries(entry);
  const files = await Promise.all(entries.map(entry => new Promise((resolve, reject) => entry.file(file => resolve([file, entry.fullPath]), error => reject(error)))));
  const map = new Map(files.map(([file, path]) => [path.replace(entry.fullPath + '/', ''), file]));
  const decoder = new TextDecoder();
  const decode = x => x ? decoder.decode(x) : null;
  const getBuffer = name => {
    var _map$get$arrayBuffer, _map$get2;
    return (_map$get$arrayBuffer = (_map$get2 = map.get(name)) === null || _map$get2 === void 0 ? void 0 : _map$get2.arrayBuffer()) !== null && _map$get$arrayBuffer !== void 0 ? _map$get$arrayBuffer : null;
  };
  const loadText = async name => decode(await getBuffer(name));
  const loadBlob = name => map.get(name);
  const getSize = name => {
    var _map$get$size, _map$get3;
    return (_map$get$size = (_map$get3 = map.get(name)) === null || _map$get3 === void 0 ? void 0 : _map$get3.size) !== null && _map$get$size !== void 0 ? _map$get$size : 0;
  };
  return {
    loadText,
    loadBlob,
    getSize
  };
};
export class ResponseError extends Error {}
export class NotFoundError extends Error {}
export class UnsupportedTypeError extends Error {}
const fetchFile = async url => {
  const res = await fetch(url);
  if (!res.ok) throw new ResponseError(`${res.status} ${res.statusText}`, {
    cause: res
  });
  return new File([await res.blob()], new URL(res.url).pathname);
};
export const makeBook = async file => {
  if (typeof file === 'string') file = await fetchFile(file);
  let book;
  if (file.isDirectory) {
    const loader = await makeDirectoryLoader(file);
    const {
      EPUB
    } = await import('./epub.js');
    book = await new EPUB(loader).init();
  } else if (!file.size) throw new NotFoundError('File not found');
  else if (await isZip(file)) {
    const loader = await makeZipLoader(file);
    if (isCBZ(file)) {
      const {
        makeComicBook
      } = await import('./comic-book.js');
      book = makeComicBook(loader, file);
    } else if (isFBZ(file)) {
      const {
        makeFB2
      } = await import('./fb2.js');
      const {
        entries
      } = loader;
      const entry = entries.find(entry => entry.filename.endsWith('.fb2'));
      const blob = await loader.loadBlob((entry !== null && entry !== void 0 ? entry : entries[0]).filename);
      book = await makeFB2(blob);
    } else {
      const {
        EPUB
      } = await import('./epub.js');
      book = await new EPUB(loader).init();
    }
  } else if (await isPDF(file)) {
    const {
      makePDF
    } = await import('./pdf.js');
    book = await makePDF(file);
  } else {
    const {
      isMOBI,
      MOBI
    } = await import('./mobi.js');
    if (await isMOBI(file)) {
      const fflate = await import('./vendor/fflate.js');
      book = await new MOBI({
        unzlib: fflate.unzlibSync
      }).open(file);
    } else if (isFB2(file)) {
      const {
        makeFB2
      } = await import('./fb2.js');
      book = await makeFB2(file);
    }
  }
  if (!book) throw new UnsupportedTypeError('File type not supported');
  return book;
};
var _timeout = /*#__PURE__*/new WeakMap();
var _el = /*#__PURE__*/new WeakMap();
var _check = /*#__PURE__*/new WeakMap();
var _state = /*#__PURE__*/new WeakMap();
class CursorAutohider {
  constructor(el, check, state = {}) {
    _classPrivateFieldInitSpec(this, _timeout, void 0);
    _classPrivateFieldInitSpec(this, _el, void 0);
    _classPrivateFieldInitSpec(this, _check, void 0);
    _classPrivateFieldInitSpec(this, _state, void 0);
    _classPrivateFieldSet(_el, this, el);
    _classPrivateFieldSet(_check, this, check);
    _classPrivateFieldSet(_state, this, state);
    if (_classPrivateFieldGet(_state, this).hidden) this.hide();
    _classPrivateFieldGet(_el, this).addEventListener('mousemove', ({
      screenX,
      screenY
    }) => {
      // check if it actually moved
      if (screenX === _classPrivateFieldGet(_state, this).x && screenY === _classPrivateFieldGet(_state, this).y) return;
      _classPrivateFieldGet(_state, this).x = screenX, _classPrivateFieldGet(_state, this).y = screenY;
      this.show();
      if (_classPrivateFieldGet(_timeout, this)) clearTimeout(_classPrivateFieldGet(_timeout, this));
      if (check()) _classPrivateFieldSet(_timeout, this, setTimeout(this.hide.bind(this), 1000));
    }, false);
  }
  cloneFor(el) {
    return new CursorAutohider(el, _classPrivateFieldGet(_check, this), _classPrivateFieldGet(_state, this));
  }
  hide() {
    _classPrivateFieldGet(_el, this).style.cursor = 'none';
    _classPrivateFieldGet(_state, this).hidden = true;
  }
  show() {
    _classPrivateFieldGet(_el, this).style.removeProperty('cursor');
    _classPrivateFieldGet(_state, this).hidden = false;
  }
}
var _arr = /*#__PURE__*/new WeakMap();
var _index = /*#__PURE__*/new WeakMap();
class History extends EventTarget {
  constructor(...args) {
    super(...args);
    _classPrivateFieldInitSpec(this, _arr, []);
    _classPrivateFieldInitSpec(this, _index, -1);
  }
  pushState(x) {
    var _this$index;
    const last = _classPrivateFieldGet(_arr, this)[_classPrivateFieldGet(_index, this)];
    if (last === x || last !== null && last !== void 0 && last.fraction && last.fraction === x.fraction) return;
    _classPrivateFieldGet(_arr, this)[_classPrivateFieldSet(_index, this, (_this$index = _classPrivateFieldGet(_index, this), ++_this$index))] = x;
    _classPrivateFieldGet(_arr, this).length = _classPrivateFieldGet(_index, this) + 1;
    this.dispatchEvent(new Event('index-change'));
  }
  replaceState(x) {
    const index = _classPrivateFieldGet(_index, this);
    _classPrivateFieldGet(_arr, this)[index] = x;
  }
  back() {
    const index = _classPrivateFieldGet(_index, this);
    if (index <= 0) return;
    const detail = {
      state: _classPrivateFieldGet(_arr, this)[index - 1]
    };
    _classPrivateFieldSet(_index, this, index - 1);
    this.dispatchEvent(new CustomEvent('popstate', {
      detail
    }));
    this.dispatchEvent(new Event('index-change'));
  }
  forward() {
    const index = _classPrivateFieldGet(_index, this);
    if (index >= _classPrivateFieldGet(_arr, this).length - 1) return;
    const detail = {
      state: _classPrivateFieldGet(_arr, this)[index + 1]
    };
    _classPrivateFieldSet(_index, this, index + 1);
    this.dispatchEvent(new CustomEvent('popstate', {
      detail
    }));
    this.dispatchEvent(new Event('index-change'));
  }
  get canGoBack() {
    return _classPrivateFieldGet(_index, this) > 0;
  }
  get canGoForward() {
    return _classPrivateFieldGet(_index, this) < _classPrivateFieldGet(_arr, this).length - 1;
  }
  clear() {
    _classPrivateFieldSet(_arr, this, []);
    _classPrivateFieldSet(_index, this, -1);
  }
}
const languageInfo = lang => {
  if (!lang) return {};
  try {
    var _ref, _locale$getTextInfo, _locale$getTextInfo2;
    const canonical = Intl.getCanonicalLocales(lang)[0];
    const locale = new Intl.Locale(canonical);
    const isCJK = ['zh', 'ja', 'kr'].includes(locale.language);
    const direction = (_ref = (_locale$getTextInfo = (_locale$getTextInfo2 = locale.getTextInfo) === null || _locale$getTextInfo2 === void 0 ? void 0 : _locale$getTextInfo2.call(locale)) !== null && _locale$getTextInfo !== void 0 ? _locale$getTextInfo : locale.textInfo) === null || _ref === void 0 ? void 0 : _ref.direction;
    return {
      canonical,
      locale,
      isCJK,
      direction
    };
  } catch (e) {
    console.warn(e);
    return {};
  }
};
var _root = /*#__PURE__*/new WeakMap();
var _sectionProgress = /*#__PURE__*/new WeakMap();
var _tocProgress = /*#__PURE__*/new WeakMap();
var _pageProgress = /*#__PURE__*/new WeakMap();
var _searchResults = /*#__PURE__*/new WeakMap();
var _searchDraw = /*#__PURE__*/new WeakMap();
var _searchDrawOptions = /*#__PURE__*/new WeakMap();
var _cursorAutohider = /*#__PURE__*/new WeakMap();
var _View_brand = /*#__PURE__*/new WeakSet();
export class View extends HTMLElement {
  constructor() {
    super();
    _classPrivateMethodInitSpec(this, _View_brand);
    _classPrivateFieldInitSpec(this, _root, this.attachShadow({
      mode: 'closed'
    }));
    _classPrivateFieldInitSpec(this, _sectionProgress, void 0);
    _classPrivateFieldInitSpec(this, _tocProgress, void 0);
    _classPrivateFieldInitSpec(this, _pageProgress, void 0);
    _classPrivateFieldInitSpec(this, _searchResults, new Map());
    _classPrivateFieldInitSpec(this, _searchDraw, void 0);
    _classPrivateFieldInitSpec(this, _searchDrawOptions, void 0);
    _classPrivateFieldInitSpec(this, _cursorAutohider, new CursorAutohider(this, () => this.hasAttribute('autohide-cursor')));
    _defineProperty(this, "isFixedLayout", false);
    _defineProperty(this, "lastLocation", void 0);
    _defineProperty(this, "history", new History());
    this.history.addEventListener('popstate', ({
      detail
    }) => {
      const resolved = this.resolveNavigation(detail.state);
      this.renderer.goTo(resolved);
    });
  }
  async open(book) {
    var _book$metadata, _this$book$rendition;
    if (typeof book === 'string' || typeof book.arrayBuffer === 'function' || book.isDirectory) book = await makeBook(book);
    this.book = book;
    this.language = languageInfo((_book$metadata = book.metadata) === null || _book$metadata === void 0 ? void 0 : _book$metadata.language);
    if (book.splitTOCHref && book.getTOCFragment) {
      var _book$toc, _book$pageList;
      const ids = book.sections.map(s => s.id);
      _classPrivateFieldSet(_sectionProgress, this, new SectionProgress(book.sections, 1500, 1600));
      const splitHref = book.splitTOCHref.bind(book);
      const getFragment = book.getTOCFragment.bind(book);
      _classPrivateFieldSet(_tocProgress, this, new TOCProgress());
      await _classPrivateFieldGet(_tocProgress, this).init({
        toc: (_book$toc = book.toc) !== null && _book$toc !== void 0 ? _book$toc : [],
        ids,
        splitHref,
        getFragment
      });
      _classPrivateFieldSet(_pageProgress, this, new TOCProgress());
      await _classPrivateFieldGet(_pageProgress, this).init({
        toc: (_book$pageList = book.pageList) !== null && _book$pageList !== void 0 ? _book$pageList : [],
        ids,
        splitHref,
        getFragment
      });
    }
    this.isFixedLayout = ((_this$book$rendition = this.book.rendition) === null || _this$book$rendition === void 0 ? void 0 : _this$book$rendition.layout) === 'pre-paginated';
    if (this.isFixedLayout) {
      await import('./fixed-layout.js');
      this.renderer = document.createElement('foliate-fxl');
    } else {
      await import('./paginator.js');
      this.renderer = document.createElement('foliate-paginator');
    }
    this.renderer.setAttribute('exportparts', 'head,foot,filter');
    this.renderer.addEventListener('load', e => _assertClassBrand(_View_brand, this, _onLoad).call(this, e.detail));
    this.renderer.addEventListener('relocate', e => _assertClassBrand(_View_brand, this, _onRelocate).call(this, e.detail));
    this.renderer.addEventListener('create-overlayer', e => e.detail.attach(_assertClassBrand(_View_brand, this, _createOverlayer).call(this, e.detail)));
    this.renderer.open(book);
    _classPrivateFieldGet(_root, this).append(this.renderer);
    if (book.sections.some(section => section.mediaOverlay)) {
      const activeClass = book.media.activeClass;
      const playbackActiveClass = book.media.playbackActiveClass;
      this.mediaOverlay = book.getMediaOverlay();
      let lastActive;
      this.mediaOverlay.addEventListener('highlight', e => {
        const resolved = this.resolveNavigation(e.detail.text);
        this.renderer.goTo(resolved).then(() => {
          const {
            doc
          } = this.renderer.getContents().find(x => x.index = resolved.index);
          const el = resolved.anchor(doc);
          el.classList.add(activeClass);
          if (playbackActiveClass) el.ownerDocument.documentElement.classList.add(playbackActiveClass);
          lastActive = new WeakRef(el);
        });
      });
      this.mediaOverlay.addEventListener('unhighlight', () => {
        var _lastActive;
        const el = (_lastActive = lastActive) === null || _lastActive === void 0 ? void 0 : _lastActive.deref();
        if (el) {
          el.classList.remove(activeClass);
          if (playbackActiveClass) el.ownerDocument.documentElement.classList.remove(playbackActiveClass);
        }
      });
    }
  }
  close() {
    var _this$renderer, _this$renderer2;
    (_this$renderer = this.renderer) === null || _this$renderer === void 0 || _this$renderer.destroy();
    (_this$renderer2 = this.renderer) === null || _this$renderer2 === void 0 || _this$renderer2.remove();
    _classPrivateFieldSet(_sectionProgress, this, null);
    _classPrivateFieldSet(_tocProgress, this, null);
    _classPrivateFieldSet(_pageProgress, this, null);
    _classPrivateFieldSet(_searchResults, this, new Map());
    this.lastLocation = null;
    this.history.clear();
    this.tts = null;
    this.mediaOverlay = null;
  }
  goToTextStart() {
    var _this$book$landmarks$, _this$book$landmarks;
    return this.goTo((_this$book$landmarks$ = (_this$book$landmarks = this.book.landmarks) === null || _this$book$landmarks === void 0 || (_this$book$landmarks = _this$book$landmarks.find(m => m.type.includes('bodymatter') || m.type.includes('text'))) === null || _this$book$landmarks === void 0 ? void 0 : _this$book$landmarks.href) !== null && _this$book$landmarks$ !== void 0 ? _this$book$landmarks$ : this.book.sections.findIndex(s => s.linear !== 'no'));
  }
  async init({
    lastLocation,
    showTextStart
  }) {
    const resolved = lastLocation ? this.resolveNavigation(lastLocation) : null;
    if (resolved) {
      await this.renderer.goTo(resolved);
      this.history.pushState(lastLocation);
    } else if (showTextStart) await this.goToTextStart();else {
      this.history.pushState(0);
      await this.next();
    }
  }
  async addAnnotation(annotation, remove) {
    var _classPrivateFieldGet2, _classPrivateFieldGet3;
    const {
      value
    } = annotation;
    if (value.startsWith(SEARCH_PREFIX)) {
      const cfi = value.replace(SEARCH_PREFIX, '');
      const {
        index,
        anchor
      } = await this.resolveNavigation(cfi);
      const obj = _assertClassBrand(_View_brand, this, _getOverlayer).call(this, index);
      if (obj) {
        const {
          overlayer,
          doc
        } = obj;
        if (remove) {
          overlayer.remove(value);
          return;
        }
        const range = doc ? anchor(doc) : anchor;
        overlayer.add(value, range, _classPrivateFieldGet(_searchDraw, this), _classPrivateFieldGet(_searchDrawOptions, this));
      }
      return;
    }
    const {
      index,
      anchor
    } = await this.resolveNavigation(value);
    const obj = _assertClassBrand(_View_brand, this, _getOverlayer).call(this, index);
    if (obj) {
      const {
        overlayer,
        doc
      } = obj;
      overlayer.remove(value);
      if (!remove) {
        const range = doc ? anchor(doc) : anchor;
        const draw = (func, opts) => overlayer.add(value, range, func, opts);
        _assertClassBrand(_View_brand, this, _emit).call(this, 'draw-annotation', {
          draw,
          annotation,
          doc,
          range
        });
      }
    }
    const label = (_classPrivateFieldGet2 = (_classPrivateFieldGet3 = _classPrivateFieldGet(_tocProgress, this).getProgress(index)) === null || _classPrivateFieldGet3 === void 0 ? void 0 : _classPrivateFieldGet3.label) !== null && _classPrivateFieldGet2 !== void 0 ? _classPrivateFieldGet2 : '';
    return {
      index,
      label
    };
  }
  deleteAnnotation(annotation) {
    return this.addAnnotation(annotation, true);
  }
  async showAnnotation(annotation) {
    const {
      value
    } = annotation;
    const resolved = await this.goTo(value);
    if (resolved) {
      const {
        index,
        anchor
      } = resolved;
      const {
        doc
      } = _assertClassBrand(_View_brand, this, _getOverlayer).call(this, index);
      const range = anchor(doc);
      _assertClassBrand(_View_brand, this, _emit).call(this, 'show-annotation', {
        value,
        index,
        range
      });
    }
  }
  getCFI(index, range) {
    var _this$book$sections$i;
    const baseCFI = (_this$book$sections$i = this.book.sections[index].cfi) !== null && _this$book$sections$i !== void 0 ? _this$book$sections$i : CFI.fake.fromIndex(index);
    if (!range) return baseCFI;
    return CFI.joinIndir(baseCFI, CFI.fromRange(range));
  }
  resolveCFI(cfi) {
    if (this.book.resolveCFI) return this.book.resolveCFI(cfi);else {
      var _parts$parent;
      const parts = CFI.parse(cfi);
      const index = CFI.fake.toIndex(((_parts$parent = parts.parent) !== null && _parts$parent !== void 0 ? _parts$parent : parts).shift());
      const anchor = doc => CFI.toRange(doc, parts);
      return {
        index,
        anchor
      };
    }
  }
  resolveNavigation(target) {
    try {
      if (typeof target === 'number') return {
        index: target
      };
      if (typeof target.fraction === 'number') {
        const [index, anchor] = _classPrivateFieldGet(_sectionProgress, this).getSection(target.fraction);
        return {
          index,
          anchor
        };
      }
      if (CFI.isCFI.test(target)) return this.resolveCFI(target);
      return this.book.resolveHref(target);
    } catch (e) {
      console.error(e);
      console.error(`Could not resolve target ${target}`);
    }
  }
  async goTo(target) {
    const resolved = this.resolveNavigation(target);
    try {
      await this.renderer.goTo(resolved);
      this.history.pushState(target);
      return resolved;
    } catch (e) {
      console.error(e);
      console.error(`Could not go to ${target}`);
    }
  }
  async goToFraction(frac) {
    const [index, anchor] = _classPrivateFieldGet(_sectionProgress, this).getSection(frac);
    await this.renderer.goTo({
      index,
      anchor
    });
    this.history.pushState({
      fraction: frac
    });
  }
  async select(target) {
    try {
      const obj = await this.resolveNavigation(target);
      await this.renderer.goTo({
        ...obj,
        select: true
      });
      this.history.pushState(target);
    } catch (e) {
      console.error(e);
      console.error(`Could not go to ${target}`);
    }
  }
  deselect() {
    for (const {
      doc
    } of this.renderer.getContents()) doc.defaultView.getSelection().removeAllRanges();
  }
  getSectionFractions() {
    var _classPrivateFieldGet4, _classPrivateFieldGet5;
    return ((_classPrivateFieldGet4 = (_classPrivateFieldGet5 = _classPrivateFieldGet(_sectionProgress, this)) === null || _classPrivateFieldGet5 === void 0 ? void 0 : _classPrivateFieldGet5.sectionFractions) !== null && _classPrivateFieldGet4 !== void 0 ? _classPrivateFieldGet4 : []).map(x => x + Number.EPSILON);
  }
  getProgressOf(index, range) {
    var _classPrivateFieldGet6, _classPrivateFieldGet7;
    const tocItem = (_classPrivateFieldGet6 = _classPrivateFieldGet(_tocProgress, this)) === null || _classPrivateFieldGet6 === void 0 ? void 0 : _classPrivateFieldGet6.getProgress(index, range);
    const pageItem = (_classPrivateFieldGet7 = _classPrivateFieldGet(_pageProgress, this)) === null || _classPrivateFieldGet7 === void 0 ? void 0 : _classPrivateFieldGet7.getProgress(index, range);
    return {
      tocItem,
      pageItem
    };
  }
  async getTOCItemOf(target) {
    try {
      const {
        index,
        anchor
      } = await this.resolveNavigation(target);
      const doc = await this.book.sections[index].createDocument();
      const frag = anchor(doc);
      const isRange = frag instanceof Range;
      const range = isRange ? frag : doc.createRange();
      if (!isRange) range.selectNodeContents(frag);
      return _classPrivateFieldGet(_tocProgress, this).getProgress(index, range);
    } catch (e) {
      console.error(e);
      console.error(`Could not get ${target}`);
    }
  }
  async prev(distance) {
    await this.renderer.prev(distance);
  }
  async next(distance) {
    await this.renderer.next(distance);
  }
  goLeft() {
    return this.book.dir === 'rtl' ? this.next() : this.prev();
  }
  goRight() {
    return this.book.dir === 'rtl' ? this.prev() : this.next();
  }
  async *search(opts) {
    var _opts$draw;
    this.clearSearch();
    _classPrivateFieldSet(_searchDraw, this, (_opts$draw = opts.draw) !== null && _opts$draw !== void 0 ? _opts$draw : Overlayer.outline);
    _classPrivateFieldSet(_searchDrawOptions, this, opts.drawOptions);
    const {
      searchMatcher
    } = await import('./search.js');
    const {
      query,
      index
    } = opts;
    const matcher = searchMatcher(textWalker, {
      defaultLocale: this.language,
      ...opts
    });
    const iter = index != null ? _assertClassBrand(_View_brand, this, _searchSection).call(this, matcher, query, index) : _assertClassBrand(_View_brand, this, _searchBook).call(this, matcher, query);
    const list = [];
    _classPrivateFieldGet(_searchResults, this).set(index, list);
    for await (const result of iter) {
      if (result.subitems) {
        var _classPrivateFieldGet8, _classPrivateFieldGet9;
        const list = result.subitems.map(({
          cfi
        }) => ({
          value: SEARCH_PREFIX + cfi
        }));
        _classPrivateFieldGet(_searchResults, this).set(result.index, list);
        for (const item of list) this.addAnnotation(item);
        yield {
          label: (_classPrivateFieldGet8 = (_classPrivateFieldGet9 = _classPrivateFieldGet(_tocProgress, this).getProgress(result.index)) === null || _classPrivateFieldGet9 === void 0 ? void 0 : _classPrivateFieldGet9.label) !== null && _classPrivateFieldGet8 !== void 0 ? _classPrivateFieldGet8 : '',
          subitems: result.subitems
        };
      } else {
        if (result.cfi) {
          const item = {
            value: SEARCH_PREFIX + result.cfi
          };
          list.push(item);
          this.addAnnotation(item);
        }
        yield result;
      }
    }
    yield 'done';
  }
  clearSearch() {
    for (const list of _classPrivateFieldGet(_searchResults, this).values()) for (const item of list) this.deleteAnnotation(item);
    _classPrivateFieldGet(_searchResults, this).clear();
  }
  async initTTS(granularity = 'word', highlight) {
    const doc = this.renderer.getContents()[0].doc;
    if (this.tts && this.tts.doc === doc) return;
    const {
      TTS
    } = await import('./tts.js');
    this.tts = new TTS(doc, textWalker, highlight || (range => this.renderer.scrollToAnchor(range, true)), granularity);
  }
  startMediaOverlay() {
    const {
      index
    } = this.renderer.getContents()[0];
    return this.mediaOverlay.start(index);
  }
}
function _emit(name, detail, cancelable) {
  return this.dispatchEvent(new CustomEvent(name, {
    detail,
    cancelable
  }));
}
function _onRelocate({
  reason,
  range,
  index,
  fraction,
  size
}) {
  var _classPrivateFieldGet0, _classPrivateFieldGet1, _classPrivateFieldGet10, _classPrivateFieldGet11;
  const progress = (_classPrivateFieldGet0 = (_classPrivateFieldGet1 = _classPrivateFieldGet(_sectionProgress, this)) === null || _classPrivateFieldGet1 === void 0 ? void 0 : _classPrivateFieldGet1.getProgress(index, fraction, size)) !== null && _classPrivateFieldGet0 !== void 0 ? _classPrivateFieldGet0 : {};
  const tocItem = (_classPrivateFieldGet10 = _classPrivateFieldGet(_tocProgress, this)) === null || _classPrivateFieldGet10 === void 0 ? void 0 : _classPrivateFieldGet10.getProgress(index, range);
  const pageItem = (_classPrivateFieldGet11 = _classPrivateFieldGet(_pageProgress, this)) === null || _classPrivateFieldGet11 === void 0 ? void 0 : _classPrivateFieldGet11.getProgress(index, range);
  const cfi = this.getCFI(index, range);
  this.lastLocation = {
    ...progress,
    tocItem,
    pageItem,
    cfi,
    range
  };
  if (reason === 'snap' || reason === 'page' || reason === 'scroll') this.history.replaceState(cfi);
  _assertClassBrand(_View_brand, this, _emit).call(this, 'relocate', this.lastLocation);
}
function _onLoad({
  doc,
  index
}) {
  var _doc$documentElement, _this$language$canoni, _doc$documentElement2, _this$language$direct;
  // set language and dir if not already set
  (_doc$documentElement = doc.documentElement).lang || (_doc$documentElement.lang = (_this$language$canoni = this.language.canonical) !== null && _this$language$canoni !== void 0 ? _this$language$canoni : '');
  if (!this.language.isCJK) (_doc$documentElement2 = doc.documentElement).dir || (_doc$documentElement2.dir = (_this$language$direct = this.language.direction) !== null && _this$language$direct !== void 0 ? _this$language$direct : '');
  _assertClassBrand(_View_brand, this, _handleLinks).call(this, doc, index);
  _classPrivateFieldGet(_cursorAutohider, this).cloneFor(doc.documentElement);
  _assertClassBrand(_View_brand, this, _emit).call(this, 'load', {
    doc,
    index
  });
}
function _handleLinks(doc, index) {
  const {
    book
  } = this;
  const section = book.sections[index];
  doc.addEventListener('click', e => {
    var _section$resolveHref, _section$resolveHref2, _book$isExternal;
    const a = e.target.closest('a[href]');
    if (!a) return;
    e.preventDefault();
    const href_ = a.getAttribute('href');
    const href = (_section$resolveHref = section === null || section === void 0 || (_section$resolveHref2 = section.resolveHref) === null || _section$resolveHref2 === void 0 ? void 0 : _section$resolveHref2.call(section, href_)) !== null && _section$resolveHref !== void 0 ? _section$resolveHref : href_;
    if (book !== null && book !== void 0 && (_book$isExternal = book.isExternal) !== null && _book$isExternal !== void 0 && _book$isExternal.call(book, href)) Promise.resolve(_assertClassBrand(_View_brand, this, _emit).call(this, 'external-link', {
      a,
      href_
    }, true)).then(x => x ? globalThis.open(href_, '_blank') : null).catch(e => console.error(e));else Promise.resolve(_assertClassBrand(_View_brand, this, _emit).call(this, 'link', {
      a,
      href
    }, true)).then(x => x ? this.goTo(href) : null).catch(e => console.error(e));
  });
}
function _getOverlayer(index) {
  return this.renderer.getContents().find(x => x.index === index && x.overlayer);
}
function _createOverlayer({
  doc,
  index
}) {
  const overlayer = new Overlayer();
  doc.addEventListener('click', e => {
    const [value, range] = overlayer.hitTest(e);
    if (value && !value.startsWith(SEARCH_PREFIX)) {
      _assertClassBrand(_View_brand, this, _emit).call(this, 'show-annotation', {
        value,
        index,
        range
      });
    }
  }, false);
  const list = _classPrivateFieldGet(_searchResults, this).get(index);
  if (list) for (const item of list) this.addAnnotation(item);
  _assertClassBrand(_View_brand, this, _emit).call(this, 'create-overlay', {
    index
  });
  return overlayer;
}
async function* _searchSection(matcher, query, index) {
  const doc = await this.book.sections[index].createDocument();
  for (const {
    range,
    excerpt
  } of matcher(doc, query)) yield {
    cfi: this.getCFI(index, range),
    excerpt
  };
}
async function* _searchBook(matcher, query) {
  const {
    sections
  } = this.book;
  for (const [index, {
    createDocument
  }] of sections.entries()) {
    if (!createDocument) continue;
    const doc = await createDocument();
    const subitems = Array.from(matcher(doc, query), ({
      range,
      excerpt
    }) => ({
      cfi: this.getCFI(index, range),
      excerpt
    }));
    const progress = (index + 1) / sections.length;
    yield {
      progress
    };
    if (subitems.length) yield {
      index,
      subitems
    };
  }
}
customElements.define('foliate-view', View);
