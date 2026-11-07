/* M.O.O.N — núcleo comum dos shims HTML5 (RPG Maker, TyranoBuilder).
 * Roda ANTES dos scripts do jogo (o M.O.O.N injeta no início do HTML).
 *
 * 1) Restaura o localStorage a partir de /sdcard/MOON/saves/<motor>/<jogo>/
 *    (o M.O.O.N entrega os arquivos em window.__MOON.saves = {chave: valor}).
 * 2) Observa setItem/removeItem/clear e avisa o M.O.O.N pelo document.title
 *    (a WebView do M.O.O.N não tem classe Java própria, então o canal de volta
 *    é o título; o Chromium tira caracteres de controle e colapsa espaços do
 *    título, por isso o prefixo é ASCII visível e o despejo vai em encodeURIComponent).
 *    Protocolo:
 *       @MOON@ M<rev>           "mudou": o M.O.O.N responde chamando MOON.dump()
 *       @MOON@ D<rev>.<i>.<n>|<pedaço>   despejo JSON {chave: valor} em encodeURIComponent, n pedaços de
 *                               3000 caracteres; o M.O.O.N lê cada um e chama MOON.next() para o seguinte;
 *                               no último chama MOON.ack() (volta ao M<rev>)
 *       @MOON@ X                o jogo pediu para sair (window.close / nw.App.quit)
 * 3) Tela cheia (já estamos em tela cheia), viewport e toque.
 */
(function () {
  'use strict';
  var W = window, M = W.__MOON || { saves: {} };
  var P = '@MOON@ ';
  var ls;
  try { ls = W.localStorage; ls.length; } catch (e) { return; }

  // --- 1) restaurar -------------------------------------------------------
  try {
    var old = [], i;
    for (i = 0; i < ls.length; i++) old.push(ls.key(i));
    for (i = 0; i < old.length; i++) ls.removeItem(old[i]);   // origem file:// é compartilhada entre jogos
    var s = M.saves || {};
    for (var k in s) if (Object.prototype.hasOwnProperty.call(s, k)) ls.setItem(k, s[k]);
  } catch (e) {}

  // --- 2) canal de saves --------------------------------------------------
  var rev = 0, timer = 0;
  function title(t) { try { document.title = t; } catch (e) {} }
  function touch() {
    if (timer) return;
    timer = setTimeout(function () { timer = 0; rev++; title(P + 'M' + rev); }, 120);
  }
  var proto = W.Storage && W.Storage.prototype;
  if (proto) {
    var rs = proto.setItem, rr = proto.removeItem, rc = proto.clear;
    proto.setItem = function (k, v) { rs.call(this, k, v); if (this === ls) touch(); };
    proto.removeItem = function (k) { rr.call(this, k); if (this === ls) touch(); };
    proto.clear = function () { rc.call(this); if (this === ls) touch(); };
  }
  function dump() {
    var o = {};
    for (var j = 0; j < ls.length; j++) { var kk = ls.key(j); o[kk] = ls.getItem(kk); }
    return JSON.stringify(o);
  }
  var out = '', idx = 0, CH = 3000;
  function chunk() {
    var n = Math.max(1, Math.ceil(out.length / CH));
    title(P + 'D' + rev + '.' + idx + '.' + n + '|' + out.substr(idx * CH, CH));
  }
  W.MOON = {
    version: 1,
    engine: M.engine || '',
    game: M.game || '',
    // despejo em pedaços: o Chromium corta o título em 4096 caracteres
    dump: function () { out = encodeURIComponent(dump()); idx = 0; chunk(); },
    next: function () { idx++; chunk(); },
    ack: function () { title(P + 'M' + rev); },
    ping: function () { title(P + 'M' + rev); },
    exit: function () { title(P + 'X'); }
  };
  W.addEventListener('pagehide', touch);
  W.addEventListener('visibilitychange', touch);

  // --- 3) tela cheia, janela, viewport ------------------------------------
  W.close = function () { W.MOON.exit(); };
  try {
    var noop = function () { return Promise.resolve(); };
    ['requestFullscreen', 'webkitRequestFullscreen', 'webkitRequestFullScreen', 'mozRequestFullScreen'].forEach(function (n) {
      if (W.Element) W.Element.prototype[n] = noop;
    });
    ['exitFullscreen', 'webkitExitFullscreen', 'webkitCancelFullScreen', 'mozCancelFullScreen'].forEach(function (n) {
      try { document[n] = noop; } catch (e) {}
    });
    ['fullscreenElement', 'webkitFullscreenElement', 'webkitCurrentFullScreenElement', 'mozFullScreenElement'].forEach(function (n) {
      try { Object.defineProperty(document, n, { configurable: true, get: function () { return document.documentElement; } }); } catch (e) {}
    });
    ['fullscreenEnabled', 'webkitFullscreenEnabled'].forEach(function (n) {
      try { Object.defineProperty(document, n, { configurable: true, get: function () { return true; } }); } catch (e) {}
    });
  } catch (e) {}

  function dom() {
    var head = document.head || document.documentElement;
    if (!document.querySelector('meta[name=viewport]')) {
      var m = document.createElement('meta');
      m.name = 'viewport';
      m.content = 'width=device-width,initial-scale=1,maximum-scale=1,user-scalable=no,viewport-fit=cover';
      head.appendChild(m);
    }
    var st = document.createElement('style');
    st.textContent = 'html,body{margin:0;padding:0;background:#000;overflow:hidden;' +
      '-webkit-user-select:none;user-select:none;-webkit-tap-highlight-color:transparent;' +
      '-webkit-touch-callout:none}';
    head.appendChild(st);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', dom); else dom();
})();
