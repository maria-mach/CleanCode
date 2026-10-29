package br.ifsp.republica.service;

import br.ifsp.republica.dto.Contratos.NovaDespesa;
import br.ifsp.republica.exception.RegraException;
import br.ifsp.republica.model.Despesa;
import br.ifsp.republica.model.Republica;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RegrasDespesa {

    private final ContextoRepublica contexto;
    private final DivisaoService divisao;

    public RegrasDespesa(
            ContextoRepublica contexto,
            DivisaoService divisao) {
        this.contexto = contexto;
        this.divisao = divisao;
    }

    public void preencherDespesa(Despesa despesa, NovaDespesa novaDespesa, Republica republica) {
        contexto.validarParticipantes(republica, novaDespesa);
        contexto.exigirMesAberto(republica.id, YearMonth.from(novaDespesa.data()));
        Map<Long, BigDecimal> parcelas = divisao.dividir(novaDespesa);

        despesa.republicaId = republica.id;
        despesa.descricao = novaDespesa.descricao();
        despesa.valor = novaDespesa.valor();
        despesa.data = novaDespesa.data();
        despesa.categoria = novaDespesa.categoria();
        despesa.pagadorId = novaDespesa.pagadorId();
        despesa.divisao = novaDespesa.divisao();

        // Mantém a coleção gerenciada pelo JPA e substitui seu conteúdo também na edição.
        despesa.parcelas.clear();
        despesa.parcelas.putAll(parcelas);
    }

    public void validarAlteracaoDaDespesa(Republica republica, Long usuarioId, Despesa despesa) {
        boolean autorDoLancamento = despesa.autorId.equals(usuarioId);
        boolean administrador = republica.administradorId.equals(usuarioId);
        if (!autorDoLancamento && !administrador) {
            throw RegraException.proibido();
        }

        contexto.exigirMesAberto(republica.id, YearMonth.from(despesa.data));

        // Impede recriar dívidas de moradores que já saíram com saldo zero.
        contexto.exigirMembroDaRepublica(republica, despesa.pagadorId);
        for (Long participanteId : despesa.parcelas.keySet()) {
            contexto.exigirMembroDaRepublica(republica, participanteId);
        }
    }
}
