/* WebView 70 predates a handful of builtins foliate-js relies on. These are the small,
   self-contained versions of them: nothing here reaches outside the page. */
(function () {
    if (!String.prototype.replaceAll) {
        String.prototype.replaceAll = function (search, replacement) {
            if (search instanceof RegExp) {
                if (!search.global) throw new TypeError('replaceAll must be called with a global RegExp');
                return this.replace(search, replacement);
            }
            var text = String(this);
            var find = String(search);
            if (find === '') {
                var chars = text.split('');
                var out = String(replacement);
                return out + chars.join(out) + out;
            }
            return text.split(find).join(String(replacement));
        };
    }
    if (!Array.prototype.at) {
        Array.prototype.at = function (index) {
            var length = this.length >>> 0;
            var k = Math.trunc(index) || 0;
            if (k < 0) k += length;
            return k < 0 || k >= length ? undefined : this[k];
        };
        if (!String.prototype.at) {
            String.prototype.at = function (index) {
                var text = String(this);
                return Array.prototype.at.call(text.split(''), index);
            };
        }
    }
    if (!Object.fromEntries) {
        Object.fromEntries = function (entries) {
            var result = {};
            var list = entries == null ? [] : entries;
            var iter = list[Symbol.iterator]();
            for (;;) {
                var step = iter.next();
                if (step.done) break;
                var pair = step.value;
                result[pair[0]] = pair[1];
            }
            return result;
        };
    }
    if (!String.prototype.matchAll) {
        String.prototype.matchAll = function (regexp) {
            var re = regexp instanceof RegExp ? regexp : new RegExp(regexp, 'g');
            if (!re.global) throw new TypeError('matchAll must be called with a global RegExp');
            var text = String(this);
            re.lastIndex = 0;
            var out = [];
            var match;
            while ((match = re.exec(text)) !== null) {
                out.push(match);
                if (match[0] === '') re.lastIndex++;
            }
            return out[Symbol.iterator]();
        };
    }
    if (!String.prototype.trimStart) {
        String.prototype.trimStart = function () { return this.replace(/^\s+/, ''); };
        String.prototype.trimEnd = function () { return this.replace(/\s+$/, ''); };
    }
    if (!Promise.allSettled) {
        Promise.allSettled = function (list) {
            return Promise.all(Array.from(list, function (item) {
                return Promise.resolve(item).then(
                    function (value) { return { status: 'fulfilled', value: value }; },
                    function (reason) { return { status: 'rejected', reason: reason }; });
            }));
        };
    }
    if (typeof globalThis === 'undefined') {
        window.globalThis = window;
    }
    if (!window.queueMicrotask) {
        window.queueMicrotask = function (fn) { Promise.resolve().then(fn); };
    }
    /* foliate asks Intl.Segmenter for word search only; when it is here, its own simple search
       already covers the reader's needs, so this stub keeps the constructor from throwing. */
    if (!window.Intl) window.Intl = {};
    if (!window.Intl.Segmenter) {
        window.Intl.Segmenter = function (locales, options) {
            this.segment = function (text) {
                var chars = String(text).split('');
                var items = chars.map(function (ch, i) { return { segment: ch, index: i, input: String(text) }; });
                return items[Symbol.iterator]();
            };
        };
    }
})();

/* Blob/File.prototype.arrayBuffer & text: Chrome <76 has neither. foliate's
   view.js decides "is this a File to be opened?" by typeof book.arrayBuffer,
   so without this the File object is treated as an already-made book and
   `book.sections` is undefined. */
(function () {
  if (typeof Blob === 'undefined') return;
  if (!Blob.prototype.arrayBuffer) {
    Blob.prototype.arrayBuffer = function () {
      var blob = this;
      return new Promise(function (resolve, reject) {
        var fr = new FileReader();
        fr.onload = function () { resolve(fr.result); };
        fr.onerror = function () { reject(fr.error); };
        fr.readAsArrayBuffer(blob);
      });
    };
  }
  if (!Blob.prototype.text) {
    Blob.prototype.text = function () {
      var blob = this;
      return new Promise(function (resolve, reject) {
        var fr = new FileReader();
        fr.onload = function () { resolve(fr.result); };
        fr.onerror = function () { reject(fr.error); };
        fr.readAsText(blob);
      });
    };
  }
  if (!File.prototype.arrayBuffer && Blob.prototype.arrayBuffer) File.prototype.arrayBuffer = Blob.prototype.arrayBuffer;
  if (!File.prototype.text && Blob.prototype.text) File.prototype.text = Blob.prototype.text;
})();
/* ParentNode.append/prepend/replaceChildren & ChildNode.before/after/replaceWith:
   Chrome <86 lacks replaceChildren, which foliate's paginator calls on every
   render. Implemented the standard way (accept Nodes and strings). */
(function () {
  var toNodes = function (doc, args) {
    return Array.prototype.map.call(args, function (a) {
      return a instanceof Node ? a : doc.createTextNode(String(a));
    });
  };
  var parentNode = typeof Document !== 'undefined' ? Document.prototype : null;
  var fragmentProto = typeof DocumentFragment !== 'undefined' ? DocumentFragment.prototype : null;
  var elementProto = typeof Element !== 'undefined' ? Element.prototype : null;
  var proto = elementProto || parentNode;
  if (proto) {
    if (!proto.append) proto.append = function () {
      var nodes = toNodes(this.ownerDocument || document, arguments);
      for (var i = 0; i < nodes.length; i++) this.appendChild(nodes[i]);
    };
    if (!proto.prepend) proto.prepend = function () {
      var nodes = toNodes(this.ownerDocument || document, arguments);
      var ref = this.firstChild;
      for (var i = 0; i < nodes.length; i++) this.insertBefore(nodes[i], ref);
    };
    if (!proto.replaceChildren) proto.replaceChildren = function () {
      var nodes = toNodes(this.ownerDocument || document, arguments);
      while (this.firstChild) this.removeChild(this.firstChild);
      for (var i = 0; i < nodes.length; i++) this.appendChild(nodes[i]);
    };
    if (!proto.before) proto.before = function () {
      var parent = this.parentNode;
      if (!parent) return;
      var nodes = toNodes(this.ownerDocument || document, arguments);
      for (var i = 0; i < nodes.length; i++) parent.insertBefore(nodes[i], this);
    };
    if (!proto.after) proto.after = function () {
      var parent = this.parentNode;
      if (!parent) return;
      var nodes = toNodes(this.ownerDocument || document, arguments);
      var next = this.nextSibling;
      for (var i = 0; i < nodes.length; i++) parent.insertBefore(nodes[i], next);
    };
    if (!proto.replaceWith) proto.replaceWith = function () {
      var parent = this.parentNode;
      if (!parent) return;
      var nodes = toNodes(this.ownerDocument || document, arguments);
      if (nodes.length) {
        this.before.apply(this, nodes);
      }
      parent.removeChild(this);
    };
  }
})();

/* Intl.Locale: absent before Chrome 74. foliate only reads .language (and
   occasionally .script), so a tiny value object is enough. */
(function () {
  if (typeof Intl === 'undefined' || Intl.Locale) return;
  function Locale(tag) {
    if (!(this instanceof Locale)) return new Locale(tag);
    var parts = String(tag || '').replace('_', '-').split('-');
    this.baseName = String(tag || '');
    this.language = (parts[0] || '').toLowerCase();
    this.region = '';
    this.script = '';
    for (var i = 1; i < parts.length; i++) {
      if (parts[i].length === 2 && /^[A-Za-z]+$/.test(parts[i])) this.region = parts[i].toUpperCase();
      else if (parts[i].length === 4) this.script = parts[i].charAt(0).toUpperCase() + parts[i].slice(1).toLowerCase();
    }
    this.maximize = function () { return this; };
    this.minimize = function () { return this; };
    this.toString = function () { return this.baseName; };
  }
  Intl.Locale = Locale;
})();