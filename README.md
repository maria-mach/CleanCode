# Refatoração com Clean Code: RepublicaService

Este repositório compara o antes e o depois do `RepublicaService`, da API de despesas de república feita em Java com Spring.

O código veio da disciplina de APIs e Microsserviços. Depois de apresentar o trabalho, o professor pediu para dividir o service, usar tipos explícitos no lugar de `var` e deixar uma instrução por linha. A refatoração também foi usada como exemplo na disciplina de Engenharia de Software.

## Uso de IA

Esta refatoração foi feita com o auxílio do Claude, uma IA da Anthropic. O Claude ajudou a renomear e escrever os tipos, separar as instruções, dividir o `RepublicaService` em serviços, conferir o resultado com o capítulo 9 de *Engenharia de Software Moderna* e escrever o relatório. O escopo (apenas o `RepublicaService`, por falta de tempo) e as dificuldades relatadas são minhas.

[Leia o relatório completo em PDF](docs/relatorio-refatoracao-clean-code.pdf).

## Onde está cada versão

- **[Antes](antes/RepublicaService.java):** uma classe concentrava membros, despesas, recorrências, pagamentos, saldos e relatórios.
- **[Depois](depois):** o código foi dividido em seis serviços e duas classes de apoio.

| Serviço | O que faz |
|---|---|
| [MembroService](depois/MembroService.java) | Cria a república e cuida da entrada, saída e administração dos moradores. |
| [DespesaService](depois/DespesaService.java) | Lista, busca, lança, edita e exclui despesas. |
| [RecorrenciaService](depois/RecorrenciaService.java) | Cadastra modelos e gera as despesas do mês. |
| [PagamentoService](depois/PagamentoService.java) | Registra, confirma e lista pagamentos. |
| [SaldoService](depois/SaldoService.java) | Calcula saldos, sugere acertos e monta os relatórios. |
| [FechamentoService](depois/FechamentoService.java) | Fecha o mês para impedir alterações nas despesas. |

O que era usado por vários serviços ficou em [ContextoRepublica](depois/ContextoRepublica.java) e [RegrasDespesa](depois/RegrasDespesa.java), como a trava por república, as verificações e o preenchimento da despesa.

## O que mudou

- Abreviações como `m`, `r` e `d` viraram `morador`, `republica` e `despesa`.
- Os tipos passaram a ser escritos, como `Republica republica`.
- Cada instrução ficou em uma linha, com chaves nos `if`.
- Trechos grandes foram separados em métodos menores.
- Os comentários passaram a explicar o motivo das decisões.
- No projeto completo, os textos dos eventos viraram o enum `TipoEvento`, sem mudar o JSON.

As regras de negócio, mensagens de erro e transações foram mantidas.

## Dificuldades e aprendizado

A parte mais difícil foi dividir a classe sem criar dependências circulares. Por exemplo, a saída de um morador depende do saldo, e os relatórios usam a consulta de despesas.

Também foi necessário manter a trava dentro da transação da operação. O `ContextoRepublica` não abre uma transação própria.

Os **29 testes automatizados passaram no projeto completo**. Bons nomes e etapas separadas deixaram o código mais fácil de ler e explicar.

## O que ainda pode melhorar

Por enquanto, o foco foi o `RepublicaService`. As entidades ainda têm atributos públicos, o `SaldoService` reúne saldos e relatórios e outras partes da API ainda precisam da mesma revisão. Testes de concorrência no banco real também são uma melhoria futura.

A refatoração foi conferida com o capítulo 9 de *Engenharia de Software Moderna*, de Marco Tulio Valente.

Este repositório contém apenas a comparação dos serviços e o relatório. Não é a API completa e não compila sozinho: DTOs, entidades, repositórios, testes e outras dependências continuam no projeto original.
