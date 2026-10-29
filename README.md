# Clean Code: antes e depois do RepublicaService

Comparação da versão antiga do serviço de despesas de uma república com a versão separada por responsabilidade.

## Versão antiga

[`antes/RepublicaService.java`](antes/RepublicaService.java) é a cópia histórica do serviço monolítico, anterior à primeira refatoração. Reúne operações de membros, despesas, recorrências, pagamentos, saldos e relatórios, com nomes abreviados e várias instruções na mesma linha.

## Versão refatorada

| Arquivo | Responsabilidade |
|---|---|
| [MembroService](depois/MembroService.java) | Criação da república, convite, entrada, saída, administração e consulta dos membros. |
| [DespesaService](depois/DespesaService.java) | Consulta, lançamento, edição e exclusão de despesas. |
| [RecorrenciaService](depois/RecorrenciaService.java) | Cadastro de modelos recorrentes e geração das despesas mensais. |
| [PagamentoService](depois/PagamentoService.java) | Registro, confirmação e consulta dos pagamentos. |
| [SaldoService](depois/SaldoService.java) | Cálculo dos saldos, sugestões de acerto, extrato e relatórios. |
| [FechamentoService](depois/FechamentoService.java) | Fechamento mensal. |
| [ContextoRepublica](depois/ContextoRepublica.java) | Contexto do usuário, trava por república e verificações compartilhadas. |
| [RegrasDespesa](depois/RegrasDespesa.java) | Preenchimento e validação de alterações das despesas, compartilhados com recorrências. |

## Principais mudanças

- Separação das responsabilidades do serviço original.
- Tipos explícitos e nomes descritivos para variáveis, parâmetros e métodos.
- Uma instrução por linha e chaves nos blocos condicionais.
- Reutilização da trava e das validações, sem dependências circulares entre os serviços.
- Preservação da ordem das validações, mensagens, regras e transações.
- Métodos como `buscarDespesa`, `listarDespesas`, `criarModelo`, `listarModelos` e `listarPagamentos` explicitam a operação realizada.

Os componentes compartilhados usam a transação do serviço chamador. O bloqueio continua valendo até o término dessa transação.

## Como comparar

1. Leia o método desejado em `antes/RepublicaService.java`.
2. Abra o serviço correspondente na pasta `depois`.
3. Para as verificações reutilizadas, consulte `ContextoRepublica` e `RegrasDespesa`.

O histórico também registra primeiro a versão antiga e depois a versão refatorada.

## Escopo deste repositório

Este repositório contém somente os serviços derivados do `RepublicaService` e os componentes extraídos, para estudo e comparação. Não é a API completa e não compila isoladamente.

DTOs, entidades, repositórios, controllers, configurações, testes e o enum `TipoEvento` permanecem no projeto da API. `AuthService` e `DivisaoService` também são dependências desse projeto e não foram copiados por não fazerem parte da divisão apresentada aqui.

A versão final foi validada no projeto completo com **29 testes aprovados**, incluindo verificações de ordem das validações, contrato do extrato, pagamentos e rollback do lote recorrente. Os testes não estão incluídos neste repositório de comparação.
