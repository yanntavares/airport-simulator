package com.airport.simulator.view;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

import com.airport.simulator.model.Aviao;
import com.airport.simulator.service.EstadoTorre;
import com.airport.simulator.service.TorreControle;

/**
 * Painel de controle: redesenha o estado da simulação no terminal a cada
 * {@code intervaloMs} milissegundos.
 *
 * <p>O estado das pistas e das filas vem de {@link TorreControle#obterEstado()},
 * que devolve uma foto consistente (lida sob a trava da torre). As mensagens
 * chegam pelo método estático {@link #registrar(String)}, que deve ser passado
 * para a torre como ouvinte de eventos: {@code new TorreControle(pistas, n, Painel::registrar)}.
 * Para registrar algo de outra classe, use {@code torre.registrarEvento("texto")},
 * que já coloca o horário na mensagem.
 */
public class Painel implements Runnable {

    private static final int MAX_MENSAGENS = 12;
    private static final DateTimeFormatter FORMATO_HORA = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Deque<String> mensagens = new ConcurrentLinkedDeque<>();

    private final TorreControle torre;
    private final List<Aviao> avioes;
    private final long intervaloMs;
    private final boolean limparTela;

    private volatile boolean ativo = true;

    public Painel(TorreControle torre, List<Aviao> avioes, long intervaloMs, boolean limparTela) {
        if (intervaloMs < 1) {
            throw new IllegalArgumentException("O intervalo do painel deve ser pelo menos 1 ms");
        }
        this.torre = torre;
        this.avioes = List.copyOf(avioes);
        this.intervaloMs = intervaloMs;
        this.limparTela = limparTela;
    }

    /** Guarda a mensagem (já formatada pela torre) para aparecer no painel. */
    public static void registrar(String mensagem) {
        mensagens.addLast(mensagem);
        while (mensagens.size() > MAX_MENSAGENS) {
            mensagens.pollFirst();
        }
    }

    @Override
    public void run() {
        while (ativo) {
            desenhar();
            try {
                Thread.sleep(intervaloMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    public void encerrar() {
        ativo = false;
    }

    private void desenhar() {
        EstadoTorre estado = torre.obterEstado();
        StringBuilder sb = new StringBuilder();

        // Limpa a tela com código ANSI; sem isso, apenas empurra o texto anterior para cima.
        sb.append(limparTela ? "\033[H\033[2J" : "\n\n");

        sb.append("==================== AEROPORTO ====================\n");
        sb.append("Hora: ").append(LocalTime.now().format(FORMATO_HORA)).append("\n\n");

        sb.append("--- PISTAS ---\n");
        for (EstadoTorre.EstadoPista pista : estado.pistas()) {
            if (pista.livre()) {
                sb.append(String.format("  %-10s LIVRE%n", pista.nome()));
            } else {
                sb.append(String.format("  %-10s OCUPADA  %-9s %s%n",
                        pista.nome(), pista.operacao(), pista.numeroVoo()));
            }
        }

        sb.append("\n--- FILA DE POUSO (").append(estado.filaPouso().size()).append(") ---\n");
        sb.append("  ").append(formatarFila(estado.filaPouso())).append("\n");

        sb.append("\n--- FILA DE DECOLAGEM (").append(estado.filaDecolagem().size()).append(") ---\n");
        sb.append("  ").append(formatarFila(estado.filaDecolagem())).append("\n");

        sb.append("\n--- AVIOES ---\n");
        for (Aviao aviao : avioes) {
            sb.append(String.format("  %-9s %-16s %s%n",
                    aviao.getNumeroVoo(), aviao.getModelo(), aviao.getStatus()));
        }

        sb.append("\n--- ULTIMAS MENSAGENS ---\n");
        for (String mensagem : mensagens) {
            sb.append("  ").append(mensagem).append("\n");
        }

        System.out.print(sb); // um único print evita a tela piscando
        System.out.flush();
    }

    private static String formatarFila(List<String> fila) {
        return fila.isEmpty() ? "(vazia)" : String.join(" -> ", fila);
    }
}