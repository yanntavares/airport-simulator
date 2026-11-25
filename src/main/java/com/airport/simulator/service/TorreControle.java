package com.airport.simulator.service;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import com.airport.simulator.exception.EstadoInvalidoException;
import com.airport.simulator.model.Aviao;
import com.airport.simulator.model.Pista;
import com.airport.simulator.model.StatusAviao;

/**
 * Torre de controle: coordenadora central do aeroporto.
 *
 * <p>Os aviões (cada um em sua thread) pedem pouso ou decolagem através de
 * {@link #solicitarPouso(Aviao)} e {@link #solicitarDecolagem(Aviao)}, que
 * bloqueiam até a torre autorizar. A própria torre roda em uma thread
 * ({@link #run()}): ela escolhe o próximo pedido, reserva uma pista livre e
 * acorda o avião escolhido. Quando o avião termina de usar a pista, ele avisa
 * com {@link #liberarPista(Pista, Aviao)}.
 *
 * <p>Todo o estado interno (filas, pistas livres e pistas em uso) é protegido
 * por um único {@link ReentrantLock}. Os pontos que sustentam a exclusão mútua
 * das pistas estão marcados com {@code [CONTROLE-CONCORRENCIA]}.
 *
 * <p>Política de escolha: o pouso tem prioridade (o avião está no ar), mas
 * depois de {@code maxPousosConsecutivos} pousos seguidos, se houver
 * decolagem esperando, ela é atendida para evitar inanição.
 */
public class TorreControle implements Runnable {

    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final ReentrantLock trava = new ReentrantLock(true);
    private final Condition haTrabalho = trava.newCondition();

    private final List<Pista> pistas;
    private final Deque<Solicitacao> filaPouso = new ArrayDeque<>();
    private final Deque<Solicitacao> filaDecolagem = new ArrayDeque<>();
    private final Deque<Pista> pistasLivres = new ArrayDeque<>();
    private final Map<Pista, Solicitacao> pistasEmUso = new LinkedHashMap<>();

    private final int maxPousosConsecutivos;
    private final Consumer<String> ouvinteEventos;
    private int pousosConsecutivos = 0;

    public TorreControle(List<Pista> pistas, int maxPousosConsecutivos, Consumer<String> ouvinteEventos) {
        if (pistas == null || pistas.isEmpty()) {
            throw new IllegalArgumentException("A torre precisa de ao menos uma pista");
        }
        if (maxPousosConsecutivos < 1) {
            throw new IllegalArgumentException("maxPousosConsecutivos deve ser pelo menos 1");
        }
        this.pistas = List.copyOf(pistas);
        this.pistasLivres.addAll(pistas);
        this.maxPousosConsecutivos = maxPousosConsecutivos;
        this.ouvinteEventos = ouvinteEventos;
    }

    public TorreControle(List<Pista> pistas, int maxPousosConsecutivos) {
        this(pistas, maxPousosConsecutivos, System.out::println);
    }

    // ------------------------------------------------------------------
    // API usada pelos aviões
    // ------------------------------------------------------------------

    /** Pede autorização de pouso. Bloqueia até a torre conceder uma pista. */
    public Pista solicitarPouso(Aviao aviao) throws InterruptedException {
        return solicitar(aviao, TipoSolicitacao.POUSO);
    }

    /** Pede autorização de decolagem. Bloqueia até a torre conceder uma pista. */
    public Pista solicitarDecolagem(Aviao aviao) throws InterruptedException {
        return solicitar(aviao, TipoSolicitacao.DECOLAGEM);
    }

    /** O avião avisa que terminou de usar a pista; ela volta a ficar livre. */
    public void liberarPista(Pista pista, Aviao aviao) {
        trava.lock();
        try {
            liberarInterno(pista, aviao);
        } finally {
            trava.unlock();
        }
    }

    // ------------------------------------------------------------------
    // API usada pelo painel
    // ------------------------------------------------------------------

    /** Foto consistente das filas e das pistas no momento da chamada. */
    public EstadoTorre obterEstado() {
        trava.lock();
        try {
            List<String> pouso = filaPouso.stream().map(s -> s.aviao().getNumeroVoo()).toList();
            List<String> decolagem = filaDecolagem.stream().map(s -> s.aviao().getNumeroVoo()).toList();

            List<EstadoTorre.EstadoPista> situacao = new ArrayList<>();
            for (Pista pista : pistas) {
                Solicitacao uso = pistasEmUso.get(pista);
                situacao.add(uso == null
                        ? new EstadoTorre.EstadoPista(pista.getNome(), null, null)
                        : new EstadoTorre.EstadoPista(pista.getNome(), uso.aviao().getNumeroVoo(), uso.tipo()));
            }
            return new EstadoTorre(pouso, decolagem, List.copyOf(situacao));
        } finally {
            trava.unlock();
        }
    }

    /** Registra uma mensagem com horário e a envia ao ouvinte (painel). */
    public void registrarEvento(String mensagem) {
        if (ouvinteEventos != null) {
            ouvinteEventos.accept("[" + LocalTime.now().format(FORMATO_HORA) + "] " + mensagem);
        }
    }

    // ------------------------------------------------------------------
    // Thread da torre
    // ------------------------------------------------------------------

    @Override
    public void run() {
        registrarEvento("Torre de controle em operação com " + pistas.size() + " pista(s)");
        trava.lock();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                while (!podeConceder()) {
                    haTrabalho.await();
                }
                conceder();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            trava.unlock();
        }
        registrarEvento("Torre de controle encerrou as operações");
    }

    // ------------------------------------------------------------------
    // Internos (sempre chamados com a trava adquirida)
    // ------------------------------------------------------------------

    private Pista solicitar(Aviao aviao, TipoSolicitacao tipo) throws InterruptedException {
        Solicitacao solicitacao = new Solicitacao(aviao, tipo);

        trava.lock();
        try {
            validarSolicitacao(aviao, tipo);
            filaDe(tipo).addLast(solicitacao);
            registrarEvento(aviao.getNumeroVoo() + " solicitou " + descricao(tipo)
                    + " (fila de " + descricao(tipo) + ": " + filaDe(tipo).size() + ")");
            haTrabalho.signalAll();
        } finally {
            trava.unlock();
        }

        // A espera acontece FORA da trava para não bloquear a torre nem os outros aviões.
        try {
            return solicitacao.autorizacao().get();
        } catch (InterruptedException e) {
            cancelarSolicitacao(solicitacao);
            throw e;
        } catch (ExecutionException e) {
            throw new IllegalStateException("Falha ao obter autorização da torre", e.getCause());
        }
    }

    /** Invariantes: status coerente com o pedido e avião fora das filas e das pistas. */
    private void validarSolicitacao(Aviao aviao, TipoSolicitacao tipo) {
        StatusAviao esperado = tipo == TipoSolicitacao.POUSO
                ? StatusAviao.QUERENDO_POUSAR
                : StatusAviao.QUERENDO_DECOLAR;
        if (aviao.getStatus() != esperado) {
            throw new EstadoInvalidoException("O voo " + aviao.getNumeroVoo() + " pediu " + descricao(tipo)
                    + " com status " + aviao.getStatus() + " (esperado: " + esperado + ")");
        }
        if (estaEmAlgumaFila(aviao) || estaUsandoPista(aviao)) {
            throw new EstadoInvalidoException("O voo " + aviao.getNumeroVoo()
                    + " pediu " + descricao(tipo) + " mas já está em uma fila ou usando uma pista");
        }
    }

    private boolean estaEmAlgumaFila(Aviao aviao) {
        return filaPouso.stream().anyMatch(s -> s.aviao() == aviao)
                || filaDecolagem.stream().anyMatch(s -> s.aviao() == aviao);
    }

    private boolean estaUsandoPista(Aviao aviao) {
        return pistasEmUso.values().stream().anyMatch(s -> s.aviao() == aviao);
    }

    /** Avião interrompido enquanto esperava: sai da fila ou devolve a pista já concedida. */
    private void cancelarSolicitacao(Solicitacao solicitacao) {
        trava.lock();
        try {
            if (solicitacao.autorizacao().cancel(false)) {
                filaDe(solicitacao.tipo()).remove(solicitacao);
                registrarEvento(solicitacao.aviao().getNumeroVoo() + " cancelou o pedido de "
                        + descricao(solicitacao.tipo()));
            } else {
                // A torre concedeu a pista no mesmo instante: devolve para não perdê-la.
                Pista concedida = solicitacao.autorizacao().join();
                liberarInterno(concedida, solicitacao.aviao());
            }
        } finally {
            trava.unlock();
        }
    }

    private boolean podeConceder() {
        return !pistasLivres.isEmpty() && (!filaPouso.isEmpty() || !filaDecolagem.isEmpty());
    }

    private void conceder() {
        TipoSolicitacao tipo = escolherTipo();
        Solicitacao solicitacao = filaDe(tipo).pollFirst();

        // [CONTROLE-CONCORRENCIA] A pista concedida precisa sair da lista de livres.
        // Se a linha "pistasLivres.removeFirst()" abaixo for comentada, a pista continua
        // "livre" e a torre a concede de novo enquanto está ocupada:
        //   - com "java -ea": a [ASSERCAO 2] dispara logo na 1ª autorização
        //     ("Contagem de pistas inconsistente: 2 livre(s) + 1 em uso...");
        //   - sem "-ea": pista.ocupar() lança a ColisaoException
        //     ("Pista X já está ocupada por Y") quando a pista é concedida de novo.
        // Nos dois casos a simulação para e mostra o motivo na tela.
        Pista pista = pistasLivres.peekFirst();
        pistasLivres.removeFirst();   // [CONTROLE-CONCORRENCIA] comentar esta linha desativa o controle

        pistasEmUso.put(pista, solicitacao);
        pista.ocupar(solicitacao.aviao());
        Aviao aviao = solicitacao.aviao();
        aviao.setStatus(tipo == TipoSolicitacao.POUSO ? StatusAviao.POUSANDO : StatusAviao.DECOLANDO);

        atualizarContadorDePousos(tipo);
        registrarEvento("Torre autorizou " + descricao(tipo) + " de " + aviao.getNumeroVoo()
                + " na pista " + pista.getNome());

        verificarInvariantes();
        solicitacao.autorizacao().complete(pista);
    }

    /** Pouso tem prioridade, exceto quando a decolagem já esperou demais. */
    private TipoSolicitacao escolherTipo() {
        if (filaPouso.isEmpty()) {
            return TipoSolicitacao.DECOLAGEM;
        }
        if (filaDecolagem.isEmpty()) {
            return TipoSolicitacao.POUSO;
        }
        return pousosConsecutivos >= maxPousosConsecutivos
                ? TipoSolicitacao.DECOLAGEM
                : TipoSolicitacao.POUSO;
    }

    private void atualizarContadorDePousos(TipoSolicitacao concedido) {
        pousosConsecutivos = concedido == TipoSolicitacao.POUSO ? pousosConsecutivos + 1 : 0;
    }

    private void liberarInterno(Pista pista, Aviao aviao) {
        Solicitacao uso = pistasEmUso.get(pista);
        if (uso == null) {
            throw new EstadoInvalidoException("O voo " + aviao.getNumeroVoo() + " liberou a pista "
                    + pista.getNome() + ", que não estava em uso");
        }
        if (uso.aviao() != aviao) {
            throw new EstadoInvalidoException("O voo " + aviao.getNumeroVoo() + " tentou liberar a pista "
                    + pista.getNome() + ", que está com o voo " + uso.aviao().getNumeroVoo());
        }

        pistasEmUso.remove(pista);
        pista.liberar();
        pistasLivres.addLast(pista);
        registrarEvento(aviao.getNumeroVoo() + " liberou a pista " + pista.getNome());

        verificarInvariantes();
        haTrabalho.signalAll();
    }

    /**
     * Asserções (só rodam com {@code java -ea}). Conferem, depois de toda
     * mudança no estado, regras que nunca podem ser quebradas. Se o controle de
     * concorrência falhar, uma delas dispara um {@link AssertionError} com o motivo.
     * Sempre chamada com a trava adquirida.
     */
    private void verificarInvariantes() {
        // [ASSERCAO 2] Nenhuma pista some nem é duplicada: livres + em uso = total.
        assert pistasLivres.size() + pistasEmUso.size() == pistas.size()
                : "Contagem de pistas inconsistente: " + pistasLivres.size() + " livre(s) + "
                + pistasEmUso.size() + " em uso, mas o aeroporto tem " + pistas.size();

        // [ASSERCAO 3] Uma pista nunca está livre e em uso ao mesmo tempo.
        for (Pista livre : pistasLivres) {
            assert !pistasEmUso.containsKey(livre) && !livre.isOcupada()
                    : "A pista " + livre.getNome() + " está na lista de livres, mas está em uso";
        }

        // [ASSERCAO 4] Cada avião aparece em no máximo um lugar (uma fila OU uma pista).
        Set<Aviao> vistos = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Solicitacao s : filaPouso) {
            assert vistos.add(s.aviao()) : "O voo " + s.aviao().getNumeroVoo() + " aparece duas vezes";
        }
        for (Solicitacao s : filaDecolagem) {
            assert vistos.add(s.aviao()) : "O voo " + s.aviao().getNumeroVoo() + " aparece duas vezes";
        }
        for (Solicitacao s : pistasEmUso.values()) {
            assert vistos.add(s.aviao()) : "O voo " + s.aviao().getNumeroVoo()
                    + " está em uma fila e usando uma pista ao mesmo tempo";
        }

        // [ASSERCAO 5] Quem está na pista tem status coerente com a operação autorizada.
        for (Map.Entry<Pista, Solicitacao> uso : pistasEmUso.entrySet()) {
            StatusAviao esperado = uso.getValue().tipo() == TipoSolicitacao.POUSO
                    ? StatusAviao.POUSANDO
                    : StatusAviao.DECOLANDO;
            Aviao aviao = uso.getValue().aviao();
            assert aviao.getStatus() == esperado
                    : "O voo " + aviao.getNumeroVoo() + " está na " + uso.getKey().getNome()
                    + " com status " + aviao.getStatus() + " (esperado: " + esperado + ")";
        }
    }

    private Deque<Solicitacao> filaDe(TipoSolicitacao tipo) {
        return tipo == TipoSolicitacao.POUSO ? filaPouso : filaDecolagem;
    }

    private static String descricao(TipoSolicitacao tipo) {
        return tipo == TipoSolicitacao.POUSO ? "pouso" : "decolagem";
    }
}
