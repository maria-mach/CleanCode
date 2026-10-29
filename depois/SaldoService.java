package br.ifsp.republica.service;

import br.ifsp.republica.dto.Contratos.Evento;
import br.ifsp.republica.dto.Contratos.Saldo;
import br.ifsp.republica.dto.Contratos.SaldoOutro;
import br.ifsp.republica.dto.Contratos.Transferencia;
import br.ifsp.republica.model.Despesa;
import br.ifsp.republica.model.Morador;
import br.ifsp.republica.model.Pagamento;
import br.ifsp.republica.model.Republica;
import br.ifsp.republica.model.TipoEvento;
import br.ifsp.republica.repository.DespesaRepository;
import br.ifsp.republica.repository.MoradorRepository;
import br.ifsp.republica.repository.PagamentoRepository;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SaldoService {

    private final MoradorRepository moradores;
    private final DespesaRepository despesas;
    private final PagamentoRepository pagamentos;
    private final DespesaService despesaService;
    private final ContextoRepublica contexto;

    public SaldoService(
            MoradorRepository moradores,
            DespesaRepository despesas,
            PagamentoRepository pagamentos,
            DespesaService despesaService,
            ContextoRepublica contexto) {
        this.moradores = moradores;
        this.despesas = despesas;
        this.pagamentos = pagamentos;
        this.despesaService = despesaService;
        this.contexto = contexto;
    }

    public Saldo saldo(Long usuarioId) {
        Republica republica = contexto.republica(usuarioId);
        Map<Long, BigDecimal> saldosPorMorador = calcularSaldosDoUsuarioPorMorador(republica.id, usuarioId);
        Map<Long, BigDecimal> saldosDaRepublica = calcularSaldos(republica.id);

        List<SaldoOutro> detalhamento = saldosPorMorador.entrySet().stream()
            .map(saldo -> new SaldoOutro(saldo.getKey(), saldo.getValue()))
            .toList();
        BigDecimal saldoTotal = saldosDaRepublica.getOrDefault(usuarioId, BigDecimal.ZERO);

        return new Saldo(saldoTotal, detalhamento);
    }

    // Só compensa um mapa local; as sugestões não registram pagamentos no banco.
    public List<Transferencia> simplificar(Long usuarioId) {
        Republica republica = contexto.republica(usuarioId);
        Map<Long, BigDecimal> saldos = calcularSaldos(republica.id);
        List<Transferencia> transferencias = new ArrayList<>();

        List<Map.Entry<Long, BigDecimal>> devedores = saldos.entrySet().stream()
            .filter(saldo -> saldo.getValue().signum() < 0)
            .toList();
        List<Map.Entry<Long, BigDecimal>> credores = saldos.entrySet().stream()
            .filter(saldo -> saldo.getValue().signum() > 0)
            .toList();

        int indiceDevedor = 0;
        int indiceCredor = 0;
        while (indiceDevedor < devedores.size() && indiceCredor < credores.size()) {
            Map.Entry<Long, BigDecimal> devedor = devedores.get(indiceDevedor);
            Map.Entry<Long, BigDecimal> credor = credores.get(indiceCredor);
            BigDecimal valor = devedor.getValue().negate().min(credor.getValue());

            transferencias.add(new Transferencia(devedor.getKey(), credor.getKey(), valor));
            devedor.setValue(devedor.getValue().add(valor));
            credor.setValue(credor.getValue().subtract(valor));

            if (devedor.getValue().signum() == 0) {
                indiceDevedor++;
            }
            if (credor.getValue().signum() == 0) {
                indiceCredor++;
            }
        }

        return transferencias;
    }

    public Map<String, BigDecimal> categorias(Long usuarioId, String competencia) {
        YearMonth mes = contexto.converterMes(competencia);
        List<Despesa> despesasDoMes = despesaService.listarDespesas(usuarioId, mes.toString());
        Map<String, BigDecimal> totaisPorCategoria = new TreeMap<>();

        for (Despesa despesa : despesasDoMes) {
            totaisPorCategoria.merge(despesa.categoria, despesa.valor, BigDecimal::add);
        }

        return totaisPorCategoria;
    }

    public Map<String, BigDecimal> mensal(Long usuarioId) {
        List<Despesa> despesasDaRepublica = despesaService.listarDespesas(usuarioId, null);
        Map<String, BigDecimal> totaisPorMes = new TreeMap<>();

        for (Despesa despesa : despesasDaRepublica) {
            String mes = YearMonth.from(despesa.data).toString();
            totaisPorMes.merge(mes, despesa.valor, BigDecimal::add);
        }

        return totaisPorMes;
    }

    public List<Evento> extrato(Long usuarioId) {
        Republica republica = contexto.republica(usuarioId);
        List<Evento> eventos = new ArrayList<>();
        adicionarEventosDeDespesas(eventos, republica.id, usuarioId);
        adicionarEventosDePagamentos(eventos, republica.id, usuarioId);

        eventos.sort(Comparator.comparing(Evento::data)
            .thenComparing(Evento::referenciaId)
            .thenComparing(Evento::tipo));

        return eventos;
    }

    // Positivo = a receber; negativo = a pagar. O saldo é sempre reconstruído do histórico.
    public Map<Long, BigDecimal> calcularSaldos(Long republicaId) {
        Map<Long, BigDecimal> saldos = new TreeMap<>();
        for (Morador morador : moradores.findByRepublicaIdOrderById(republicaId)) {
            saldos.put(morador.id, BigDecimal.ZERO.setScale(2));
        }

        for (Despesa despesa : despesas.findByRepublicaIdOrderByDataAscIdAsc(republicaId)) {
            saldos.merge(despesa.pagadorId, despesa.valor, BigDecimal::add);
            for (Map.Entry<Long, BigDecimal> parcela : despesa.parcelas.entrySet()) {
                saldos.merge(parcela.getKey(), parcela.getValue().negate(), BigDecimal::add);
            }
        }

        for (Pagamento pagamento : pagamentos.findByRepublicaIdOrderByDataAscIdAsc(republicaId)) {
            if (pagamento.confirmado) {
                saldos.merge(pagamento.pagadorId, pagamento.valor, BigDecimal::add);
                saldos.merge(pagamento.recebedorId, pagamento.valor.negate(), BigDecimal::add);
            }
        }

        return saldos;
    }

    private Map<Long, BigDecimal> calcularSaldosDoUsuarioPorMorador(Long republicaId, Long usuarioId) {
        Map<Long, BigDecimal> saldosPorMorador = new TreeMap<>();

        for (Despesa despesa : despesas.findByRepublicaIdOrderByDataAscIdAsc(republicaId)) {
            adicionarDespesaAoDetalhamento(saldosPorMorador, despesa, usuarioId);
        }

        for (Pagamento pagamento : pagamentos.findByRepublicaIdOrderByDataAscIdAsc(republicaId)) {
            if (pagamento.confirmado) {
                adicionarPagamentoAoDetalhamento(saldosPorMorador, pagamento, usuarioId);
            }
        }

        return saldosPorMorador;
    }

    private void adicionarDespesaAoDetalhamento(
            Map<Long, BigDecimal> saldosPorMorador, Despesa despesa, Long usuarioId) {
        if (despesa.pagadorId.equals(usuarioId)) {
            for (Map.Entry<Long, BigDecimal> parcela : despesa.parcelas.entrySet()) {
                if (!parcela.getKey().equals(usuarioId)) {
                    saldosPorMorador.merge(parcela.getKey(), parcela.getValue(), BigDecimal::add);
                }
            }
        } else if (despesa.parcelas.containsKey(usuarioId)) {
            BigDecimal valorDevido = despesa.parcelas.get(usuarioId).negate();
            saldosPorMorador.merge(despesa.pagadorId, valorDevido, BigDecimal::add);
        }
    }

    private void adicionarPagamentoAoDetalhamento(
            Map<Long, BigDecimal> saldosPorMorador, Pagamento pagamento, Long usuarioId) {
        if (pagamento.pagadorId.equals(usuarioId)) {
            saldosPorMorador.merge(pagamento.recebedorId, pagamento.valor, BigDecimal::add);
        }
        if (pagamento.recebedorId.equals(usuarioId)) {
            saldosPorMorador.merge(pagamento.pagadorId, pagamento.valor.negate(), BigDecimal::add);
        }
    }

    private void adicionarEventosDeDespesas(List<Evento> eventos, Long republicaId, Long usuarioId) {
        for (Despesa despesa : despesas.findByRepublicaIdOrderByDataAscIdAsc(republicaId)) {
            if (despesa.pagadorId.equals(usuarioId)) {
                eventos.add(new Evento(despesa.data, TipoEvento.DESPESA_PAGA.name(), despesa.id, despesa.valor));
            }
            if (despesa.parcelas.containsKey(usuarioId)) {
                BigDecimal parcela = despesa.parcelas.get(usuarioId).negate();
                eventos.add(new Evento(despesa.data, TipoEvento.PARCELA.name(), despesa.id, parcela));
            }
        }
    }

    private void adicionarEventosDePagamentos(List<Evento> eventos, Long republicaId, Long usuarioId) {
        for (Pagamento pagamento : pagamentos.findByRepublicaIdOrderByDataAscIdAsc(republicaId)) {
            if (!contexto.envolveMorador(pagamento, usuarioId)) {
                continue;
            }

            BigDecimal valor = pagamento.pagadorId.equals(usuarioId)
                ? pagamento.valor
                : pagamento.valor.negate();
            TipoEvento tipo = pagamento.confirmado
                ? TipoEvento.PAGAMENTO_CONFIRMADO
                : TipoEvento.PAGAMENTO_PENDENTE;
            // O DTO continua usando texto para preservar o contrato e a ordenação do extrato.
            eventos.add(new Evento(pagamento.data, tipo.name(), pagamento.id, valor));
        }
    }
}
