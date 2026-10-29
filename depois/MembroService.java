package br.ifsp.republica.service;

import br.ifsp.republica.dto.Contratos.Convite;
import br.ifsp.republica.dto.Contratos.NovaRepublica;
import br.ifsp.republica.dto.Contratos.Usuario;
import br.ifsp.republica.exception.RegraException;
import br.ifsp.republica.model.Morador;
import br.ifsp.republica.model.Pagamento;
import br.ifsp.republica.model.Republica;
import br.ifsp.republica.repository.MoradorRepository;
import br.ifsp.republica.repository.PagamentoRepository;
import br.ifsp.republica.repository.RepublicaRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MembroService {

    private final MoradorRepository moradores;
    private final RepublicaRepository republicas;
    private final PagamentoRepository pagamentos;
    private final SaldoService saldos;
    private final ContextoRepublica contexto;

    public MembroService(
            MoradorRepository moradores,
            RepublicaRepository republicas,
            PagamentoRepository pagamentos,
            SaldoService saldos,
            ContextoRepublica contexto) {
        this.moradores = moradores;
        this.republicas = republicas;
        this.pagamentos = pagamentos;
        this.saldos = saldos;
        this.contexto = contexto;
    }

    public Morador morador(Long moradorId) {
        return contexto.morador(moradorId);
    }

    public Republica republica(Long usuarioId) {
        return contexto.republica(usuarioId);
    }

    public List<Usuario> membros(Long usuarioId) {
        Republica republica = contexto.republica(usuarioId);
        List<Morador> membros = moradores.findByRepublicaIdOrderById(republica.id);

        return membros.stream()
            .map(AuthService::usuario)
            .toList();
    }

    @Transactional
    public Republica criar(Long usuarioId, NovaRepublica novaRepublica) {
        Morador morador = contexto.bloquearMorador(usuarioId);
        contexto.exigirMoradorSemRepublica(morador);

        Republica republica = new Republica();
        republica.nome = novaRepublica.nome().strip();
        republica.administradorId = usuarioId;
        republica.codigoConvite = UUID.randomUUID().toString();

        republicas.saveAndFlush(republica);
        morador.republicaId = republica.id;

        return republica;
    }

    @Transactional
    public Republica entrar(Long usuarioId, Convite convite) {
        Republica republicaEncontrada = republicas.findByCodigoConvite(convite.codigo())
            .orElseThrow(RegraException::ausente);

        Republica republica = republicas.lockById(republicaEncontrada.id)
            .orElseThrow(RegraException::ausente);
        Morador morador = contexto.bloquearMorador(usuarioId);
        contexto.exigirMoradorSemRepublica(morador);

        morador.republicaId = republica.id;

        return republica;
    }

    @Transactional
    public Republica transferir(Long usuarioId, Long novoAdministradorId) {
        Republica republica = contexto.bloquearRepublica(usuarioId);
        contexto.exigirAdministrador(republica, usuarioId);
        contexto.exigirMembroDaRepublica(republica, novoAdministradorId);

        republica.administradorId = novoAdministradorId;

        return republica;
    }

    // O vínculo é removido, mas o morador e seu histórico financeiro são preservados.
    @Transactional
    public void sair(Long usuarioId, Long moradorId) {
        Republica republica = contexto.bloquearRepublica(usuarioId);
        if (!usuarioId.equals(moradorId)) {
            contexto.exigirAdministrador(republica, usuarioId);
        }

        contexto.exigirMembroDaRepublica(republica, moradorId);
        validarSaida(republica, moradorId);

        Morador morador = contexto.bloquearMorador(moradorId);
        morador.republicaId = null;
    }

    private void validarSaida(Republica republica, Long moradorId) {
        if (republica.administradorId.equals(moradorId)) {
            throw RegraException.invalido("Transfira a administração antes de sair");
        }

        BigDecimal saldo = saldos.calcularSaldos(republica.id).getOrDefault(moradorId, BigDecimal.ZERO);
        if (saldo.signum() != 0) {
            throw RegraException.invalido("Só é possível sair com saldo zero");
        }

        if (possuiPagamentoPendente(republica.id, moradorId)) {
            throw RegraException.invalido("Confirme os pagamentos pendentes antes de sair");
        }
    }

    private boolean possuiPagamentoPendente(Long republicaId, Long moradorId) {
        List<Pagamento> pagamentosDaRepublica =
            pagamentos.findByRepublicaIdOrderByDataAscIdAsc(republicaId);

        return pagamentosDaRepublica.stream()
            .anyMatch(pagamento -> !pagamento.confirmado && contexto.envolveMorador(pagamento, moradorId));
    }
}
