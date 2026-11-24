package com.airport.simulator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import com.airport.simulator.config.Configuracao;
import com.airport.simulator.model.Aviao;
import com.airport.simulator.model.Pista;
import com.airport.simulator.model.StatusAviao;
import com.airport.simulator.service.TorreControle;
import com.airport.simulator.view.Painel;

/**
 * Ponto de entrada: lê a configuração, monta pistas, torre, aviões e painel,
 * e inicia cada um em sua própria thread. A simulação é infinita (Ctrl+C encerra).
 */
public class Application {

    // Prefixos de voos de empresas brasileiras: Azul, LATAM, GOL, Passaredo.
    private static final String[] COMPANHIAS = {"AD", "LA", "G3", "2Z"};
    private static final String[] MODELOS = {"Airbus A320", "Boeing 737-800", "Embraer E195", "ATR 72-600"};
    private static final int[] CAPACIDADES = {174, 186, 118, 70};

    public static void main(String[] args) {
        Configuracao config = Configuracao.carregar();

        List<Pista> pistas = criarPistas(config.pistas());
        TorreControle torre = new TorreControle(pistas, config.maxPousosConsecutivos(), Painel::registrar);
        List<Aviao> avioes = criarAvioes(config, torre);
        Painel painel = new Painel(torre, avioes, config.intervaloPainelMs(), config.limparTela());

        torre.registrarEvento("Simulação configurada: " + config.avioesParaPousar() + " avião(ões) para pousar, "
                + config.avioesParaDecolar() + " para decolar, " + config.pistas() + " pista(s)");

        iniciar("painel", painel);
        iniciar("torre", torre);

        for (Aviao aviao : avioes) {
            iniciar(aviao.getNumeroVoo(), aviao);
        }
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

    private static void iniciar(String nome, Runnable tarefa) {
        new Thread(tarefa, nome).start();
    }
}