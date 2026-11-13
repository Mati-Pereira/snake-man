# AGENTS.md

Guia para agentes de IA (e humanos apressados) que forem mexer neste repositório.
Leia antes de editar. O objetivo é preservar a arquitetura e não vazar segredos.

---

## 1. O que é este projeto

Jogo da cobra para Android, Kotlin + Jetpack Compose, sem dependências de terceiros.
A regra estrutural mais importante:

> **`game/`, `data/ScoreEntry.kt` e `audio/ChiptuneSynth.kt` são Kotlin puro.
> Não importe nada de `android.*` neles.**

Isso mantém a lógica e a síntese testáveis na JVM (29 testes) sem Robolectric nem emulador.

---

## 2. Setup obrigatório

```bash
export JAVA_HOME=$HOME/Android/jdk        # JDK 21 (Temurin)
export ANDROID_HOME=$HOME/Android/sdk     # platform 35 + build-tools 35.0.0
export PATH=$JAVA_HOME/bin:$PATH
```

`local.properties` (não versionado) deve conter `sdk.dir=$HOME/Android/sdk`.

Sempre use o wrapper (`./gradlew`), nunca um `gradle` global — o projeto fixa **8.11.1**.

---

## 3. Comandos essenciais

| Objetivo | Comando |
|---|---|
| Testes unitários | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug` |
| APK debug | `./gradlew assembleDebug` |
| APK release assinado | `./gradlew assembleRelease` |
| Demo web (navegador) | `./gradlew :web:webDemo` → `dist/web/snake-man/` |
| Tudo junto | `./gradlew testDebugUnitTest lintDebug assembleRelease` |
| Relatório de testes | `app/build/reports/tests/testDebugUnitTest/index.html` |

Antes de considerar qualquer tarefa concluída: **testes + lint + build** precisam passar.

---

## 4. Mapa do código

```
app/src/main/java/dev/matheus/snakeman/
├── MainActivity.kt        setContent + enableEdgeToEdge(dark)
├── game/
│   ├── GameModels.kt     Direction, WallMode, GameStatus, Cell, Apple, GameSnapshot
│   └── SnakeEngine.kt    REGRAS: tick, colisão, wrap, maçã dourada, velocidade
├── data/
│   ├── Prefs.kt          SharedPreferences: highScore, wallMode, musicEnabled, scores
│   └── ScoreEntry.kt     ScoreBoard (top 5) — lógica pura + encode/decode
├── audio/
│   ├── Feedback.kt       ToneGenerator + Handler + Vibrator (efeitos)
│   ├── ChiptuneSynth.kt  composição + síntese PCM (Kotlin puro, testável)
│   └── MusicPlayer.kt    AudioTrack, loop, foco de áudio e ciclo de vida
└── ui/
    ├── GameScreen.kt     telas, overlays, loop de ticks, placar
    ├── Board.kt          Canvas: grid, maçã comum/dourada, cobra, olhos
    ├── Controls.kt       DirectionPad + Modifier.swipeToSteer
    └── theme/Theme.kt    cores e MaterialTheme

web/src/jsMain/
├── kotlin/.../web/
│   ├── Main.kt          estados, loop, overlays e DOM
│   ├── BoardRenderer.kt Canvas 2D — espelho de ui/Board.kt
│   ├── BrowserAudio.kt  Web Audio com o MESMO PCM do ChiptuneSynth
│   └── WebPrefs.kt      localStorage no lugar de SharedPreferences
└── resources/index.html markup + CSS da demo
```

O módulo `:web` não tem código de regra próprio: ele compila `game/`,
`data/ScoreEntry.kt` e `audio/ChiptuneSynth.kt` direto do módulo `app`
(veja `web/build.gradle.kts`). Se algum desses arquivos ganhar um import de
`android.*`, a demo quebra — é o alarme de pureza.

Onde mexer para cada tipo de mudança:

| Quero mudar… | Arquivo |
|---|---|
| pontuação, velocidade, regra da dourada | `game/SnakeEngine.kt` (constantes no `companion object`) |
| desenho da cobra/maçã/tabuleiro | `ui/Board.kt` |
| botões, swipe, D-pad | `ui/Controls.kt` |
| layout, overlays, textos, placar | `ui/GameScreen.kt` |
| sons/vibração | `audio/Feedback.kt` |
| música de fundo (melodia, arranjo, timbres) | `audio/ChiptuneSynth.kt` |
| reprodução, loop, pausa, foco de áudio | `audio/MusicPlayer.kt` |
| botão MÚSICA ON/OFF | `ui/GameScreen.kt` + `data/Prefs.kt` |
| persistência | `data/Prefs.kt` |
| cores/tema | `ui/theme/Theme.kt` |
| visual do demo web | `web/src/jsMain/.../BoardRenderer.kt` + `index.html` |
| áudio do demo web | `web/src/jsMain/.../BrowserAudio.kt` |

---

## 5. Regras de negócio (não invente outras)

| Constante | Valor | Onde |
|---|---|---|
| Grid | 20×20 | `SnakeEngine` (parâmetros) |
| Pontos maçã comum | 10 | `APPLE_SCORE` |
| Pontos maçã dourada | 30 | `GOLDEN_SCORE` |
| Dourada a cada N maçãs | 5 | `GOLDEN_EVERY` |
| Vida da dourada | 45 ticks | `GOLDEN_LIFETIME_TICKS` |
| Tick inicial | 190 ms | `BASE_TICK_MILLIS` |
| Aceleração | −6 ms por maçã | `SPEED_STEP_MILLIS` |
| Tick mínimo | 70 ms | `MIN_TICK_MILLIS` |
| Fila de direções | 2 | `MAX_QUEUED_DIRECTIONS` |

Detalhes que já foram decididos e testados — **não "conserte" sem pedir**:

- A cauda que sai no mesmo tick **não** conta como colisão.
- Viradas de 180° são descartadas (compara com a última direção enfileirada).
- Maçã dourada expirada vira **maçã comum nova**, não desaparece sem substituição.
- `backToMenu()` zera score/maçãs (o recorde e o placar ficam).
- O placar guarda apenas partidas com **score > 0**, no máximo 5.
- Empate no placar é desempatado pela partida **mais recente**.

---

## 6. Convenções

- Kotlin oficial (`kotlin.code.style=official`), 4 espaços, sem wildcard imports.
- Comentários e textos de UI em **português**; nomes de código em inglês.
- Toda string visível está em `GameScreen.kt` (não há i18n ainda).
- Cores sempre via `ui/theme/Theme.kt`, nunca hex solto na UI.
- Novas regras de jogo → método no `SnakeEngine` + teste em `SnakeEngineTest`.
- Novas regras de dados → função pura em `ScoreEntry.kt` + teste em `ScoreBoardTest`.
- Novas músicas/timbres → `ChiptuneSynth.kt` (dados de notas em Kotlin, sem assets) + teste.
- `MusicPlayer.kt` é a única camada de áudio que pode tocar em `AudioTrack`/`AudioManager`.
- Composables privados no mesmo arquivo; evite criar arquivos novos sem necessidade.

---

## 7. Testes: como estender

```kotlin
engine.loadForTest(
    snake = listOf(Cell(2, 2), Cell(1, 2), Cell(0, 2)),
    apple = Cell(3, 2),
    applesEaten = 4
)
```

`loadForTest` é `internal` e aceita `appleKind`, `goldenTicksLeft`, `mode` e `applesEaten`.
Use-o para cenários determinísticos em vez de mexer no `Random`.

Não existe teste instrumentado (androidTest) de propósito — a UI é validada
manualmente no emulador (seção 8).

---

## 8. Validação visual no emulador

```bash
export ANDROID_AVD_HOME=$HOME/.config/.android/avd
$ANDROID_HOME/emulator/emulator -avd snake_pixel -no-window -no-audio \
    -no-boot-anim -gpu swiftshader_indirect -no-snapshot &

adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n dev.matheus.snakeman/.MainActivity
adb exec-out screencap -p > /tmp/shot.png
```

Dicas aprendidas na prática:

- **Não use a imagem ATD (`aosp_atd`)**: ela desenha mas o framebuffer sai preto.
  Use `system-images;android-34;google_apis;x86_64`.
- Espere `sys.boot_completed=1` antes de instalar.
- Em alguns boots o pacote **não persiste**; reinstale após subir o emulador.
- Descubra coordenadas de toque com:
  `adb shell uiautomator dump /sdcard/ui.xml && adb shell cat /sdcard/ui.xml`
- Toques são `input tap X Y`; swipes, `input swipe X1 Y1 X2 Y2 200`.
- Para jogar sozinho, existe a abordagem "bot": tirar screenshot → achar a maçã por
  cor (vermelho `#FF5A5F`, dourado `#FFC93C`) → tocar no D-pad.

---

## 9. Segurança — NUNCA faça

- ❌ commitar `keystore/`, `*.jks`, `keystore.properties` ou `local.properties`
- ❌ colar senhas da keystore em código, testes, docs ou issues
- ❌ adicionar `.apk` / `.aab` ao Git (use GitHub Releases)
- ❌ trocar `applicationId` ou a keystore (quebra atualizações de quem já instalou)
- ❌ rodar `git add -f` em algo que o `.gitignore` bloqueia

Antes de commitar:

```bash
git status --porcelain          # confira a lista
git check-ignore -v keystore/ local.properties dist/ 2>/dev/null
```

---

## 10. Versionamento e release

- `versionCode`/`versionName` ficam em `app/build.gradle.kts` (módulo `app`).
- Suba **os dois** a cada release publicada.
- Tag `v*` dispara `.github/workflows/release.yml`: testes + lint + APK assinado
  publicado como GitHub Release. A tag precisa bater com `versionName`
  (`v1.3` ↔ `"1.3"`), senão o workflow falha antes do build.
- O CI reconstrói o `.jks` e o `keystore.properties` a partir dos secrets
  `KEYSTORE_BASE64`, `KEYSTORE_STORE_PASSWORD`, `KEYSTORE_KEY_ALIAS` e
  `KEYSTORE_KEY_PASSWORD`.
- A assinatura é lida de `keystore/keystore.properties`; se ausente, cai para debug.
- Verifique o APK:

```bash
$ANDROID_HOME/build-tools/35.0.0/apksigner verify --verbose --print-certs \
    app/build/outputs/apk/release/app-release.apk
```

---

## 11. Armadilhas conhecidas

| Sintoma | Causa provável | Solução |
|---|---|---|
| `JAVA_HOME is not set` | env não exportado | exporte antes de `./gradlew` |
| `min(Dp, Dp)` não compila | `kotlin.math.min` não aceita `Dp` | use `minOf(a, b)` |
| `windowLightNavigationBar requires API 27` | lint com minSdk 24 | não use esse atributo |
| tela preta no emulador | imagem ATD | troque para `google_apis` |
| `Activity class does not exist` | pacote sumiu após boot | `adb install -r` de novo |
| layout "pula" quando há recorde | linha extra no menu | mantenha altura fixa (já resolvido) |
| som não toca | emulador sem áudio (`-no-audio`) | teste em aparelho real |

---

## 12. Checklist antes de finalizar uma tarefa

1. `./gradlew testDebugUnitTest` verde
2. `./gradlew lintDebug` sem erros
3. `./gradlew assembleRelease` gera APK
4. Nenhum arquivo sensível em `git status`
5. Se mudou regra de jogo: teste novo cobrindo
6. Se mudou UI: screenshot atualizado em `screenshots/` e README coerente
7. Se mudou versão: `versionCode` e `versionName` atualizados
