const walkRange = (range, walker) => {
  const nodes = [];
  for (let node = walker.currentNode; node; node = walker.nextNode()) {
    const compare = range.comparePoint(node, 0);
    if (compare === 0) nodes.push(node);else if (compare > 0) break;
  }
  return nodes;
};
const walkDocument = (_, walker) => {
  const nodes = [];
  for (let node = walker.nextNode(); node; node = walker.nextNode()) nodes.push(node);
  return nodes;
};
const filter = NodeFilter.SHOW_ELEMENT | NodeFilter.SHOW_TEXT | NodeFilter.SHOW_CDATA_SECTION;
const acceptNode = node => {
  if (node.nodeType === 1) {
    const name = node.tagName.toLowerCase();
    if (name === 'script' || name === 'style') return NodeFilter.FILTER_REJECT;
    return NodeFilter.FILTER_SKIP;
  }
  return NodeFilter.FILTER_ACCEPT;
};
export const textWalker = function* (x, func, filterFunc) {
  var _ref, _x$commonAncestorCont;
  const root = (_ref = (_x$commonAncestorCont = x.commonAncestorContainer) !== null && _x$commonAncestorCont !== void 0 ? _x$commonAncestorCont : x.body) !== null && _ref !== void 0 ? _ref : x;
  const walker = document.createTreeWalker(root, filter, {
    acceptNode: filterFunc || acceptNode
  });
  const walk = x.commonAncestorContainer ? walkRange : walkDocument;
  const nodes = walk(x, walker);
  const strs = nodes.map(node => {
    var _node$nodeValue;
    return (_node$nodeValue = node.nodeValue) !== null && _node$nodeValue !== void 0 ? _node$nodeValue : '';
  });
  const makeRange = (startIndex, startOffset, endIndex, endOffset) => {
    const range = document.createRange();
    range.setStart(nodes[startIndex], startOffset);
    range.setEnd(nodes[endIndex], endOffset);
    return range;
  };
  for (const match of func(strs, makeRange)) yield match;
};