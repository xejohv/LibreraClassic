function _defineProperty(e, r, t) { return (r = _toPropertyKey(r)) in e ? Object.defineProperty(e, r, { value: t, enumerable: !0, configurable: !0, writable: !0 }) : e[r] = t, e; }
function _toPropertyKey(t) { var i = _toPrimitive(t, "string"); return "symbol" == typeof i ? i : i + ""; }
function _toPrimitive(t, e) { if ("object" != typeof t || !t) return t; var r; if ("undefined" != typeof Symbol && void 0 !== (r = t[Symbol.toPrimitive])) { var i = r.call(t, e || "default"); if ("object" != typeof i) return i; throw new TypeError("@@toPrimitive must return a primitive value."); } return ("string" === e ? String : Number)(t); }
function _classPrivateMethodInitSpec(e, a) { _checkPrivateRedeclaration(e, a), a.add(e); }
function _classPrivateFieldInitSpec(e, t, a) { _checkPrivateRedeclaration(e, t), t.set(e, a); }
function _checkPrivateRedeclaration(e, t) { if (t.has(e)) throw new TypeError("Cannot initialize the same private elements twice on an object"); }
function _classPrivateGetter(s, r, a) { return a(_assertClassBrand(s, r)); }
function _classPrivateFieldSet(s, a, r) { return s.set(_assertClassBrand(s, a), r), r; }
function _classPrivateFieldGet(s, a) { return s.get(_assertClassBrand(s, a)); }
function _assertClassBrand(e, t, n) { if ("function" == typeof e ? e === t : e.has(t)) return arguments.length < 3 ? t : n; throw new TypeError("Private element is not present on this object"); }
import * as CFI from './epubcfi.js';
const NS = {
  CONTAINER: 'urn:oasis:names:tc:opendocument:xmlns:container',
  XHTML: 'http://www.w3.org/1999/xhtml',
  OPF: 'http://www.idpf.org/2007/opf',
  EPUB: 'http://www.idpf.org/2007/ops',
  DC: 'http://purl.org/dc/elements/1.1/',
  DCTERMS: 'http://purl.org/dc/terms/',
  ENC: 'http://www.w3.org/2001/04/xmlenc#',
  NCX: 'http://www.daisy.org/z3986/2005/ncx/',
  XLINK: 'http://www.w3.org/1999/xlink',
  SMIL: 'http://www.w3.org/ns/SMIL'
};
const MIME = {
  XML: 'application/xml',
  NCX: 'application/x-dtbncx+xml',
  XHTML: 'application/xhtml+xml',
  HTML: 'text/html',
  CSS: 'text/css',
  SVG: 'image/svg+xml',
  JS: /\/(x-)?(javascript|ecmascript)/
};

// https://www.w3.org/TR/epub-33/#sec-reserved-prefixes
const PREFIX = {
  a11y: 'http://www.idpf.org/epub/vocab/package/a11y/#',
  dcterms: 'http://purl.org/dc/terms/',
  marc: 'http://id.loc.gov/vocabulary/',
  media: 'http://www.idpf.org/epub/vocab/overlays/#',
  onix: 'http://www.editeur.org/ONIX/book/codelists/current.html#',
  rendition: 'http://www.idpf.org/vocab/rendition/#',
  schema: 'http://schema.org/',
  xsd: 'http://www.w3.org/2001/XMLSchema#',
  msv: 'http://www.idpf.org/epub/vocab/structure/magazine/#',
  prism: 'http://www.prismstandard.org/specifications/3.0/PRISM_CV_Spec_3.0.htm#'
};
const RELATORS = {
  art: 'artist',
  aut: 'author',
  clr: 'colorist',
  edt: 'editor',
  ill: 'illustrator',
  nrt: 'narrator',
  trl: 'translator',
  pbl: 'publisher'
};
const ONIX5 = {
  '02': 'isbn',
  '06': 'doi',
  '15': 'isbn',
  '26': 'doi',
  '34': 'issn'
};

// convert to camel case
const camel = x => x.toLowerCase().replace(/[-:](.)/g, (_, g) => g.toUpperCase());

// strip and collapse ASCII whitespace
// https://infra.spec.whatwg.org/#strip-and-collapse-ascii-whitespace
const normalizeWhitespace = str => str ? str.replace(/[\t\n\f\r ]+/g, ' ').replace(/^[\t\n\f\r ]+/, '').replace(/[\t\n\f\r ]+$/, '') : '';
const filterAttribute = (attr, value, isList) => isList ? el => {
  var _el$getAttribute;
  return (_el$getAttribute = el.getAttribute(attr)) === null || _el$getAttribute === void 0 || (_el$getAttribute = _el$getAttribute.split(/\s/)) === null || _el$getAttribute === void 0 ? void 0 : _el$getAttribute.includes(value);
} : typeof value === 'function' ? el => value(el.getAttribute(attr)) : el => el.getAttribute(attr) === value;
const getAttributes = (...xs) => el => el ? Object.fromEntries(xs.map(x => [camel(x), el.getAttribute(x)])) : null;
const getElementText = el => normalizeWhitespace(el === null || el === void 0 ? void 0 : el.textContent);
const childGetter = (doc, ns) => {
  // ignore the namespace if it doesn't appear in document at all
  const useNS = doc.lookupNamespaceURI(null) === ns || doc.lookupPrefix(ns);
  const f = useNS ? (el, name) => el => el.namespaceURI === ns && el.localName === name : (el, name) => el => el.localName === name;
  return {
    $: (el, name) => [...el.children].find(f(el, name)),
    $$: (el, name) => [...el.children].filter(f(el, name)),
    $$$: useNS ? (el, name) => [...el.getElementsByTagNameNS(ns, name)] : (el, name) => [...el.getElementsByTagName(name)]
  };
};
const resolveURL = (url, relativeTo) => {
  try {
    if (relativeTo.includes(':')) return new URL(url, relativeTo);
    // the base needs to be a valid URL, so set a base URL and then remove it
    const root = 'https://invalid.invalid/';
    const obj = new URL(url, root + relativeTo);
    obj.search = '';
    return decodeURI(obj.href.replace(root, ''));
  } catch (e) {
    console.warn(e);
    return url;
  }
};
const isExternal = uri => /^(?!blob)\w+:/i.test(uri);

// like `path.relative()` in Node.js
const pathRelative = (from, to) => {
  if (!from) return to;
  const as = from.replace(/\/$/, '').split('/');
  const bs = to.replace(/\/$/, '').split('/');
  const i = (as.length > bs.length ? as : bs).findIndex((_, i) => as[i] !== bs[i]);
  return i < 0 ? '' : Array(as.length - i).fill('..').concat(bs.slice(i)).join('/');
};
const pathDirname = str => str.slice(0, str.lastIndexOf('/') + 1);

// replace asynchronously and sequentially
// same technique as https://stackoverflow.com/a/48032528
const replaceSeries = async (str, regex, f) => {
  const matches = [];
  str.replace(regex, (...args) => (matches.push(args), null));
  const results = [];
  for (const args of matches) results.push(await f(...args));
  return str.replace(regex, () => results.shift());
};
const regexEscape = str => str.replace(/[-/\\^$*+?.()|[\]{}]/g, '\\$&');
const tidy = obj => {
  for (const [key, val] of Object.entries(obj)) if (val == null) delete obj[key];else if (Array.isArray(val)) {
    obj[key] = val.filter(x => x).map(x => typeof x === 'object' && !Array.isArray(x) ? tidy(x) : x);
    if (!obj[key].length) delete obj[key];else if (obj[key].length === 1) obj[key] = obj[key][0];
  } else if (typeof val === 'object') {
    obj[key] = tidy(val);
    if (!Object.keys(val).length) delete obj[key];
  }
  const keys = Object.keys(obj);
  if (keys.length === 1 && keys[0] === 'name') return obj[keys[0]];
  return obj;
};

// https://www.w3.org/TR/epub/#sec-prefix-attr
const getPrefixes = doc => {
  const map = new Map(Object.entries(PREFIX));
  const value = doc.documentElement.getAttributeNS(NS.EPUB, 'prefix') || doc.documentElement.getAttribute('prefix');
  if (value) for (const [, prefix, url] of value.matchAll(/(.+): +(.+)[ \t\r\n]*/g)) map.set(prefix, url);
  return map;
};

// https://www.w3.org/TR/epub-rs/#sec-property-values
// but ignoring the case where the prefix is omitted
const getPropertyURL = (value, prefixes) => {
  if (!value) return null;
  const [a, b] = value.split(':');
  const prefix = b ? a : null;
  const reference = b ? b : a;
  const baseURL = prefixes.get(prefix);
  return baseURL ? baseURL + reference : null;
};
const getMetadata = opf => {
  var _ref, _$metadata$getAttribu, _els$meta, _getProperties, _els$legacyMeta$map, _els$legacyMeta, _properties$belongsT, _dc$title$find, _dc$title, _dc$title2, _ref2, _makeLanguageMap2, _mainTitle$props, _mainTitle$attrs, _dc$title3, _dc$language, _dc$publisher, _dc$date$find$value, _dc$date, _one, _dc$date2, _dc$subject, _belongsTo$collection, _belongsTo$series$map, _belongsTo$series, _dc$identifier, _dc$source;
  const {
    $
  } = childGetter(opf, NS.OPF);
  const $metadata = $(opf.documentElement, 'metadata');

  // first pass: convert to JS objects
  const els = Object.groupBy($metadata.children, el => el.namespaceURI === NS.DC ? 'dc' : el.namespaceURI === NS.OPF && el.localName === 'meta' ? el.hasAttribute('name') ? 'legacyMeta' : 'meta' : '');
  const baseLang = (_ref = (_$metadata$getAttribu = $metadata.getAttribute('xml:lang')) !== null && _$metadata$getAttribu !== void 0 ? _$metadata$getAttribu : opf.documentElement.getAttribute('xml:lang')) !== null && _ref !== void 0 ? _ref : 'und';
  const prefixes = getPrefixes(opf);
  const parse = el => {
    var _getPropertyURL, _getPropertyURL2;
    const property = el.getAttribute('property');
    const scheme = el.getAttribute('scheme');
    return {
      property: (_getPropertyURL = getPropertyURL(property, prefixes)) !== null && _getPropertyURL !== void 0 ? _getPropertyURL : property,
      scheme: (_getPropertyURL2 = getPropertyURL(scheme, prefixes)) !== null && _getPropertyURL2 !== void 0 ? _getPropertyURL2 : scheme,
      lang: el.getAttribute('xml:lang'),
      value: getElementText(el),
      props: getProperties(el),
      // `opf:` attributes from EPUB 2 & EPUB 3.1 (removed in EPUB 3.2)
      attrs: Object.fromEntries(Array.from(el.attributes).filter(attr => attr.namespaceURI === NS.OPF).map(attr => [attr.localName, attr.value]))
    };
  };
  const refines = Map.groupBy((_els$meta = els.meta) !== null && _els$meta !== void 0 ? _els$meta : [], el => el.getAttribute('refines'));
  const getProperties = el => {
    const els = refines.get(el ? '#' + el.getAttribute('id') : null);
    if (!els) return null;
    return Object.groupBy(els.map(parse), x => x.property);
  };
  const dc = Object.fromEntries(Object.entries(Object.groupBy(els.dc, el => el.localName)).map(([name, els]) => [name, els.map(parse)]));
  const properties = (_getProperties = getProperties()) !== null && _getProperties !== void 0 ? _getProperties : {};
  const legacyMeta = Object.fromEntries((_els$legacyMeta$map = (_els$legacyMeta = els.legacyMeta) === null || _els$legacyMeta === void 0 ? void 0 : _els$legacyMeta.map(el => [el.getAttribute('name'), el.getAttribute('content')])) !== null && _els$legacyMeta$map !== void 0 ? _els$legacyMeta$map : []);

  // second pass: map to webpub
  const one = x => {
    var _x$;
    return x === null || x === void 0 || (_x$ = x[0]) === null || _x$ === void 0 ? void 0 : _x$.value;
  };
  const prop = (x, p) => {
    var _x$props;
    return one(x === null || x === void 0 || (_x$props = x.props) === null || _x$props === void 0 ? void 0 : _x$props[p]);
  };
  const makeLanguageMap = x => {
    var _x$props$alternateSc, _x$props2, _x$lang;
    if (!x) return null;
    const alts = (_x$props$alternateSc = (_x$props2 = x.props) === null || _x$props2 === void 0 ? void 0 : _x$props2['alternate-script']) !== null && _x$props$alternateSc !== void 0 ? _x$props$alternateSc : [];
    const altRep = x.attrs['alt-rep'];
    if (!alts.length && (!x.lang || x.lang === baseLang) && !altRep) return x.value;
    const map = {
      [(_x$lang = x.lang) !== null && _x$lang !== void 0 ? _x$lang : baseLang]: x.value
    };
    if (altRep) map[x.attrs['alt-rep-lang']] = altRep;
    for (const y of alts) {
      var _y$lang, _map$_y$lang;
      (_map$_y$lang = map[_y$lang = y.lang]) !== null && _map$_y$lang !== void 0 ? _map$_y$lang : map[_y$lang] = y.value;
    }
    return map;
  };
  const makeContributor = x => {
    var _makeLanguageMap, _x$props3, _x$props$role$filter$, _x$props4, _prop, _prop2;
    return x ? {
      name: makeLanguageMap(x),
      sortAs: (_makeLanguageMap = makeLanguageMap((_x$props3 = x.props) === null || _x$props3 === void 0 || (_x$props3 = _x$props3['file-as']) === null || _x$props3 === void 0 ? void 0 : _x$props3[0])) !== null && _makeLanguageMap !== void 0 ? _makeLanguageMap : x.attrs['file-as'],
      role: (_x$props$role$filter$ = (_x$props4 = x.props) === null || _x$props4 === void 0 || (_x$props4 = _x$props4.role) === null || _x$props4 === void 0 || (_x$props4 = _x$props4.filter(x => x.scheme === PREFIX.marc + 'relators')) === null || _x$props4 === void 0 ? void 0 : _x$props4.map(x => x.value)) !== null && _x$props$role$filter$ !== void 0 ? _x$props$role$filter$ : [x.attrs.role],
      code: (_prop = prop(x, 'term')) !== null && _prop !== void 0 ? _prop : x.attrs.term,
      scheme: (_prop2 = prop(x, 'authority')) !== null && _prop2 !== void 0 ? _prop2 : x.attrs.authority
    } : null;
  };
  const makeCollection = x => {
    var _x$props5;
    return {
      name: makeLanguageMap(x),
      // NOTE: webpub requires number but EPUB allows values like "2.2.1"
      position: one((_x$props5 = x.props) === null || _x$props5 === void 0 ? void 0 : _x$props5['group-position'])
    };
  };
  const makeAltIdentifier = x => {
    var _x$props6;
    const {
      value
    } = x;
    if (/^urn:/i.test(value)) return value;
    if (/^doi:/i.test(value)) return `urn:${value}`;
    const type = (_x$props6 = x.props) === null || _x$props6 === void 0 ? void 0 : _x$props6['identifier-type'];
    if (!type) {
      const scheme = x.attrs.scheme;
      if (!scheme) return value;
      // https://idpf.github.io/epub-registries/identifiers/
      // but no "jdcn", which isn't a registered URN namespace
      if (/^(doi|isbn|uuid)$/i.test(scheme)) return `urn:${scheme}:${value}`;
      // NOTE: webpub requires scheme to be a URI; EPUB allows anything
      return {
        scheme,
        value
      };
    }
    if (type.scheme === PREFIX.onix + 'codelist5') {
      const nid = ONIX5[type.value];
      if (nid) return `urn:${nid}:${value}`;
    }
    return value;
  };
  const belongsTo = Object.groupBy((_properties$belongsT = properties['belongs-to-collection']) !== null && _properties$belongsT !== void 0 ? _properties$belongsT : [], x => prop(x, 'collection-type') === 'series' ? 'series' : 'collection');
  const mainTitle = (_dc$title$find = (_dc$title = dc.title) === null || _dc$title === void 0 ? void 0 : _dc$title.find(x => prop(x, 'title-type') === 'main')) !== null && _dc$title$find !== void 0 ? _dc$title$find : (_dc$title2 = dc.title) === null || _dc$title2 === void 0 ? void 0 : _dc$title2[0];
  const metadata = {
    identifier: getIdentifier(opf),
    title: makeLanguageMap(mainTitle),
    sortAs: (_ref2 = (_makeLanguageMap2 = makeLanguageMap(mainTitle === null || mainTitle === void 0 || (_mainTitle$props = mainTitle.props) === null || _mainTitle$props === void 0 || (_mainTitle$props = _mainTitle$props['file-as']) === null || _mainTitle$props === void 0 ? void 0 : _mainTitle$props[0])) !== null && _makeLanguageMap2 !== void 0 ? _makeLanguageMap2 : mainTitle === null || mainTitle === void 0 || (_mainTitle$attrs = mainTitle.attrs) === null || _mainTitle$attrs === void 0 ? void 0 : _mainTitle$attrs['file-as']) !== null && _ref2 !== void 0 ? _ref2 : legacyMeta === null || legacyMeta === void 0 ? void 0 : legacyMeta['calibre:title_sort'],
    subtitle: (_dc$title3 = dc.title) === null || _dc$title3 === void 0 || (_dc$title3 = _dc$title3.find(x => prop(x, 'title-type') === 'subtitle')) === null || _dc$title3 === void 0 ? void 0 : _dc$title3.value,
    language: (_dc$language = dc.language) === null || _dc$language === void 0 ? void 0 : _dc$language.map(x => x.value),
    description: one(dc.description),
    publisher: (_dc$publisher = dc.publisher) === null || _dc$publisher === void 0 ? void 0 : _dc$publisher.map(makeContributor),
    published: (_dc$date$find$value = (_dc$date = dc.date) === null || _dc$date === void 0 || (_dc$date = _dc$date.find(x => x.attrs.event === 'publication')) === null || _dc$date === void 0 ? void 0 : _dc$date.value) !== null && _dc$date$find$value !== void 0 ? _dc$date$find$value : one(dc.date),
    modified: (_one = one(properties[PREFIX.dcterms + 'modified'])) !== null && _one !== void 0 ? _one : (_dc$date2 = dc.date) === null || _dc$date2 === void 0 || (_dc$date2 = _dc$date2.find(x => x.attrs.event === 'modification')) === null || _dc$date2 === void 0 ? void 0 : _dc$date2.value,
    subject: (_dc$subject = dc.subject) === null || _dc$subject === void 0 ? void 0 : _dc$subject.map(makeContributor),
    belongsTo: {
      collection: (_belongsTo$collection = belongsTo.collection) === null || _belongsTo$collection === void 0 ? void 0 : _belongsTo$collection.map(makeCollection),
      series: ((_belongsTo$series$map = (_belongsTo$series = belongsTo.series) === null || _belongsTo$series === void 0 ? void 0 : _belongsTo$series.map(makeCollection)) !== null && _belongsTo$series$map !== void 0 ? _belongsTo$series$map : legacyMeta === null || legacyMeta === void 0 ? void 0 : legacyMeta['calibre:series']) ? {
        name: legacyMeta === null || legacyMeta === void 0 ? void 0 : legacyMeta['calibre:series'],
        position: parseFloat(legacyMeta === null || legacyMeta === void 0 ? void 0 : legacyMeta['calibre:series_index'])
      } : null
    },
    altIdentifier: (_dc$identifier = dc.identifier) === null || _dc$identifier === void 0 ? void 0 : _dc$identifier.map(makeAltIdentifier),
    source: (_dc$source = dc.source) === null || _dc$source === void 0 ? void 0 : _dc$source.map(makeAltIdentifier),
    // NOTE: not in webpub schema
    rights: one(dc.rights),
    // NOTE: not in webpub schema
    pageBreakSource: one(properties['pageBreakSource']) // NOTE: not in webpub schema
  };
  const remapContributor = defaultKey => x => {
    var _x$role;
    const keys = new Set((_x$role = x.role) === null || _x$role === void 0 ? void 0 : _x$role.map(role => {
      var _RELATORS$role;
      return (_RELATORS$role = RELATORS[role]) !== null && _RELATORS$role !== void 0 ? _RELATORS$role : defaultKey;
    }));
    return [keys.size ? keys : [defaultKey], x];
  };
  for (const [keys, val] of [].concat((_dc$creator$map$map = (_dc$creator = dc.creator) === null || _dc$creator === void 0 || (_dc$creator = _dc$creator.map(makeContributor)) === null || _dc$creator === void 0 ? void 0 : _dc$creator.map(remapContributor('author'))) !== null && _dc$creator$map$map !== void 0 ? _dc$creator$map$map : [], (_dc$contributor$map$m = (_dc$contributor = dc.contributor) === null || _dc$contributor === void 0 || (_dc$contributor = _dc$contributor.map(makeContributor)) === null || _dc$contributor === void 0 ? void 0 : _dc$contributor.map(remapContributor('contributor'))) !== null && _dc$contributor$map$m !== void 0 ? _dc$contributor$map$m : [])) {
    var _dc$creator$map$map, _dc$creator, _dc$contributor$map$m, _dc$contributor;
    for (const key of keys) if (metadata[key]) metadata[key].push(val);else metadata[key] = [val];
  }
  tidy(metadata);
  if (metadata.altIdentifier === metadata.identifier) delete metadata.altIdentifier;
  const rendition = {};
  const media = {};
  for (const [key, val] of Object.entries(properties)) {
    if (key.startsWith(PREFIX.rendition)) rendition[camel(key.replace(PREFIX.rendition, ''))] = one(val);else if (key.startsWith(PREFIX.media)) media[camel(key.replace(PREFIX.media, ''))] = one(val);
  }
  if (media.duration) media.duration = parseClock(media.duration);
  return {
    metadata,
    rendition,
    media
  };
};
const parseNav = (doc, resolve = f => f) => {
  const {
    $,
    $$,
    $$$
  } = childGetter(doc, NS.XHTML);
  const resolveHref = href => href ? decodeURI(resolve(href)) : null;
  const parseLI = getType => $li => {
    var _$, _$a$getAttributeNS;
    const $a = (_$ = $($li, 'a')) !== null && _$ !== void 0 ? _$ : $($li, 'span');
    const $ol = $($li, 'ol');
    const href = resolveHref($a === null || $a === void 0 ? void 0 : $a.getAttribute('href'));
    const label = getElementText($a) || ($a === null || $a === void 0 ? void 0 : $a.getAttribute('title'));
    // TODO: get and concat alt/title texts in content
    const result = {
      label,
      href,
      subitems: parseOL($ol)
    };
    if (getType) result.type = $a === null || $a === void 0 || (_$a$getAttributeNS = $a.getAttributeNS(NS.EPUB, 'type')) === null || _$a$getAttributeNS === void 0 ? void 0 : _$a$getAttributeNS.split(/\s/);
    return result;
  };
  const parseOL = ($ol, getType) => $ol ? $$($ol, 'li').map(parseLI(getType)) : null;
  const parseNav = ($nav, getType) => parseOL($($nav, 'ol'), getType);
  const $$nav = $$$(doc, 'nav');
  let toc = null,
    pageList = null,
    landmarks = null,
    others = [];
  for (const $nav of $$nav) {
    var _$nav$getAttributeNS$, _$nav$getAttributeNS;
    const type = (_$nav$getAttributeNS$ = (_$nav$getAttributeNS = $nav.getAttributeNS(NS.EPUB, 'type')) === null || _$nav$getAttributeNS === void 0 ? void 0 : _$nav$getAttributeNS.split(/\s/)) !== null && _$nav$getAttributeNS$ !== void 0 ? _$nav$getAttributeNS$ : [];
    if (type.includes('toc')) toc !== null && toc !== void 0 ? toc : toc = parseNav($nav);else if (type.includes('page-list')) pageList !== null && pageList !== void 0 ? pageList : pageList = parseNav($nav);else if (type.includes('landmarks')) landmarks !== null && landmarks !== void 0 ? landmarks : landmarks = parseNav($nav, true);else others.push({
      label: getElementText($nav.firstElementChild),
      type,
      list: parseNav($nav)
    });
  }
  return {
    toc,
    pageList,
    landmarks,
    others
  };
};
const parseNCX = (doc, resolve = f => f) => {
  const {
    $,
    $$
  } = childGetter(doc, NS.NCX);
  const resolveHref = href => href ? decodeURI(resolve(href)) : null;
  const parseItem = el => {
    const $label = $(el, 'navLabel');
    const $content = $(el, 'content');
    const label = getElementText($label);
    const href = resolveHref($content.getAttribute('src'));
    if (el.localName === 'navPoint') {
      const els = $$(el, 'navPoint');
      return {
        label,
        href,
        subitems: els.length ? els.map(parseItem) : null
      };
    }
    return {
      label,
      href
    };
  };
  const parseList = (el, itemName) => $$(el, itemName).map(parseItem);
  const getSingle = (container, itemName) => {
    const $container = $(doc.documentElement, container);
    return $container ? parseList($container, itemName) : null;
  };
  return {
    toc: getSingle('navMap', 'navPoint'),
    pageList: getSingle('pageList', 'pageTarget'),
    others: $$(doc.documentElement, 'navList').map(el => ({
      label: getElementText($(el, 'navLabel')),
      list: parseList(el, 'navTarget')
    }))
  };
};
const parseClock = str => {
  if (!str) return;
  const parts = str.split(':').map(x => parseFloat(x));
  if (parts.length === 3) {
    const [h, m, s] = parts;
    return h * 60 * 60 + m * 60 + s;
  }
  if (parts.length === 2) {
    const [m, s] = parts;
    return m * 60 + s;
  }
  const [x, unit] = str.split(/(?=[^\d.])/);
  const n = parseFloat(x);
  const f = unit === 'h' ? 60 * 60 : unit === 'min' ? 60 : unit === 'ms' ? .001 : 1;
  return n * f;
};
var _entries = /*#__PURE__*/new WeakMap();
var _lastMediaOverlayItem = /*#__PURE__*/new WeakMap();
var _sectionIndex = /*#__PURE__*/new WeakMap();
var _audioIndex = /*#__PURE__*/new WeakMap();
var _itemIndex = /*#__PURE__*/new WeakMap();
var _audio = /*#__PURE__*/new WeakMap();
var _volume = /*#__PURE__*/new WeakMap();
var _rate = /*#__PURE__*/new WeakMap();
var _state = /*#__PURE__*/new WeakMap();
var _MediaOverlay_brand = /*#__PURE__*/new WeakSet();
class MediaOverlay extends EventTarget {
  constructor(book, loadXML) {
    super();
    _classPrivateMethodInitSpec(this, _MediaOverlay_brand);
    _classPrivateFieldInitSpec(this, _entries, void 0);
    _classPrivateFieldInitSpec(this, _lastMediaOverlayItem, void 0);
    _classPrivateFieldInitSpec(this, _sectionIndex, void 0);
    _classPrivateFieldInitSpec(this, _audioIndex, void 0);
    _classPrivateFieldInitSpec(this, _itemIndex, void 0);
    _classPrivateFieldInitSpec(this, _audio, void 0);
    _classPrivateFieldInitSpec(this, _volume, 1);
    _classPrivateFieldInitSpec(this, _rate, 1);
    _classPrivateFieldInitSpec(this, _state, void 0);
    this.book = book;
    this.loadXML = loadXML;
  }
  async start(sectionIndex, filter = () => true) {
    var _classPrivateFieldGet2;
    (_classPrivateFieldGet2 = _classPrivateFieldGet(_audio, this)) === null || _classPrivateFieldGet2 === void 0 || _classPrivateFieldGet2.pause();
    const section = this.book.sections[sectionIndex];
    const href = section === null || section === void 0 ? void 0 : section.id;
    if (!href) return;
    const {
      mediaOverlay
    } = section;
    if (!mediaOverlay) return this.start(sectionIndex + 1);
    _classPrivateFieldSet(_sectionIndex, this, sectionIndex);
    await _assertClassBrand(_MediaOverlay_brand, this, _loadSMIL).call(this, mediaOverlay);
    for (let i = 0; i < _classPrivateFieldGet(_entries, this).length; i++) {
      const {
        items
      } = _classPrivateFieldGet(_entries, this)[i];
      for (let j = 0; j < items.length; j++) {
        if (items[j].text.split('#')[0] === href && filter(items[j], j, items)) return _assertClassBrand(_MediaOverlay_brand, this, _play).call(this, i, j).catch(e => _assertClassBrand(_MediaOverlay_brand, this, _error).call(this, e));
      }
    }
  }
  pause() {
    var _classPrivateFieldGet3;
    _classPrivateFieldSet(_state, this, 'paused');
    (_classPrivateFieldGet3 = _classPrivateFieldGet(_audio, this)) === null || _classPrivateFieldGet3 === void 0 || _classPrivateFieldGet3.pause();
  }
  resume() {
    var _classPrivateFieldGet4;
    _classPrivateFieldSet(_state, this, 'playing');
    (_classPrivateFieldGet4 = _classPrivateFieldGet(_audio, this)) === null || _classPrivateFieldGet4 === void 0 || _classPrivateFieldGet4.play().catch(e => _assertClassBrand(_MediaOverlay_brand, this, _error).call(this, e));
  }
  stop() {
    _classPrivateFieldSet(_state, this, 'stopped');
    _assertClassBrand(_MediaOverlay_brand, this, _stop).call(this);
  }
  prev() {
    if (_classPrivateFieldGet(_itemIndex, this) > 0) _assertClassBrand(_MediaOverlay_brand, this, _play).call(this, _classPrivateFieldGet(_audioIndex, this), _classPrivateFieldGet(_itemIndex, this) - 1);else if (_classPrivateFieldGet(_audioIndex, this) > 0) _assertClassBrand(_MediaOverlay_brand, this, _play).call(this, _classPrivateFieldGet(_audioIndex, this) - 1, _classPrivateFieldGet(_entries, this)[_classPrivateFieldGet(_audioIndex, this) - 1].items.length - 1);else if (_classPrivateFieldGet(_sectionIndex, this) > 0) this.start(_classPrivateFieldGet(_sectionIndex, this) - 1, (_, i, items) => i === items.length - 1);
  }
  next() {
    _assertClassBrand(_MediaOverlay_brand, this, _play).call(this, _classPrivateFieldGet(_audioIndex, this), _classPrivateFieldGet(_itemIndex, this) + 1);
  }
  setVolume(volume) {
    _classPrivateFieldSet(_volume, this, volume);
    if (_classPrivateFieldGet(_audio, this)) _classPrivateFieldGet(_audio, this).volume = volume;
  }
  setRate(rate) {
    _classPrivateFieldSet(_rate, this, rate);
    if (_classPrivateFieldGet(_audio, this)) _classPrivateFieldGet(_audio, this).playbackRate = rate;
  }
}
async function _loadSMIL(item) {
  if (_classPrivateFieldGet(_lastMediaOverlayItem, this) === item) return;
  const doc = await this.loadXML(item.href);
  const resolve = href => href ? resolveURL(href, item.href) : null;
  const {
    $,
    $$$
  } = childGetter(doc, NS.SMIL);
  _classPrivateFieldSet(_audioIndex, this, -1);
  _classPrivateFieldSet(_itemIndex, this, -1);
  _classPrivateFieldSet(_entries, this, $$$(doc, 'par').reduce((arr, $par) => {
    var _$2;
    const text = resolve((_$2 = $($par, 'text')) === null || _$2 === void 0 ? void 0 : _$2.getAttribute('src'));
    const $audio = $($par, 'audio');
    if (!text || !$audio) return arr;
    const src = resolve($audio.getAttribute('src'));
    const begin = parseClock($audio.getAttribute('clipBegin'));
    const end = parseClock($audio.getAttribute('clipEnd'));
    const last = arr.at(-1);
    if ((last === null || last === void 0 ? void 0 : last.src) === src) last.items.push({
      text,
      begin,
      end
    });else arr.push({
      src,
      items: [{
        text,
        begin,
        end
      }]
    });
    return arr;
  }, []));
  _classPrivateFieldSet(_lastMediaOverlayItem, this, item);
}
function _get_activeAudio(_this) {
  return _classPrivateFieldGet(_entries, _this)[_classPrivateFieldGet(_audioIndex, _this)];
}
function _get_activeItem(_this2) {
  var _classPrivateGetter2;
  return (_classPrivateGetter2 = _classPrivateGetter(_MediaOverlay_brand, _this2, _get_activeAudio)) === null || _classPrivateGetter2 === void 0 || (_classPrivateGetter2 = _classPrivateGetter2.items) === null || _classPrivateGetter2 === void 0 ? void 0 : _classPrivateGetter2[_classPrivateFieldGet(_itemIndex, _this2)];
}
function _error(e) {
  console.error(e);
  this.dispatchEvent(new CustomEvent('error', {
    detail: e
  }));
}
function _highlight() {
  this.dispatchEvent(new CustomEvent('highlight', {
    detail: _classPrivateGetter(_MediaOverlay_brand, this, _get_activeItem)
  }));
}
function _unhighlight() {
  this.dispatchEvent(new CustomEvent('unhighlight', {
    detail: _classPrivateGetter(_MediaOverlay_brand, this, _get_activeItem)
  }));
}
async function _play(audioIndex, itemIndex) {
  var _classPrivateGetter3;
  _assertClassBrand(_MediaOverlay_brand, this, _stop).call(this);
  _classPrivateFieldSet(_audioIndex, this, audioIndex);
  _classPrivateFieldSet(_itemIndex, this, itemIndex);
  const src = (_classPrivateGetter3 = _classPrivateGetter(_MediaOverlay_brand, this, _get_activeAudio)) === null || _classPrivateGetter3 === void 0 ? void 0 : _classPrivateGetter3.src;
  if (!src || !_classPrivateGetter(_MediaOverlay_brand, this, _get_activeItem)) return this.start(_classPrivateFieldGet(_sectionIndex, this) + 1);
  const url = URL.createObjectURL(await this.book.loadBlob(src));
  const audio = new Audio(url);
  _classPrivateFieldSet(_audio, this, audio);
  audio.volume = _classPrivateFieldGet(_volume, this);
  audio.playbackRate = _classPrivateFieldGet(_rate, this);
  audio.addEventListener('timeupdate', () => {
    var _classPrivateGetter4;
    if (audio.paused) return;
    const t = audio.currentTime;
    const {
      items
    } = _classPrivateGetter(_MediaOverlay_brand, this, _get_activeAudio);
    if (t > ((_classPrivateGetter4 = _classPrivateGetter(_MediaOverlay_brand, this, _get_activeItem)) === null || _classPrivateGetter4 === void 0 ? void 0 : _classPrivateGetter4.end)) {
      _assertClassBrand(_MediaOverlay_brand, this, _unhighlight).call(this);
      if (_classPrivateFieldGet(_itemIndex, this) === items.length - 1) {
        _assertClassBrand(_MediaOverlay_brand, this, _play).call(this, _classPrivateFieldGet(_audioIndex, this) + 1, 0).catch(e => _assertClassBrand(_MediaOverlay_brand, this, _error).call(this, e));
        return;
      }
    }
    const oldIndex = _classPrivateFieldGet(_itemIndex, this);
    while (((_items = items[_classPrivateFieldGet(_itemIndex, this) + 1]) === null || _items === void 0 ? void 0 : _items.begin) <= t) {
      var _this$itemIndex, _this$itemIndex2, _items;
      _classPrivateFieldSet(_itemIndex, this, (_this$itemIndex = _classPrivateFieldGet(_itemIndex, this), _this$itemIndex2 = _this$itemIndex++, _this$itemIndex)), _this$itemIndex2;
    }
    if (_classPrivateFieldGet(_itemIndex, this) !== oldIndex) _assertClassBrand(_MediaOverlay_brand, this, _highlight).call(this);
  });
  audio.addEventListener('error', () => _assertClassBrand(_MediaOverlay_brand, this, _error).call(this, new Error(`Failed to load ${src}`)));
  audio.addEventListener('playing', () => _assertClassBrand(_MediaOverlay_brand, this, _highlight).call(this));
  audio.addEventListener('ended', () => {
    _assertClassBrand(_MediaOverlay_brand, this, _unhighlight).call(this);
    URL.revokeObjectURL(url);
    _classPrivateFieldSet(_audio, this, null);
    _assertClassBrand(_MediaOverlay_brand, this, _play).call(this, audioIndex + 1, 0).catch(e => _assertClassBrand(_MediaOverlay_brand, this, _error).call(this, e));
  });
  if (_classPrivateFieldGet(_state, this) === 'paused') {
    var _classPrivateGetter$b;
    _assertClassBrand(_MediaOverlay_brand, this, _highlight).call(this);
    audio.currentTime = (_classPrivateGetter$b = _classPrivateGetter(_MediaOverlay_brand, this, _get_activeItem).begin) !== null && _classPrivateGetter$b !== void 0 ? _classPrivateGetter$b : 0;
  } else audio.addEventListener('canplaythrough', () => {
    var _classPrivateGetter$b2;
    // for some reason need to seek in `canplaythrough`
    // or it won't play when skipping in WebKit
    audio.currentTime = (_classPrivateGetter$b2 = _classPrivateGetter(_MediaOverlay_brand, this, _get_activeItem).begin) !== null && _classPrivateGetter$b2 !== void 0 ? _classPrivateGetter$b2 : 0;
    _classPrivateFieldSet(_state, this, 'playing');
    audio.play().catch(e => _assertClassBrand(_MediaOverlay_brand, this, _error).call(this, e));
  }, {
    once: true
  });
}
function _stop() {
  if (_classPrivateFieldGet(_audio, this)) {
    _classPrivateFieldGet(_audio, this).pause();
    URL.revokeObjectURL(_classPrivateFieldGet(_audio, this).src);
    _classPrivateFieldSet(_audio, this, null);
    _assertClassBrand(_MediaOverlay_brand, this, _unhighlight).call(this);
  }
}
const isUUID = /([0-9a-f]{8})-([0-9a-f]{4})-([0-9a-f]{4})-([0-9a-f]{4})-([0-9a-f]{12})/;
const getUUID = opf => {
  for (const el of opf.getElementsByTagNameNS(NS.DC, 'identifier')) {
    const [id] = getElementText(el).split(':').slice(-1);
    if (isUUID.test(id)) return id;
  }
  return '';
};
const getIdentifier = opf => {
  var _opf$getElementById;
  return getElementText((_opf$getElementById = opf.getElementById(opf.documentElement.getAttribute('unique-identifier'))) !== null && _opf$getElementById !== void 0 ? _opf$getElementById : opf.getElementsByTagNameNS(NS.DC, 'identifier')[0]);
};

// https://www.w3.org/publishing/epub32/epub-ocf.html#sec-resource-obfuscation
const deobfuscate = async (key, length, blob) => {
  const array = new Uint8Array(await blob.slice(0, length).arrayBuffer());
  length = Math.min(length, array.length);
  for (var i = 0; i < length; i++) array[i] = array[i] ^ key[i % key.length];
  return new Blob([array, blob.slice(length)], {
    type: blob.type
  });
};
const WebCryptoSHA1 = async str => {
  const data = new TextEncoder().encode(str);
  const buffer = await globalThis.crypto.subtle.digest('SHA-1', data);
  return new Uint8Array(buffer);
};
const deobfuscators = (sha1 = WebCryptoSHA1) => ({
  'http://www.idpf.org/2008/embedding': {
    key: opf => sha1(getIdentifier(opf)
    // eslint-disable-next-line no-control-regex
    .replaceAll(/[\u0020\u0009\u000d\u000a]/g, '')),
    decode: (key, blob) => deobfuscate(key, 1040, blob)
  },
  'http://ns.adobe.com/pdf/enc#RC': {
    key: opf => {
      const uuid = getUUID(opf).replaceAll('-', '');
      return Uint8Array.from({
        length: 16
      }, (_, i) => parseInt(uuid.slice(i * 2, i * 2 + 2), 16));
    },
    decode: (key, blob) => deobfuscate(key, 1024, blob)
  }
});
var _uris = /*#__PURE__*/new WeakMap();
var _decoders = /*#__PURE__*/new WeakMap();
var _algorithms = /*#__PURE__*/new WeakMap();
class Encryption {
  constructor(algorithms) {
    _classPrivateFieldInitSpec(this, _uris, new Map());
    _classPrivateFieldInitSpec(this, _decoders, new Map());
    _classPrivateFieldInitSpec(this, _algorithms, void 0);
    _classPrivateFieldSet(_algorithms, this, algorithms);
  }
  async init(encryption, opf) {
    if (!encryption) return;
    const data = Array.from(encryption.getElementsByTagNameNS(NS.ENC, 'EncryptedData'), el => {
      var _el$getElementsByTagN, _el$getElementsByTagN2;
      return {
        algorithm: (_el$getElementsByTagN = el.getElementsByTagNameNS(NS.ENC, 'EncryptionMethod')[0]) === null || _el$getElementsByTagN === void 0 ? void 0 : _el$getElementsByTagN.getAttribute('Algorithm'),
        uri: (_el$getElementsByTagN2 = el.getElementsByTagNameNS(NS.ENC, 'CipherReference')[0]) === null || _el$getElementsByTagN2 === void 0 ? void 0 : _el$getElementsByTagN2.getAttribute('URI')
      };
    });
    for (const {
      algorithm,
      uri
    } of data) {
      if (!_classPrivateFieldGet(_decoders, this).has(algorithm)) {
        const algo = _classPrivateFieldGet(_algorithms, this)[algorithm];
        if (!algo) {
          console.warn('Unknown encryption algorithm');
          continue;
        }
        const key = await algo.key(opf);
        _classPrivateFieldGet(_decoders, this).set(algorithm, blob => algo.decode(key, blob));
      }
      _classPrivateFieldGet(_uris, this).set(uri, algorithm);
    }
  }
  getDecoder(uri) {
    var _classPrivateFieldGet5;
    return (_classPrivateFieldGet5 = _classPrivateFieldGet(_decoders, this).get(_classPrivateFieldGet(_uris, this).get(uri))) !== null && _classPrivateFieldGet5 !== void 0 ? _classPrivateFieldGet5 : x => x;
  }
}
class Resources {
  constructor({
    opf,
    resolveHref
  }) {
    var _this$getItemByProper, _ref3, _this$getItemByID, _ref4, _this$getItemByProper2, _$$$$find, _this$guide;
    this.opf = opf;
    const {
      $,
      $$,
      $$$
    } = childGetter(opf, NS.OPF);
    const $manifest = $(opf.documentElement, 'manifest');
    const $spine = $(opf.documentElement, 'spine');
    const $$itemref = $$($spine, 'itemref');
    this.manifest = $$($manifest, 'item').map(getAttributes('href', 'id', 'media-type', 'properties', 'media-overlay')).map(item => {
      var _item$properties;
      item.href = resolveHref(item.href);
      item.properties = (_item$properties = item.properties) === null || _item$properties === void 0 ? void 0 : _item$properties.split(/\s/);
      return item;
    });
    this.manifestById = new Map(this.manifest.map(item => [item.id, item]));
    this.spine = $$itemref.map(getAttributes('idref', 'id', 'linear', 'properties')).map(item => {
      var _item$properties2;
      return item.properties = (_item$properties2 = item.properties) === null || _item$properties2 === void 0 ? void 0 : _item$properties2.split(/\s/), item;
    });
    this.pageProgressionDirection = $spine.getAttribute('page-progression-direction');
    this.navPath = (_this$getItemByProper = this.getItemByProperty('nav')) === null || _this$getItemByProper === void 0 ? void 0 : _this$getItemByProper.href;
    this.ncxPath = (_ref3 = (_this$getItemByID = this.getItemByID($spine.getAttribute('toc'))) !== null && _this$getItemByID !== void 0 ? _this$getItemByID : this.manifest.find(item => item.mediaType === MIME.NCX)) === null || _ref3 === void 0 ? void 0 : _ref3.href;
    const $guide = $(opf.documentElement, 'guide');
    if ($guide) this.guide = $$($guide, 'reference').map(getAttributes('type', 'title', 'href')).map(({
      type,
      title,
      href
    }) => ({
      label: title,
      type: type.split(/\s/),
      href: resolveHref(href)
    }));
    this.cover = (_ref4 = (_this$getItemByProper2 = this.getItemByProperty('cover-image')
    // EPUB 2 compat
    ) !== null && _this$getItemByProper2 !== void 0 ? _this$getItemByProper2 : this.getItemByID((_$$$$find = $$$(opf, 'meta').find(filterAttribute('name', 'cover'))) === null || _$$$$find === void 0 ? void 0 : _$$$$find.getAttribute('content'))) !== null && _ref4 !== void 0 ? _ref4 : this.getItemByHref((_this$guide = this.guide) === null || _this$guide === void 0 || (_this$guide = _this$guide.find(ref => ref.type.includes('cover'))) === null || _this$guide === void 0 ? void 0 : _this$guide.href);
    this.cfis = CFI.fromElements($$itemref);
  }
  getItemByID(id) {
    return this.manifestById.get(id);
  }
  getItemByHref(href) {
    return this.manifest.find(item => item.href === href);
  }
  getItemByProperty(prop) {
    return this.manifest.find(item => {
      var _item$properties3;
      return (_item$properties3 = item.properties) === null || _item$properties3 === void 0 ? void 0 : _item$properties3.includes(prop);
    });
  }
  resolveCFI(cfi) {
    var _parts$parent, _$itemref;
    const parts = CFI.parse(cfi);
    const top = ((_parts$parent = parts.parent) !== null && _parts$parent !== void 0 ? _parts$parent : parts).shift();
    let $itemref = CFI.toElement(this.opf, top);
    // make sure it's an idref; if not, try again without the ID assertion
    // mainly because Epub.js used to generate wrong ID assertions
    // https://github.com/futurepress/epub.js/issues/1236
    if ($itemref && $itemref.nodeName !== 'idref') {
      top.at(-1).id = null;
      $itemref = CFI.toElement(this.opf, top);
    }
    const idref = (_$itemref = $itemref) === null || _$itemref === void 0 ? void 0 : _$itemref.getAttribute('idref');
    const index = this.spine.findIndex(item => item.idref === idref);
    const anchor = doc => CFI.toRange(doc, parts);
    return {
      index,
      anchor
    };
  }
}
var _cache = /*#__PURE__*/new WeakMap();
var _children = /*#__PURE__*/new WeakMap();
var _refCount = /*#__PURE__*/new WeakMap();
class Loader {
  constructor({
    loadText,
    loadBlob,
    resources
  }) {
    _classPrivateFieldInitSpec(this, _cache, new Map());
    _classPrivateFieldInitSpec(this, _children, new Map());
    _classPrivateFieldInitSpec(this, _refCount, new Map());
    _defineProperty(this, "eventTarget", new EventTarget());
    this.loadText = loadText;
    this.loadBlob = loadBlob;
    this.manifest = resources.manifest;
    this.assets = resources.manifest;
    // needed only when replacing in (X)HTML w/o parsing (see below)
    //.filter(({ mediaType }) => ![MIME.XHTML, MIME.HTML].includes(mediaType))
  }
  async createURL(href, data, type, parent) {
    if (!data) return '';
    const detail = {
      data,
      type
    };
    Object.defineProperty(detail, 'name', {
      value: href
    }); // readonly
    const event = new CustomEvent('data', {
      detail
    });
    this.eventTarget.dispatchEvent(event);
    const newData = await event.detail.data;
    const newType = await event.detail.type;
    const url = URL.createObjectURL(new Blob([newData], {
      type: newType
    }));
    _classPrivateFieldGet(_cache, this).set(href, url);
    _classPrivateFieldGet(_refCount, this).set(href, 1);
    if (parent) {
      const childList = _classPrivateFieldGet(_children, this).get(parent);
      if (childList) childList.push(href);else _classPrivateFieldGet(_children, this).set(parent, [href]);
    }
    return url;
  }
  ref(href, parent) {
    const childList = _classPrivateFieldGet(_children, this).get(parent);
    if (!(childList !== null && childList !== void 0 && childList.includes(href))) {
      _classPrivateFieldGet(_refCount, this).set(href, _classPrivateFieldGet(_refCount, this).get(href) + 1);
      //console.log(`referencing ${href}, now ${this.#refCount.get(href)}`)
      if (childList) childList.push(href);else _classPrivateFieldGet(_children, this).set(parent, [href]);
    }
    return _classPrivateFieldGet(_cache, this).get(href);
  }
  unref(href) {
    if (!_classPrivateFieldGet(_refCount, this).has(href)) return;
    const count = _classPrivateFieldGet(_refCount, this).get(href) - 1;
    //console.log(`unreferencing ${href}, now ${count}`)
    if (count < 1) {
      //console.log(`unloading ${href}`)
      URL.revokeObjectURL(_classPrivateFieldGet(_cache, this).get(href));
      _classPrivateFieldGet(_cache, this).delete(href);
      _classPrivateFieldGet(_refCount, this).delete(href);
      // unref children
      const childList = _classPrivateFieldGet(_children, this).get(href);
      if (childList) while (childList.length) this.unref(childList.pop());
      _classPrivateFieldGet(_children, this).delete(href);
    } else _classPrivateFieldGet(_refCount, this).set(href, count);
  }
  // load manifest item, recursively loading all resources as needed
  async loadItem(item, parents = []) {
    if (!item) return null;
    const {
      href,
      mediaType
    } = item;
    const isScript = MIME.JS.test(item.mediaType);
    const detail = {
      type: mediaType,
      isScript,
      allow: true
    };
    const event = new CustomEvent('load', {
      detail
    });
    this.eventTarget.dispatchEvent(event);
    const allow = await event.detail.allow;
    if (!allow) return null;
    const parent = parents.at(-1);
    if (_classPrivateFieldGet(_cache, this).has(href)) return this.ref(href, parent);
    const shouldReplace = (isScript || [MIME.XHTML, MIME.HTML, MIME.CSS, MIME.SVG].includes(mediaType)
    // prevent circular references
    ) && parents.every(p => p !== href);
    if (shouldReplace) return this.loadReplaced(item, parents);
    // NOTE: this can be replaced with `Promise.try()`
    const tryLoadBlob = Promise.resolve().then(() => this.loadBlob(href));
    return this.createURL(href, tryLoadBlob, mediaType, parent);
  }
  async loadHref(href, base, parents = []) {
    if (isExternal(href)) return href;
    const path = resolveURL(href, base);
    const item = this.manifest.find(item => item.href === path);
    if (!item) return href;
    return this.loadItem(item, parents.concat(base));
  }
  async loadReplaced(item, parents = []) {
    const {
      href,
      mediaType
    } = item;
    const parent = parents.at(-1);
    let str = '';
    try {
      str = await this.loadText(href);
    } catch (e) {
      return this.createURL(href, Promise.reject(e), mediaType, parent);
    }
    if (!str) return null;

    // note that one can also just use `replaceString` for everything:
    // ```
    // const replaced = await this.replaceString(str, href, parents)
    // return this.createURL(href, replaced, mediaType, parent)
    // ```
    // which is basically what Epub.js does, which is simpler, but will
    // break things like iframes (because you don't want to replace links)
    // or text that just happen to be paths

    // parse and replace in HTML
    if ([MIME.XHTML, MIME.HTML, MIME.SVG].includes(mediaType)) {
      var _doc$documentElement;
      let doc = new DOMParser().parseFromString(str, mediaType);
      // change to HTML if it's not valid XHTML
      if (mediaType === MIME.XHTML && (doc.querySelector('parsererror') || !((_doc$documentElement = doc.documentElement) !== null && _doc$documentElement !== void 0 && _doc$documentElement.namespaceURI))) {
        var _doc$querySelector$in, _doc$querySelector;
        console.warn((_doc$querySelector$in = (_doc$querySelector = doc.querySelector('parsererror')) === null || _doc$querySelector === void 0 ? void 0 : _doc$querySelector.innerText) !== null && _doc$querySelector$in !== void 0 ? _doc$querySelector$in : 'Invalid XHTML');
        item.mediaType = MIME.HTML;
        // Librera: an empty element written the XML way stays open in HTML, where `<a/>`
        // is a start tag only - libmobi writes one per removed filepos, and the link it
        // opens takes in the text after it, painted in the reader's link colour. Written
        // out as start and end, as XML means it, for every element HTML does not close
        // itself (`<br/>`, `<img/>` and the other void ones are fine as they are).
        str = str.replace(/<(?!(?:area|base|br|col|embed|hr|img|input|keygen|link|meta|param|source|track|wbr)[\s/>])([A-Za-z][\w:.-]*)((?:\s[^<>]*?)?)\s*\/>/gi, '<$1$2></$1>');
        doc = new DOMParser().parseFromString(str, item.mediaType);
      }
      // replace hrefs in XML processing instructions
      // this is mainly for SVGs that use xml-stylesheet
      if ([MIME.XHTML, MIME.SVG].includes(item.mediaType)) {
        let child = doc.firstChild;
        while (child instanceof ProcessingInstruction) {
          if (child.data) {
            const replacedData = await replaceSeries(child.data, /(?:^|\s*)(href\s*=\s*['"])([^'"]*)(['"])/i, (_, p1, p2, p3) => this.loadHref(p2, href, parents).then(p2 => `${p1}${p2}${p3}`));
            child.replaceWith(doc.createProcessingInstruction(child.target, replacedData));
          }
          child = child.nextSibling;
        }
      }
      // replace hrefs (excluding anchors)
      const replace = async (el, attr) => el.setAttribute(attr, await this.loadHref(el.getAttribute(attr), href, parents));
      for (const el of doc.querySelectorAll('link[href]')) await replace(el, 'href');
      for (const el of doc.querySelectorAll('[src]')) await replace(el, 'src');
      for (const el of doc.querySelectorAll('[poster]')) await replace(el, 'poster');
      for (const el of doc.querySelectorAll('object[data]')) await replace(el, 'data');
      for (const el of doc.querySelectorAll('[*|href]:not([href])')) el.setAttributeNS(NS.XLINK, 'href', await this.loadHref(el.getAttributeNS(NS.XLINK, 'href'), href, parents));
      for (const el of doc.querySelectorAll('[srcset]')) el.setAttribute('srcset', await replaceSeries(el.getAttribute('srcset'), /(\s*)(.+?)\s*((?:\s[\d.]+[wx])+\s*(?:,|$)|,\s+|$)/g, (_, p1, p2, p3) => this.loadHref(p2, href, parents).then(p2 => `${p1}${p2}${p3}`)));
      // replace inline styles
      for (const el of doc.querySelectorAll('style')) if (el.textContent) el.textContent = await this.replaceCSS(el.textContent, href, parents);
      for (const el of doc.querySelectorAll('[style]')) el.setAttribute('style', await this.replaceCSS(el.getAttribute('style'), href, parents));
      // TODO: replace inline scripts? probably not worth the trouble
      const result = new XMLSerializer().serializeToString(doc);
      return this.createURL(href, result, item.mediaType, parent);
    }
    const result = mediaType === MIME.CSS ? await this.replaceCSS(str, href, parents) : await this.replaceString(str, href, parents);
    return this.createURL(href, result, mediaType, parent);
  }
  async replaceCSS(str, href, parents = []) {
    const replacedUrls = await replaceSeries(str, /url\(\s*["']?([^'"\n]*?)\s*["']?\s*\)/gi, (_, url) => this.loadHref(url, href, parents).then(url => `url("${url}")`));
    // apart from `url()`, strings can be used for `@import` (but why?!)
    return replaceSeries(replacedUrls, /@import\s*["']([^"'\n]*?)["']/gi, (_, url) => this.loadHref(url, href, parents).then(url => `@import "${url}"`));
  }
  // find & replace all possible relative paths for all assets without parsing
  replaceString(str, href, parents = []) {
    const assetMap = new Map();
    const urls = this.assets.map(asset => {
      // do not replace references to the file itself
      if (asset.href === href) return;
      // href was decoded and resolved when parsing the manifest
      const relative = pathRelative(pathDirname(href), asset.href);
      const relativeEnc = encodeURI(relative);
      const rootRelative = '/' + asset.href;
      const rootRelativeEnc = encodeURI(rootRelative);
      const set = new Set([relative, relativeEnc, rootRelative, rootRelativeEnc]);
      for (const url of set) assetMap.set(url, asset);
      return Array.from(set);
    }).flat().filter(x => x);
    if (!urls.length) return str;
    const regex = new RegExp(urls.map(regexEscape).join('|'), 'g');
    return replaceSeries(str, regex, async match => this.loadItem(assetMap.get(match.replace(/^\//, '')), parents.concat(href)));
  }
  unloadItem(item) {
    this.unref(item === null || item === void 0 ? void 0 : item.href);
  }
  destroy() {
    for (const url of _classPrivateFieldGet(_cache, this).values()) URL.revokeObjectURL(url);
  }
}
const getHTMLFragment = (doc, id) => {
  var _doc$getElementById;
  return (_doc$getElementById = doc.getElementById(id)) !== null && _doc$getElementById !== void 0 ? _doc$getElementById : doc.querySelector(`[name="${CSS.escape(id)}"]`);
};
const getPageSpread = properties => {
  for (const p of properties) {
    if (p === 'page-spread-left' || p === 'rendition:page-spread-left') return 'left';
    if (p === 'page-spread-right' || p === 'rendition:page-spread-right') return 'right';
    if (p === 'rendition:page-spread-center') return 'center';
  }
};
const getDisplayOptions = doc => {
  if (!doc) return null;
  return {
    fixedLayout: getElementText(doc.querySelector('option[name="fixed-layout"]')),
    openToSpread: getElementText(doc.querySelector('option[name="open-to-spread"]'))
  };
};
var _loader = /*#__PURE__*/new WeakMap();
var _encryption = /*#__PURE__*/new WeakMap();
var _EPUB_brand = /*#__PURE__*/new WeakSet();
export class EPUB {
  constructor({
    loadText,
    loadBlob,
    getSize,
    sha1
  }) {
    _classPrivateMethodInitSpec(this, _EPUB_brand);
    _defineProperty(this, "parser", new DOMParser());
    _classPrivateFieldInitSpec(this, _loader, void 0);
    _classPrivateFieldInitSpec(this, _encryption, void 0);
    this.loadText = loadText;
    this.loadBlob = loadBlob;
    this.getSize = getSize;
    _classPrivateFieldSet(_encryption, this, new Encryption(deobfuscators(sha1)));
  }
  async init() {
    var _this$landmarks, _await$_assertClassBr;
    const $container = await _assertClassBrand(_EPUB_brand, this, _loadXML).call(this, 'META-INF/container.xml');
    if (!$container) throw new Error('Failed to load container file');
    const opfs = Array.from($container.getElementsByTagNameNS(NS.CONTAINER, 'rootfile'), getAttributes('full-path', 'media-type')).filter(file => file.mediaType === 'application/oebps-package+xml');
    if (!opfs.length) throw new Error('No package document defined in container');
    const opfPath = opfs[0].fullPath;
    const opf = await _assertClassBrand(_EPUB_brand, this, _loadXML).call(this, opfPath);
    if (!opf) throw new Error('Failed to load package document');
    const $encryption = await _assertClassBrand(_EPUB_brand, this, _loadXML).call(this, 'META-INF/encryption.xml');
    await _classPrivateFieldGet(_encryption, this).init($encryption, opf);
        this.resources = new Resources({
      opf,
      resolveHref: url => resolveURL(url, opfPath)
    });
    _classPrivateFieldSet(_loader, this, new Loader({
      loadText: this.loadText,
      loadBlob: uri => Promise.resolve(this.loadBlob(uri)).then(_classPrivateFieldGet(_encryption, this).getDecoder(uri)),
      resources: this.resources
    }));
    this.transformTarget = _classPrivateFieldGet(_loader, this).eventTarget;
    this.sections = this.resources.spine.map((spineItem, index) => {
      const {
        idref,
        linear,
        properties = []
      } = spineItem;
      const item = this.resources.getItemByID(idref);
      if (!item) {
        console.warn(`Could not find item with ID "${idref}" in manifest`);
        return null;
      }
      return {
        id: item.href,
        load: () => _classPrivateFieldGet(_loader, this).loadItem(item),
        unload: () => _classPrivateFieldGet(_loader, this).unloadItem(item),
        createDocument: () => this.loadDocument(item),
        size: this.getSize(item.href),
        cfi: this.resources.cfis[index],
        linear,
        pageSpread: getPageSpread(properties),
        resolveHref: href => resolveURL(href, item.href),
        mediaOverlay: item.mediaOverlay ? this.resources.getItemByID(item.mediaOverlay) : null
      };
    }).filter(s => s);
    const {
      navPath,
      ncxPath
    } = this.resources;
    if (navPath) try {
      const resolve = url => resolveURL(url, navPath);
      const nav = parseNav(await _assertClassBrand(_EPUB_brand, this, _loadXML).call(this, navPath), resolve);
      this.toc = nav.toc;
      this.pageList = nav.pageList;
      this.landmarks = nav.landmarks;
    } catch (e) {
      console.warn(e);
    }
    if (!this.toc && ncxPath) try {
      const resolve = url => resolveURL(url, ncxPath);
      const ncx = parseNCX(await _assertClassBrand(_EPUB_brand, this, _loadXML).call(this, ncxPath), resolve);
      this.toc = ncx.toc;
      this.pageList = ncx.pageList;
    } catch (e) {
      console.warn(e);
    }
    (_this$landmarks = this.landmarks) !== null && _this$landmarks !== void 0 ? _this$landmarks : this.landmarks = this.resources.guide;
    const {
      metadata,
      rendition,
      media
    } = getMetadata(opf);
    this.metadata = metadata;
    this.rendition = rendition;
    this.media = media;
    this.dir = this.resources.pageProgressionDirection;
    const displayOptions = getDisplayOptions((_await$_assertClassBr = await _assertClassBrand(_EPUB_brand, this, _loadXML).call(this, 'META-INF/com.apple.ibooks.display-options.xml')) !== null && _await$_assertClassBr !== void 0 ? _await$_assertClassBr : await _assertClassBrand(_EPUB_brand, this, _loadXML).call(this, 'META-INF/com.kobobooks.display-options.xml'));
    if (displayOptions) {
      var _this$rendition, _this$rendition$layou, _this$sections$find, _this$sections$find$p;
      if (displayOptions.fixedLayout === 'true') (_this$rendition$layou = (_this$rendition = this.rendition).layout) !== null && _this$rendition$layou !== void 0 ? _this$rendition$layou : _this$rendition.layout = 'pre-paginated';
      if (displayOptions.openToSpread === 'false') (_this$sections$find$p = (_this$sections$find = this.sections.find(section => section.linear !== 'no')).pageSpread) !== null && _this$sections$find$p !== void 0 ? _this$sections$find$p : _this$sections$find.pageSpread = this.dir === 'rtl' ? 'left' : 'right';
    }
    return this;
  }
  async loadDocument(item) {
    const str = await this.loadText(item.href);
    return this.parser.parseFromString(str, item.mediaType);
  }
  getMediaOverlay() {
    return new MediaOverlay(this, _assertClassBrand(_EPUB_brand, this, _loadXML).bind(this));
  }
  resolveCFI(cfi) {
    return this.resources.resolveCFI(cfi);
  }
  resolveHref(href) {
    const [path, hash] = href.split('#');
    const item = this.resources.getItemByHref(decodeURI(path));
    if (!item) return null;
    const index = this.resources.spine.findIndex(({
      idref
    }) => idref === item.id);
    const anchor = hash ? doc => getHTMLFragment(doc, hash) : () => 0;
    return {
      index,
      anchor
    };
  }
  splitTOCHref(href) {
    var _href$split;
    return (_href$split = href === null || href === void 0 ? void 0 : href.split('#')) !== null && _href$split !== void 0 ? _href$split : [];
  }
  getTOCFragment(doc, id) {
    var _doc$getElementById2;
    return (_doc$getElementById2 = doc.getElementById(id)) !== null && _doc$getElementById2 !== void 0 ? _doc$getElementById2 : doc.querySelector(`[name="${CSS.escape(id)}"]`);
  }
  isExternal(uri) {
    return isExternal(uri);
  }
  async getCover() {
    var _this$resources;
    const cover = (_this$resources = this.resources) === null || _this$resources === void 0 ? void 0 : _this$resources.cover;
    return cover !== null && cover !== void 0 && cover.href ? new Blob([await this.loadBlob(cover.href)], {
      type: cover.mediaType
    }) : null;
  }
  async getCalibreBookmarks() {
    const txt = await this.loadText('META-INF/calibre_bookmarks.txt');
    const magic = 'encoding=json+base64:';
    if (txt !== null && txt !== void 0 && txt.startsWith(magic)) {
      const json = atob(txt.slice(magic.length));
      return JSON.parse(json);
    }
  }
  destroy() {
    var _classPrivateFieldGet6;
    (_classPrivateFieldGet6 = _classPrivateFieldGet(_loader, this)) === null || _classPrivateFieldGet6 === void 0 || _classPrivateFieldGet6.destroy();
  }
}
async function _loadXML(uri) {
  const str = await this.loadText(uri);
  if (!str) return null;
  const doc = this.parser.parseFromString(str, MIME.XML);
  if (doc.querySelector('parsererror')) throw new Error(`XML parsing error: ${uri}
${doc.querySelector('parsererror').innerText}`);
  return doc;
}
