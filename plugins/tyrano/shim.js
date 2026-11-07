/* M.O.O.N — shim do TyranoBuilder/TyranoScript (roda depois de common/moon_web.js).
 *
 * O Tyrano em navegador guarda os saves no localStorage ($.setStorage com
 * "webstorage"); o canal comum já exporta tudo para
 * /sdcard/MOON/saves/tyrano/<jogo>/. Aqui só: não definir `process`/`require`
 * (o Tyrano decide "modo app" por eles e passaria a gravar em arquivo), tela
 * cheia/escala e toque.
 */
(function () {
  'use strict';
  var W = window;
  // sem `process`/`require`/`nw`: modo web (localStorage)
  // escala: o Tyrano recalcula o tamanho no resize da janela
  function fit() {
    try {
      if (W.$ && W.$.fn && W.TYRANO && W.TYRANO.kag && W.TYRANO.kag.tmp) { W.$(W).trigger('resize'); }
    } catch (e) {}
  }
  W.addEventListener('orientationchange', function () { setTimeout(fit, 300); });
  // toque: o Tyrano usa click/touch do jQuery; evita o zoom por duplo toque e o menu longo
  document.addEventListener('contextmenu', function (e) { e.preventDefault(); });
  document.addEventListener('gesturestart', function (e) { e.preventDefault(); });
})();
