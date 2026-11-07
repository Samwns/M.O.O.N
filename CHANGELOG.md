# Changelog — M.O.O.N

Mudanças relevantes de cada versão. Formato inspirado em [Keep a Changelog](https://keepachangelog.com/pt-BR/1.1.0/).

## [Não lançado]

### Adicionado
- **Modo de controle (novel/gamepad):** ao tocar em Jogar/Continuar aparece uma escolha, com **novel** como padrão: **Novel** (botões laterais/flutuantes + botão **⋮** no canto, sem D-pad nem losango) ou **Gamepad** (D-pad + losango + **⋮**; as colunas laterais saem), com "lembrar para este jogo". A escolha é salva por jogo e pode ser trocada ao segurar o card, nos Detalhes e dentro do jogo. Menu ⋮: Salvar, Carregar, Auto, Pular, Histórico, Esconder interface, Orientação, Trocar modo, Configurações, Sair (em paisagem, em 2 páginas).
- **Navegação redesenhada:** botão **+** para adicionar jogo, ícone da lua e menu **⋮** na barra superior, abas embaixo (Biblioteca, Saves, Plugins, Configurações; voltar na aba volta à Biblioteca, na Biblioteca sai do app) e **segurar** card/linha para abrir as ações (jogo: Jogar, Detalhes, Renomear, Trocar capa, Apagar da biblioteca com confirmação, sem apagar os arquivos; saves e plugins também); botões do player em paisagem não cobrem mais o D-pad.
- **Gamepad na tela com estilos de botões:** D-pad + 4 botões em losango (Teclado, Xbox, PlayStation com ✕ ○ □ △ ou confirmar com ○, Nintendo), botão de confirmar/cancelar configurável por estilo, mostrar/ocultar, e suporte a controle físico/teclado (A/B/X/Y, setas, START/SELECT, Enter/Espaço/Esc) com ocultar automático.
- **Motores dentro do app, sem instalar APK:** Ren'Py (8.3.7, 8.4.1) e Godot (4.7.2) rodam dentro do próprio M.O.O.N, carregados de pacotes `.moonpack`.
- **Pacotes de motor por versão exata** (`renpy-8.4.1`, `godot-4.7.2`…), instalados lado a lado. Podem ser importados de `Download/` ou baixados de um repositório (`index.json`).
- **RPG Maker MV/MZ e TyranoBuilder** (base): rodam numa WebView, com shims de save.
- **Gerenciador de saves:** pasta `/sdcard/MOON/saves/<motor>/<jogo>/` e aba Saves com exportar, importar, backup e apagar.
- **Capa, ícone e nome automáticos** para jogos Ren'Py e Godot.
- **Tela de detalhes:** descrição editável e screenshots tiradas do próprio jogo.
- **Patches de tradução** (motores que suportam, hoje o MOON.): importar, ativar/desativar, remover e exportar o modelo.
- **Temas:** preto e branco (padrão), mais 5 prontos e um personalizado com seletor de cor para cada cor da interface.
- Segurar o botão voltar dentro de um jogo abre a opção de voltar à biblioteca.

### Corrigido
- **Patch PT-BR não aparecia no MOON.:** com o idioma do texto em japonês (`game_lang: ja`) o player lia os scripts JP e ignorava o patch (escrito para o texto EN); agora um patch ativo força o texto EN ao jogar e ao ativar/instalar o patch.

### Alterado
- Saves do MOON. migrados para `/sdcard/MOON/saves/moon/<jogo>/`, sem perda.
- O botão voltar das barras passou para o lado esquerdo.

## [0.2.0]

### Adicionado
- **Player do MOON.** (Tactics, 1997) 100% em Obsidian Fault Script:
  - jogo em paisagem e tela cheia, com a interface sobreposta (caixa de texto, botões flutuantes, D-pad);
  - auto, pular, histórico e escolhas;
  - 12 slots + salvo rápido com miniatura;
  - galeria com CGs desbloqueadas;
  - mapas com marcador e placa de horário;
  - patch PT-BR;
  - áudio original.
- **Importação no celular** com detecção de motor (MOON., Ren'Py, Unity, Windows) e conversão com barra de progresso.
- **Biblioteca** com capas, busca e selo de motor; gerenciador de arquivos; configurações em abas (idioma, controles, opacidade, orientação, volumes).
- Ícones vetoriais em todos os botões, inclusive os ícones de cada motor.

## [0.1.0]

### Adicionado
- Primeiro APK do M.O.O.N: app Android nativo escrito em OFS, com a interface na sub-linguagem OAL, sem Java nem C próprios.
