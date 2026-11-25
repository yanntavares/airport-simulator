package com.airport.simulator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.airport.simulator.config.Configuracao;
import com.airport.simulator.model.Aviao;
import com.airport.simulator.model.Pista;
import com.airport.simulator.model.StatusAviao;
import com.airport.simulator.service.TorreControle;
import com.airport.simulator.view.Painel;

/**
 * Ponto de entrada: lê a configuração, monta pistas, torre, aviões e painel,
 * e inicia cada um em sua própria thread. A simulação é infinita (Ctrl+C encerra).
 *
 * <p>Uso: {@code java -ea -cp target/classes com.airport.simulator.Application [arquivo.properties]}.
 * Sem argumento, usa {@code config.properties}.
 */
public class Application {

    // Prefixos de voos de empresas brasileiras: Azul, LATAM, GOL, Passaredo.
    private static final String[] COMPANHIAS = {"AD", "LA", "G3", "2Z"};
    private static final String[] MODELOS = {"Airbus A320", "Boeing 737-800", "Embraer E195", "ATR 72-600"};
    private static final int[] CAPACIDADES = {174, 186, 118, 70};

    /** Garante que só a primeira falha é relatada, mesmo que várias threads falhem juntas. */
    private static final AtomicBoolean falhaRelatada = new AtomicBoolean(false);

    public static void main(String[] args) {
        Configuracao config = args.length > 0
                ? Configuracao.carregar(Path.of(args[0]))
                : Configuracao.carregar();

        List<Pista> pistas = criarPistas(config.pistas());
        TorreControle torre = new TorreControle(pistas, config.maxPousosConsecutivos(), Painel::registrar);
        List<Aviao> avioes = criarAvioes(config, torre);
        Painel painel = new Painel(torre, avioes, config.intervaloPainelMs(), config.limparTela());

        torre.registrarEvento("Simulação configurada: " + config.avioesParaPousar() + " avião(ões) para pousar, "
                + config.avioesParaDecolar() + " para decolar, " + config.pistas() + " pista(s)");
        torre.registrarEvento(assercoesAtivas()
                ? "Asserções ATIVADAS (-ea)"
                : "Asserções DESATIVADAS: rode com 'java -ea' para verificá-las");

        Thread threadPainel = criarThread("painel", painel);

        // Qualquer exceção não tratada (ColisaoException, EstadoInvalidoException,
        // AssertionError...) em qualquer thread para a simulação e é exibida na tela.
        Thread.setDefaultUncaughtExceptionHandler(
                (thread, erro) -> relatarFalhaEEncerrar(thread, erro, painel, threadPainel));

        threadPainel.start();
        criarThread("torre", torre).start();
        for (Aviao aviao : avioes) {
            criarThread(aviao.getNumeroVoo(), aviao).start();
        }
    }

    /**
     * Para o painel (senão ele apagaria a mensagem no próximo redesenho), mostra
     * qual thread falhou e por quê, e encerra o programa.
     */
    private static void relatarFalhaEEncerrar(Thread thread, Throwable erro, Painel painel, Thread threadPainel) {
        if (!falhaRelatada.compareAndSet(false, true)) {
            return;
        }

        painel.encerrar();
        if (thread != threadPainel) {
            threadPainel.interrupt();
            try {
                threadPainel.join(2000); // espera o painel terminar o desenho atual
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println();
        System.out.println("################## SIMULAÇÃO INTERROMPIDA ##################");
        System.out.println("  Thread   : " + thread.getName());
        System.out.println("  Exceção  : " + erro.getClass().getSimpleName());
        System.out.println("  Motivo   : " + erro.getMessage());
        System.out.println("############################################################");
        System.out.println();
        erro.printStackTrace(System.out);
        System.out.flush();

        System.exit(1);
    }

    @SuppressWarnings("AssertWithSideEffects")
    private static boolean assercoesAtivas() {
        boolean ativas = false;
        assert ativas = true; // só executa (e muda o valor) quando a JVM roda com -ea
        return ativas;
    }

    private static List<Pista> criarPistas(int quantidade) {
        List<Pista> pistas = new ArrayList<>();
        for (int i = 1; i <= quantidade; i++) {
            pistas.add(new Pista("Pista " + i));
        }
        return pistas;
    }

    private static List<Aviao> criarAvioes(Configuracao config, TorreControle torre) {
        Random sorteio = new Random();
        Set<String> voosUsados = new HashSet<>();
        List<Aviao> avioes = new ArrayList<>();

        for (int i = 0; i < config.avioesParaPousar(); i++) {
            avioes.add(novoAviao(sorteio, voosUsados, StatusAviao.QUERENDO_POUSAR, torre, config));
        }
        for (int i = 0; i < config.avioesParaDecolar(); i++) {
            avioes.add(novoAviao(sorteio, voosUsados, StatusAviao.QUERENDO_DECOLAR, torre, config));
        }
        return avioes;
    }

    private static Aviao novoAviao(Random sorteio, Set<String> voosUsados, StatusAviao statusInicial, TorreControle torre, Configuracao configuracao) {
        String numeroVoo;
        do {
            numeroVoo = COMPANHIAS[sorteio.nextInt(COMPANHIAS.length)] + " " + (1000 + sorteio.nextInt(9000));
        } while (!voosUsados.add(numeroVoo)); // garante número de voo único

        int modelo = sorteio.nextInt(MODELOS.length);
        return new Aviao(MODELOS[modelo], CAPACIDADES[modelo], numeroVoo, statusInicial, torre, configuracao);
    }

    private static Thread criarThread(String nome, Runnable tarefa) {
        return new Thread(tarefa, nome);
    }
}
