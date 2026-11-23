function _classPrivateMethodInitSpec(e, a) { _checkPrivateRedeclaration(e, a), a.add(e); }
function _checkPrivateRedeclaration(e, t) { if (t.has(e)) throw new TypeError("Cannot initialize the same private elements twice on an object"); }
function _assertClassBrand(e, t, n) { if ("function" == typeof e ? e === t : e.has(t)) return arguments.length < 3 ? t : n; throw new TypeError("Private element is not present on this object"); }
// assign a unique ID for each TOC item
const assignIDs = toc => {
  let id = 0;
  const assignID = item => {
    item.id = id++;
    if (item.subitems) for (const subitem of item.subitems) assignID(subitem);
  };
  for (const item of toc) assignID(item);
  return toc;
};
const flatten = items => items.map(item => {
  var _item$subitems;
  return (_item$subitems = item.subitems) !== null && _item$subitems !== void 0 && _item$subitems.length ? [item, flatten(item.subitems)].flat() : item;
}).flat();
export class TOCProgress {
  async init({
    toc,
    ids,
    splitHref,
    getFragment
  }) {
    assignIDs(toc);
    const items = flatten(toc);
    const grouped = new Map();
    for (const [i, item] of items.entries()) {
      var _await$splitHref;
      const [id, fragment] = (_await$splitHref = await splitHref(item === null || item === void 0 ? void 0 : item.href)) !== null && _await$splitHref !== void 0 ? _await$splitHref : [];
      const value = {
        fragment,
        item
      };
      if (grouped.has(id)) grouped.get(id).items.push(value);else grouped.set(id, {
        prev: items[i - 1],
        items: [value]
      });
    }
    const map = new Map();
    for (const [i, id] of ids.entries()) {
      if (grouped.has(id)) map.set(id, grouped.get(id));else map.set(id, map.get(ids[i - 1]));
    }
    this.ids = ids;
    this.map = map;
    this.getFragment = getFragment;
  }
  getProgress(index, range) {
    if (!this.ids) return;
    const id = this.ids[index];
    const obj = this.map.get(id);
    if (!obj) return null;
    const {
      prev,
      items
    } = obj;
    if (!items) return prev;
    if (!range || items.length === 1 && !items[0].fragment) return items[0].item;
    const doc = range.startContainer.getRootNode();
    for (const [i, {
      fragment
    }] of items.entries()) {
      var _items$item, _items;
      const el = this.getFragment(doc, fragment);
      if (!el) continue;
      if (range.comparePoint(el, 0) > 0) return (_items$item = (_items = items[i - 1]) === null || _items === void 0 ? void 0 : _items.item) !== null && _items$item !== void 0 ? _items$item : prev;
    }
    return items[items.length - 1].item;
  }
}
var _SectionProgress_brand = /*#__PURE__*/new WeakSet();
export class SectionProgress {
  constructor(sections, sizePerLoc, sizePerTimeUnit) {
    _classPrivateMethodInitSpec(this, _SectionProgress_brand);
    this.sizes = sections.map(s => s.linear != 'no' && s.size > 0 ? s.size : 0);
    this.sizePerLoc = sizePerLoc;
    this.sizePerTimeUnit = sizePerTimeUnit;
    this.sizeTotal = this.sizes.reduce((a, b) => a + b, 0);
    this.sectionFractions = _assertClassBrand(_SectionProgress_brand, this, _getSectionFractions).call(this);
  }
  // get progress given index of and fractions within a section
  getProgress(index, fractionInSection, pageFraction = 0) {
    var _sizes$index;
    const {
      sizes,
      sizePerLoc,
      sizePerTimeUnit,
      sizeTotal
    } = this;
    const sizeInSection = (_sizes$index = sizes[index]) !== null && _sizes$index !== void 0 ? _sizes$index : 0;
    const sizeBefore = sizes.slice(0, index).reduce((a, b) => a + b, 0);
    const size = sizeBefore + fractionInSection * sizeInSection;
    const nextSize = size + pageFraction * sizeInSection;
    const remainingTotal = sizeTotal - size;
    const remainingSection = (1 - fractionInSection) * sizeInSection;
    return {
      fraction: nextSize / sizeTotal,
      section: {
        current: index,
        total: sizes.length
      },
      location: {
        current: Math.floor(size / sizePerLoc),
        next: Math.floor(nextSize / sizePerLoc),
        total: Math.ceil(sizeTotal / sizePerLoc)
      },
      time: {
        section: remainingSection / sizePerTimeUnit,
        total: remainingTotal / sizePerTimeUnit
      }
    };
  }
  // the inverse of `getProgress`
  // get index of and fraction in section based on total fraction
  getSection(fraction) {
    if (fraction <= 0) return [0, 0];
    if (fraction >= 1) return [this.sizes.length - 1, 1];
    fraction = fraction + Number.EPSILON;
    const {
      sizeTotal
    } = this;
    let index = this.sectionFractions.findIndex(x => x > fraction) - 1;
    if (index < 0) return [0, 0];
    while (!this.sizes[index]) index++;
    const fractionInSection = (fraction - this.sectionFractions[index]) / (this.sizes[index] / sizeTotal);
    return [index, fractionInSection];
  }
}
function _getSectionFractions() {
  const {
    sizeTotal
  } = this;
  const results = [0];
  let sum = 0;
  for (const size of this.sizes) results.push((sum += size) / sizeTotal);
  return results;
}