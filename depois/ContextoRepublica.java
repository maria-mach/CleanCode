package br.ifsp.republica.service;

import br.ifsp.republica.dto.Contratos.NovaDespesa;
import br.ifsp.republica.dto.Contratos.Participante;
import br.ifsp.republica.exception.RegraException;
import br.ifsp.republica.model.Morador;
import br.ifsp.republica.model.Pagamento;
import br.ifsp.republica.model.Republica;
import br.ifsp.republica.repository.FechamentoRepository;
import br.ifsp.republica.repository.MoradorRepository;
import br.ifsp.republica.repository.RepublicaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

// Usa a transação do serviço chamador para manter a trava até o fim da operação.
@Component
public class ContextoRepublica {

    @PersistenceContext
    private EntityManager entityManager;

    private final MoradorRepository moradores;
    private final RepublicaRepository republicas;
    private final FechamentoRepository fechamentos;

    public ContextoRepublica(
            MoradorRepository moradores,
            RepublicaRepository republicas,
            FechamentoRepository fechamentos) {
        this.moradores = moradores;
        this.republicas = republicas;
        this.fechamentos = fechamentos;
    }

    public Morador morador(Long moradorId) {
        return moradores.findById(moradorId)
            .orElseThrow(RegraException::ausente);
    }

    public Republica republica(Long usuarioId) {
        Long republicaId = morador(usuarioId).republicaId;
        if (republicaId == null) {
            throw RegraException.invalido("Entre ou crie uma república primeiro");
        }

        return republicas.findById(republicaId)
            .orElseThrow(RegraException::ausente);
    }

    // As alterações financeiras e de membros compartilham a mesma trava por república.
    // Os dados podem mudar durante a espera pela trava, por isso são recarregados depois dela.
    public Republica bloquearRepublica(Long usuarioId) {
        Republica republicaEncontrada = republica(usuarioId);
        Republica republicaBloqueada = republicas.lockById(republicaEncontrada.id)
            .orElseThrow(RegraException::ausente);
        entityManager.refresh(republicaBloqueada);

        Morador morador = morador(usuarioId);
        entityManager.refresh(morador);

        if (!republicaBloqueada.id.equals(morador.republicaId)) {
            throw RegraException.proibido();
        }

        return republicaBloqueada;
    }

    public Morador bloquearMorador(Long moradorId) {
        return moradores.lockById(moradorId)
            .orElseThrow(RegraException::ausente);
    }

    public void exigirAdministrador(Republica republica, Long usuarioId) {
        if (!republica.administradorId.equals(usuarioId)) {
            throw RegraException.proibido();
        }
    }

    public void exigirMembroDaRepublica(Republica republica, Long moradorId) {
        Morador morador = morador(moradorId);
        if (!republica.id.equals(morador.republicaId)) {
            throw RegraException.invalido("Todos os envolvidos devem pertencer à sua república");
        }
    }

    public void exigirMoradorSemRepublica(Morador morador) {
        if (morador.republicaId != null) {
            throw RegraException.invalido("Você já pertence a uma república");
        }
    }

    public YearMonth converterMes(String competencia) {
        try {
            return YearMonth.parse(competencia);
        } catch (DateTimeParseException | NullPointerException exception) {
            throw RegraException.invalido("Mês deve ter formato AAAA-MM");
        }
    }

    public void exigirMesAberto(Long republicaId, YearMonth mes) {
        if (fechamentos.existsByRepublicaIdAndMes(republicaId, mes.toString())) {
            throw RegraException.invalido("Mês fechado: despesas não podem ser alteradas");
        }
    }

    public void validarParticipantes(Republica republica, NovaDespesa novaDespesa) {
        exigirMembroDaRepublica(republica, novaDespesa.pagadorId());
        for (Participante participante : novaDespesa.participantes()) {
            exigirMembroDaRepublica(republica, participante.moradorId());
        }
    }

    public boolean envolveMorador(Pagamento pagamento, Long moradorId) {
        return pagamento.pagadorId.equals(moradorId) || pagamento.recebedorId.equals(moradorId);
    }
}
