<p align="center"><img src="moon/android/moon_icon.png" alt="M.O.O.N" width="128"></p>

<h1 align="center">M.O.O.N</h1>

**English** · [Português](#português)

A visual novel player for Android, written **100% in [Obsidian Fault Script](https://github.com/Samwns/Obsidian-Fault-Script)**.

## Supported engines

| Engine | Status |
| --- | --- |
| MOON. (Tactics, 1997) | Native |
| Ren'Py 8.x | Runs inside the app (engine pack) |
| Godot 4.x | Runs inside the app (engine pack) |
| RPG Maker MV/MZ, TyranoBuilder | Experimental (WebView) |
| Unity / Windows `.exe` | Planned |

## Features

- Game library with automatic engine detection, covers and search
- Engines installed as downloadable packs, one per exact version, with no extra APK
- Saves organized per engine and per game, with export, import and backup
- Novel mode or on-screen gamepad, with Xbox, PlayStation, Nintendo and keyboard layouts and physical controller support
- Themes with a color picker for each interface color
- Translation patches (MOON.)

## Build

Requires the Android SDK/NDK and the Obsidian Fault Script repository next to this one.

```sh
cd moon
./build.sh android            # debug APK
./build.sh android install    # install on a connected device
./build.sh android release    # signed APK (MOON_KEYSTORE, MOON_KEY_ALIAS, MOON_KS_PASS)
```

Target: Android 8+ (arm64).

## Changes

See [CHANGELOG.md](CHANGELOG.md).

---

## Português

Executor de visual novels para Android, escrito **100% em [Obsidian Fault Script](https://github.com/Samwns/Obsidian-Fault-Script)**.

### Motores suportados

| Motor | Estado |
| --- | --- |
| MOON. (Tactics, 1997) | Nativo |
| Ren'Py 8.x | Roda dentro do app (pacote de motor) |
| Godot 4.x | Roda dentro do app (pacote de motor) |
| RPG Maker MV/MZ, TyranoBuilder | Experimental (WebView) |
| Unity / `.exe` Windows | Planejado |

### Recursos

- Biblioteca com detecção automática do motor, capas e busca
- Motores instalados como pacotes baixáveis, um por versão exata, sem APK extra
- Saves organizados por motor e por jogo, com exportar, importar e backup
- Modo novel ou gamepad na tela, com layouts Xbox, PlayStation, Nintendo e teclado, e suporte a controle físico
- Temas com seletor para cada cor da interface
- Patches de tradução (MOON.)

### Compilar

Precisa do Android SDK/NDK e do repositório do Obsidian Fault Script ao lado deste.

```sh
cd moon
./build.sh android            # APK de debug
./build.sh android install    # instala no aparelho conectado
./build.sh android release    # APK assinado (MOON_KEYSTORE, MOON_KEY_ALIAS, MOON_KS_PASS)
```

Alvo: Android 8+ (arm64).

### Mudanças

Veja o [CHANGELOG.md](CHANGELOG.md).
