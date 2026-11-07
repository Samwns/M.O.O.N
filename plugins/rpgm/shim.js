/* M.O.O.N — shim do RPG Maker MV/MZ (roda depois de common/moon_web.js).
 *
 * Saves: o MV guarda em localStorage ("RPG File1", "RPG Global", "RPG Config")
 * quando NÃO está no NW.js (Utils.isNwjs() = typeof require === 'function' &&
 * typeof process === 'object'). Por isso o shim define `nw` e um `require`
 * de brinquedo (plugins que olham `nw`/`require('fs')` não quebram), mas
 * NÃO define `process`: o jogo continua no modo web e usa o localStorage.
 * O MZ no modo web usa localforage (IndexedDB); trocamos o localforage por
 * uma versão com a mesma API sobre o localStorage, para os saves caírem no
 * mesmo canal e serem exportados para /sdcard/MOON/saves/rpgm/<jogo>/.
 */
(function () {
  'use strict';
  var W = window;
  var ls = W.localStorage;

  // --- localforage -> localStorage ---
  function done(f, cb) {
    var p = new Promise(function (res, rej) { try { res(f()); } catch (e) { rej(e); } });
    if (typeof cb === 'function') p.then(function (v) { cb(null, v); }, function (e) { cb(e); });
    return p;
  }
  var forage = {
    getItem: function (k, cb) { return done(function () { var v = ls.getItem(k); return v === null ? null : v; }, cb); },
    setItem: function (k, v, cb) { return done(function () { ls.setItem(k, String(v)); return v; }, cb); },
    removeItem: function (k, cb) { return done(function () { ls.removeItem(k); }, cb); },
    clear: function (cb) { return done(function () { ls.clear(); }, cb); },
    length: function (cb) { return done(function () { return ls.length; }, cb); },
    key: function (i, cb) { return done(function () { return ls.key(i); }, cb); },
    keys: function (cb) { return done(function () { var a = []; for (var i = 0; i < ls.length; i++) a.push(ls.key(i)); return a; }, cb); },
    iterate: function (fn, cb) { return done(function () { for (var i = 0; i < ls.length; i++) { var k = ls.key(i); var r = fn(ls.getItem(k), k, i + 1); if (r !== undefined) return r; } }, cb); },
    ready: function () { return Promise.resolve(); },
    config: function () { return true; },
    createInstance: function () { return forage; },
    dropInstance: function () { return Promise.resolve(); },
    supports: function () { return true; }
  };
  try {
    Object.defineProperty(W, 'localforage', { configurable: true, get: function () { return forage; }, set: function () {} });
  } catch (e) {}

  // --- NW.js de mentirinha ---
  var winStub = {
    on: function () {}, off: function () {}, removeAllListeners: function () {},
    isFullscreen: true, enterFullscreen: function () {}, leaveFullscreen: function () {}, toggleFullscreen: function () {},
    zoomLevel: 0, x: 0, y: 0, width: W.innerWidth, height: W.innerHeight,
    moveTo: function () {}, resizeTo: function () {}, setPosition: function () {}, focus: function () {}, show: function () {}, hide: function () {},
    showDevTools: function () {}, closeDevTools: function () {}, close: function () { W.MOON.exit(); },
    setResizable: function () {}, setMinimumSize: function () {}, setMaximumSize: function () {}, capturePage: function () {}
  };
  if (!W.nw) {
    W.nw = {
      Window: { get: function () { return winStub; }, open: function () { return winStub; } },
      App: { argv: [], dataPath: '', manifest: {}, quit: function () { W.MOON.exit(); }, closeAllWindows: function () { W.MOON.exit(); } },
      Screen: { Init: function () {}, screens: [{ bounds: { x: 0, y: 0, width: W.innerWidth, height: W.innerHeight } }], on: function () {} },
      Shell: { openExternal: function () {}, openItem: function () {}, showItemInFolder: function () {} },
      Clipboard: { get: function () { return { get: function () { return ''; }, set: function () {} }; } }
    };
  }
  var stubs = {
    'nw.gui': W.nw,
    fs: {
      existsSync: function () { return false; }, readFileSync: function () { return ''; }, readdirSync: function () { return []; },
      writeFileSync: function () {}, mkdirSync: function () {}, unlinkSync: function () {}, statSync: function () { return { isDirectory: function () { return false; }, isFile: function () { return false; } }; }
    },
    path: {
      sep: '/', join: function () { return Array.prototype.join.call(arguments, '/'); },
      dirname: function (p) { return String(p).replace(/\/[^\/]*$/, ''); }, basename: function (p) { return String(p).replace(/^.*\//, ''); },
      extname: function (p) { var m = /\.[^.\/]*$/.exec(p); return m ? m[0] : ''; }, resolve: function () { return Array.prototype.join.call(arguments, '/'); }
    },
    os: { platform: function () { return 'android'; }, type: function () { return 'Linux'; }, arch: function () { return 'arm64'; } },
    child_process: { exec: function () {}, spawn: function () {} }
  };
  if (typeof W.require !== 'function') {
    W.require = function (n) { return stubs[n] || {}; };
  }

  // --- tela cheia / escala: o MV/MZ escolhem esticar sozinhos em dispositivos móveis ---
  function patchGraphics() {
    var G = W.Graphics;
    if (!G) return false;
    G._requestFullScreen = function () {};
    G._cancelFullScreen = function () {};
    G._isFullScreen = function () { return true; };
    G._switchFullScreen = function () {};
    return true;
  }
  var tries = 0;
  (function wait() { if (!patchGraphics() && tries++ < 200) setTimeout(wait, 50); })();
})();
