# Pausa Estudo

Ciclo de estudo automático (tipo pomodoro) para Android. Você descansa, e depois o app abre sozinho, por cima de qualquer coisa, a próxima sessão de estudo.

## Você escolhe a combinação

**1. O que abrir a cada sessão**

- **Um PDF:** o app mostra as páginas por cima de tudo.
- **Outro app de estudo:** abre um app (por exemplo o Samsung Notes). Aqui a sessão sempre é por tempo.

**2. Como termina a sessão (quando é PDF)**

- **Por trecho:** você lê e toca em "Terminei" (o botão libera depois de um tempo mínimo de leitura, sem contagem visível). Você define quantas **páginas por sessão** (1, 2, 3...) ou marca **abrir o PDF inteiro de uma vez**. Ao terminar, o app avança sozinho para as páginas seguintes.
- **Por tempo fixo:** o PDF abre inteiro, a sessão fecha sozinha quando o tempo acaba e chega uma notificação. A contagem fica escondida e não existe botão para sair antes. A próxima sessão continua da página em que você parou.

Exemplos de uso:

| Situação | O que abrir | Como termina |
|---|---|---|
| Celular, estudar 1 ou 2 páginas por vez | PDF | Por trecho (páginas por sessão = 1 ou 2) |
| Celular, ler o PDF todo logo | PDF | Por trecho + "PDF inteiro de uma vez" |
| Celular, sem Samsung Notes, estudar por tempo | PDF | Por tempo fixo |
| Tablet Samsung, estudar no Samsung Notes | Outro app | (sempre por tempo) |

O celular e o tablet têm configurações separadas.

## Como o ciclo funciona

1. **Descanso** (padrão 10 min). Conta pelo relógio, então tela inicial, tela apagada e tela de bloqueio contam também.
2. **Sessão de estudo.** Se o descanso acabar com a tela apagada ou bloqueada, a sessão abre assim que você desbloquear.
3. Volta ao passo 1, para sempre, até você desligar o interruptor.

## Gerar o APK sem instalar nada no computador

1. Crie uma conta em https://github.com e um repositório novo (pode ser privado).
2. Envie o conteúdo desta pasta para o repositório (*Add file → Upload files*).
3. Crie o workflow `.github/workflows/build-apk.yml` (já vem nesta pasta; se a pasta `.github` não subir, use *Actions → set up a workflow yourself* e cole o conteúdo do arquivo).
4. Na aba **Actions**, o **Build APK** roda sozinho (3 a 6 minutos). Quando ficar verde, baixe **PausaEstudo-apk** em *Artifacts*. Dentro do .zip está o `app-debug.apk`.
5. Instale no celular/tablet. Se já havia uma versão instalada, desinstale antes (cada build tem uma assinatura diferente).

## Configurar

1. **Permissões:** conceda as 3 (aparecer sobre outros apps, notificações e bateria sem restrição).
   - Se o botão estiver cinza (Android 13 ou mais novo, app instalado fora da Play Store): *Configurações → Apps → Pausa Estudo → ⋮ → Permitir configurações restritas*.
2. Escolha **o que abrir** e **como termina**, e preencha as opções que aparecem.
3. Toque em **Testar agora** para ver a sessão na hora. O teste não mexe no ciclo nem avança páginas, e no modo por tempo dura só 10 segundos.
4. Ligue **Ciclo de estudo ativo**. O primeiro descanso começa nesse momento.

## Para não parar de funcionar

Alguns aparelhos (Xiaomi, Samsung, Motorola, Oppo, Vivo, Huawei) matam apps em segundo plano:

- Conceda *Sem otimização de bateria* dentro do app.
- Nas configurações do aparelho, deixe a bateria do Pausa Estudo como "Sem restrições" (no Samsung: *Bateria → Limites de uso em segundo plano → Apps que nunca dormem*).
- Ative "Início automático" / "Autostart" quando existir.
- Nos apps recentes, trave o Pausa Estudo (segure o card e escolha "Bloquear").

## Limitações

- O app não consegue abrir uma nota específica dentro do Samsung Notes: ele abre o Samsung Notes e ele mostra o que costuma mostrar.
- O app não fecha outros apps; ele passa por cima (PDF) ou traz o app de estudo para a frente.
- Com o PDF na tela, ela fica por cima de tudo, até se você apertar Home ou Voltar. Ela só some quando o botão de terminar libera (por trecho) ou quando o tempo acaba (por tempo fixo). Não use tempos muito altos. Em emergência: *Configurações → Apps → Pausa Estudo → Forçar parada*.
- Se o aparelho dormir fundo (tela apagada por muito tempo), a notificação de fim de estudo pode atrasar um pouco.
- O PDF é copiado para dentro do app na hora que você escolhe; se alterar o arquivo original, escolha de novo. PDFs com senha não funcionam.
- O APK é de depuração, para uso pessoal; para a Play Store seria preciso gerar uma versão assinada de release.
