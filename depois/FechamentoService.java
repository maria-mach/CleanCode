package br.ifsp.republica.service;

import br.ifsp.republica.model.Fechamento;
import br.ifsp.republica.model.Republica;
import br.ifsp.republica.repository.FechamentoRepository;
import java.time.YearMonth;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FechamentoService {

    private final FechamentoRepository fechamentos;
    private final ContextoRepublica contexto;

    public FechamentoService(
            FechamentoRepository fechamentos,
            ContextoRepublica contexto) {
        this.fechamentos = fechamentos;
        this.contexto = contexto;
    }

    // Fechar protege os lançamentos; não quita dívidas nem confirma pagamentos.
    @Transactional
    public void fechar(Long usuarioId, String competencia) {
        Republica republica = contexto.bloquearRepublica(usuarioId);
        contexto.exigirAdministrador(republica, usuarioId);
        YearMonth mes = contexto.converterMes(competencia);

        boolean jaFechado = fechamentos.existsByRepublicaIdAndMes(republica.id, mes.toString());
        if (jaFechado) {
            return;
        }

        Fechamento fechamento = new Fechamento();
        fechamento.republicaId = republica.id;
        fechamento.mes = mes.toString();
        fechamentos.save(fechamento);
    }
}
