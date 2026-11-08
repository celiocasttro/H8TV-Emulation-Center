# H8TV Emulation Center

Central de emulação retrô para Android TV, com foco no H8TV (Amlogic S905X4, 2 GB RAM, Android 11).

## Objetivo

Um único aplicativo para organizar plataformas, localizar jogos no armazenamento interno/USB e iniciar os emuladores compatíveis.

## Estado atual

**Protótipo inicial da central Android.** Esta primeira etapa cria a estrutura nativa do APK, uma interface navegável por controle remoto e o pipeline de compilação no GitHub Actions.

Ainda não inclui emuladores nem promete compatibilidade de jogos. A integração será feita por etapas, priorizando componentes Android/ARM64 e respeitando as licenças de cada projeto.

## Plataformas planejadas

- Prioridade: Atari 2600, NES, Master System, Mega Drive, Game Boy, Game Boy Color, Game Boy Advance, SNES, Arcade e PlayStation 1.
- Experimentais: Nintendo 64, Nintendo DS, Dreamcast, PSP e Sega Saturn.
- Fora do escopo de desempenho esperado: PS2, GameCube/Wii, PS3, Switch e Xbox 360.

## Desenvolvimento

- Android nativo em Java.
- Gradle + Android Gradle Plugin.
- GitHub Actions para gerar APK de debug.
- Navegação por D-pad/controle remoto.
- Próximas etapas: biblioteca de ROMs via Storage Access Framework, metadados/capas e integração com emuladores.

## Compilar

O workflow **Android APK** compila automaticamente em cada push para `main` e também pode ser executado manualmente. O APK de debug fica disponível em **Actions → workflow run → Artifacts**.

## Licenças e componentes de terceiros

O projeto começa com código próprio. Antes de incorporar código, temas, imagens, emuladores ou núcleos de terceiros, suas licenças e requisitos de redistribuição deverão ser verificados individualmente.
