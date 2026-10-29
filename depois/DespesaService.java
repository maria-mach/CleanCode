package br.ifsp.republica.service;

import br.ifsp.republica.dto.Contratos.NovaDespesa;
import br.ifsp.republica.exception.RegraException;
import br.ifsp.republica.model.Despesa;
import br.ifsp.republica.model.Republica;
import br.ifsp.republica.repository.DespesaRepository;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DespesaService {

    private final DespesaRepository despesas;
    private final ContextoRepublica contexto;
    private final RegrasDespesa regrasDespesa;

    public DespesaService(
            DespesaRepository despesas,
            ContextoRepublica contexto,
            RegrasDespesa regrasDespesa) {
        this.despesas = despesas;
        this.contexto = contexto;
        this.regrasDespesa = regrasDespesa;
    }

    public List<Despesa> listarDespesas(Long usuarioId, String competencia) {
        Republica republica = contexto.republica(usuarioId);
        List<Despesa> despesasDaRepublica =
            despesas.findByRepublicaIdOrderByDataAscIdAsc(republica.id);

        if (competencia == null) {
            return despesasDaRepublica;
        }

        YearMonth mes = contexto.converterMes(competencia);
        return despesasDaRepublica.stream()
            .filter(despesa -> YearMonth.from(despesa.data).equals(mes))
            .toList();
    }

    // O filtro por república impede buscar uma despesa de outro grupo apenas conhecendo seu ID.
    public Despesa buscarDespesa(Long usuarioId, Long despesaId) {
        Republica republica = contexto.republica(usuarioId);
        return despesas.findByIdAndRepublicaId(despesaId, republica.id)
            .orElseThrow(RegraException::ausente);
    }

    @Transactional
    public Despesa lancar(Long usuarioId, NovaDespesa novaDespesa) {
        Republica republica = contexto.bloquearRepublica(usuarioId);
        Despesa despesa = new Despesa();
        despesa.autorId = usuarioId;

        regrasDespesa.preencherDespesa(despesa, novaDespesa, republica);

        return despesas.save(despesa);
    }

    @Transactional
    public Despesa editar(Long usuarioId, Long despesaId, NovaDespesa novaDespesa) {
        Republica republica = contexto.bloquearRepublica(usuarioId);
        Despesa despesa = buscarDespesa(usuarioId, despesaId);
        regrasDespesa.validarAlteracaoDaDespesa(republica, usuarioId, despesa);

        // A validação também considera o mês de destino se a data for alterada.
        regrasDespesa.preencherDespesa(despesa, novaDespesa, republica);

        return despesa;
    }

    @Transactional
    public void excluir(Long usuarioId, Long despesaId) {
        Republica republica = contexto.bloquearRepublica(usuarioId);
        Despesa despesa = buscarDespesa(usuarioId, despesaId);
        regrasDespesa.validarAlteracaoDaDespesa(republica, usuarioId, despesa);

        despesas.delete(despesa);
    }
}
