# Para quem acompanha o paciente

Esta página é para o psicólogo ou psiquiatra que recebe os relatórios do SunBi. A apresentação do
app está no [README](../README.md); os métodos e as referências, em [METODOS.md](METODOS.md).

## O que a pessoa registra

Todo dia, em três passos curtos:

1. **Humor**, numa escala de **−3 (muito para baixo) a +3 (muito acelerado)**, com 0 = estável.
   É a escala do NIMH Life Chart, com os nomes escritos por extenso.
2. **Sono**: hora em que deitou e em que acordou. O app calcula as horas e compara com a mediana
   das duas últimas semanas da própria pessoa.
3. Opcionalmente **energia** e **foco** (1 a 5), **irritação** e **ansiedade** (0 a 3), os
   **sinais de alerta pessoais** que apareceram, **acontecimentos** (noite virada, álcool,
   viagem, conflito, mudança no remédio, algo bom...) e uma anotação livre.

Além do check-in, a pessoa marca as tarefas da rotina conforme faz. Cinco delas são **âncoras**
do ritmo social (levantar, primeiro contato com alguém, começar o dia, jantar, deitar), e o app
guarda o horário real em que cada âncora foi marcada. Tarefas de **remédio** contam como doses
marcadas; o app nunca sugere mudar remédio e a assistente de IA não pode mexer nelas.

## O que você recebe

Na aba **Histórico**, o botão **"Relatório para a consulta"** gera um **PDF** dos últimos 7, 14 ou
28 dias e abre a conversa de WhatsApp com você (o app já tem o seu contato se a pessoa o
cadastrou como psicólogo). O PDF traz:

- **Quatro números**: dias com registro, média de sono, doses de remédio marcadas e âncoras no
  horário (feitas a até 45 minutos do horário-alvo).
- **Gráfico de humor** no estilo Life Chart (barras para cima e para baixo do estável) e
  **gráfico de sono** com a linha do sono habitual.
- **Sinais e acontecimentos** marcados no período.
- **Dia a dia**: uma linha por dia com humor, sono, energia, irritação, ansiedade, rotina feita,
  remédios, sinais, acontecimentos e a anotação, com as marcas de folga e férias.

O relatório é o que a pessoa registrou, sem interpretação do app. Ele diz isso no rodapé:
"não é avaliação clínica".

## Como ler

- **Noites curtas com humor acima do estável e energia alta** é o padrão que o app marca como
  "sinais de subida". **Humor baixo por vários dias ou sono muito acima do normal** é "sinais de
  descida". O aviso que a pessoa vê é um convite para abrir o próprio plano, não um alerta clínico.
- **Âncoras no horário** mede a regularidade da rotina, na linha do Social Rhythm Metric
  (IPSRT). Dias de folga, feriados e férias usam uma rotina mais solta, marcada no relatório.
- **Dias sem registro não significam dias ruins.** O app foi desenhado sem culpa: nada zera,
  nada é cobrado. Se faltam dias, o relatório mostra isso.

## O que o app faz sozinho

- **Avisos gentis** quando os registros batem com os padrões acima, com cores calmas, e um
  botão para o plano da pessoa.
- **Alarmes** nos horários da rotina, sem soneca (só "Concluído" ou "Parar"), com volume que sobe
  devagar.
- **Pontos e conquistas** para a rotina ter um gostinho de jogo, sem punição.
- **Uma assistente de IA** opcional, se a pessoa ligar.

## Limites, com franqueza

Ensaios com automonitoramento por smartphone no transtorno bipolar (MONARCA, LiveWell) não
mostraram redução de recaídas por si só, e a mania é subestimada no autorrelato. Por isso o
SunBi registra também sono, energia e irritação, trata os avisos como convite para olhar o
plano e aposta no que tem mais apoio: **rotina regular, plano combinado com a equipe, adesão e
comunicação com quem trata a pessoa**. Os métodos, as evidências e as referências estão em
**[METODOS.md](METODOS.md)**.

## Perguntas frequentes

**Posso confiar nos horários de sono?** São os que a pessoa digitou (ou escolheu no relógio do
app). Não há sensor. O gráfico de sono mostra a variação do horário de acordar, que costuma ser
mais útil que o total.

**O que a IA vê e faz?** Só se a pessoa ligar a assistente e aceitar: a cada pergunta, o app envia a
rotina, o plano e os registros dos últimos 31 dias para o provedor de IA que ela escolheu (OpenAI,
OpenRouter, OpenCode Go ou outro). A assistente resume, dá ideias e pode ajustar tarefas da rotina (sempre com "Desfazer"). Ela não vê o
relatório, não fala com você e tem regras fixas: nunca sugere mudar remédio, nunca apaga ou
muda horário de tarefa de remédio e, em sinal de risco, orienta CVV 188, SAMU 192 e o plano de
segurança.

**Onde ficam os dados?** Só nos aparelhos da pessoa (celular e, se ela usar a extensão, o navegador do computador). Não existe servidor
nem conta. O PDF sai do celular dela para o seu WhatsApp.

**Dá para ajustar a rotina junto com o paciente?** Sim. Na consulta, pelo celular (**Rotina**) ou,
com mais calma, pelo **modo avançado**: o celular serve uma página que abre no navegador do
computador com toda a configuração (rotinas, sinais, planos, pessoas, registros e backup).
