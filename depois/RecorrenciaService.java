package br.ifsp.republica.service;

import br.ifsp.republica.dto.Contratos.NovaDespesa;
import br.ifsp.republica.dto.Contratos.NovaRecorrencia;
import br.ifsp.republica.dto.Contratos.Participante;
import br.ifsp.republica.exception.RegraException;
import br.ifsp.republica.model.Despesa;
import br.ifsp.republica.model.Recorrencia;
import br.ifsp.republica.model.Republica;
import br.ifsp.republica.repository.DespesaRepository;
import br.ifsp.republica.repository.RecorrenciaRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RecorrenciaService {

    private final RecorrenciaRepository recorrencias;
    private final DespesaRepository despesas;
    private final ContextoRepublica contexto;
    private final RegrasDespesa regrasDespesa;
    private final DivisaoService divisao;

    public RecorrenciaService(
            RecorrenciaRepository recorrencias,
            DespesaRepository despesas,
            ContextoRepublica contexto,
            RegrasDespesa regrasDespesa,
            DivisaoService divisao) {
        this.recorrencias = recorrencias;
        this.despesas = despesas;
        this.contexto = contexto;
        this.regrasDespesa = regrasDespesa;
        this.divisao = divisao;
    }

    // O modelo não gera gastos: ele é reutilizado pelo método gerar para um mês solicitado.
    @Transactional
    public Recorrencia criarModelo(Long usuarioId, NovaRecorrencia novaRecorrencia) {
        Republica republica = contexto.bloquearRepublica(usuarioId);
        contexto.exigirAdministrador(republica, usuarioId);

        NovaDespesa dadosDaDespesa = novaRecorrencia.despesa();
        contexto.validarParticipantes(republica, dadosDaDespesa);
        divisao.dividir(dadosDaDespesa);

        Recorrencia recorrencia = new Recorrencia();
        recorrencia.republicaId = republica.id;
        recorrencia.descricao = dadosDaDespesa.descricao();
        recorrencia.categoria = dadosDaDespesa.categoria();
        recorrencia.valor = dadosDaDespesa.valor();
        recorrencia.pagadorId = dadosDaDespesa.pagadorId();
        recorrencia.divisao = dadosDaDespesa.divisao();
        recorrencia.dia = novaRecorrencia.dia();

        for (Participante participante : dadosDaDespesa.participantes()) {
            BigDecimal parte = participante.parte() == null
                ? BigDecimal.ZERO
                : participante.parte();
            recorrencia.participantes.put(participante.moradorId(), parte);
        }

        return recorrencias.save(recorrencia);
    }

    public List<Recorrencia> listarModelos(Long usuarioId) {
        Republica republica = contexto.republica(usuarioId);
        return recorrencias.findByRepublicaIdOrderById(republica.id);
    }

    @Transactional
    public void excluirModelo(Long usuarioId, Long modeloId) {
        Republica republica = contexto.bloquearRepublica(usuarioId);
        contexto.exigirAdministrador(republica, usuarioId);

        Recorrencia recorrencia = recorrencias.findByIdAndRepublicaId(modeloId, republica.id)
            .orElseThrow(RegraException::ausente);
        recorrencias.delete(recorrencia);
    }

    // A transação engloba o lote: se um modelo falhar, nenhuma despesa do lote é confirmada.
    @Transactional
    public List<Despesa> gerar(Long usuarioId, String competencia) {
        Republica republica = contexto.bloquearRepublica(usuarioId);
        contexto.exigirAdministrador(republica, usuarioId);
        YearMonth mes = contexto.converterMes(competencia);
        contexto.exigirMesAberto(republica.id, mes);

        List<Despesa> despesasGeradas = new ArrayList<>();
        List<Recorrencia> modelos = recorrencias.findByRepublicaIdOrderById(republica.id);

        for (Recorrencia recorrencia : modelos) {
            boolean jaGerada = despesas.existsByModeloIdAndCompetencia(
                recorrencia.id, mes.toString());
            if (jaGerada) {
                continue;
            }

            Despesa despesa = criarDespesaDoModelo(recorrencia, mes, republica, usuarioId);
            despesasGeradas.add(despesas.save(despesa));
        }

        return despesasGeradas;
    }

    private Despesa criarDespesaDoModelo(
            Recorrencia recorrencia, YearMonth mes, Republica republica, Long usuarioId) {
        NovaDespesa dadosDaDespesa = montarDadosDoModelo(recorrencia, mes);
        Despesa despesa = new Despesa();
        regrasDespesa.preencherDespesa(despesa, dadosDaDespesa, republica);
        despesa.autorId = usuarioId;
        despesa.modeloId = recorrencia.id;
        despesa.competencia = mes.toString();

        return despesa;
    }

    private NovaDespesa montarDadosDoModelo(Recorrencia recorrencia, YearMonth mes) {
        List<Participante> participantes = recorrencia.participantes.entrySet().stream()
            .map(parte -> new Participante(parte.getKey(), parte.getValue()))
            .toList();

        // Um modelo do dia 31 usa o último dia quando o mês é mais curto.
        int diaDaDespesa = Math.min(recorrencia.dia, mes.lengthOfMonth());
        LocalDate data = mes.atDay(diaDaDespesa);

        return new NovaDespesa(
            recorrencia.descricao,
            recorrencia.valor,
            data,
            recorrencia.categoria,
            recorrencia.pagadorId,
            recorrencia.divisao,
            participantes);
    }
}
