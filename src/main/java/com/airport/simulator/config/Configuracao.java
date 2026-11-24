package com.airport.simulator.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Parâmetros da simulação, lidos do arquivo {@code config.properties}
 * (na pasta de onde o programa é executado, normalmente a raiz do projeto).
 *
 * <p>Todos os tempos são em milissegundos.
 *
 * @param avioesParaPousar        aviões que começam em voo, querendo pousar
 * @param avioesParaDecolar       aviões que começam no solo, querendo decolar
 * @param pistas                  quantidade de pistas do aeroporto
 * @param tempoPousoMs            quanto tempo o avião ocupa a pista ao pousar
 * @param tempoDecolagemMs        quanto tempo o avião ocupa a pista ao decolar
 * @param tempoNoSoloMs           intervalo entre o pouso e o pedido de decolagem
 * @param tempoEmVooMs            intervalo entre a decolagem e o novo pedido de pouso
 * @param intervaloPainelMs       taxa de atualização do painel
 * @param limparTela              se o painel limpa a tela a cada atualização
 * @param maxPousosConsecutivos   pousos seguidos antes de deixar uma decolagem passar
 */
public record Configuracao(
        int avioesParaPousar,
        int avioesParaDecolar,
        int pistas,
        long tempoPousoMs,
        long tempoDecolagemMs,
        long tempoNoSoloMs,
        long tempoEmVooMs,
        long intervaloPainelMs,
        boolean limparTela,
        int maxPousosConsecutivos) {

    private static final String ARQUIVO_PADRAO = "config.properties";

    public Configuracao {
        if (avioesParaPousar < 0 || avioesParaDecolar < 0) {
            throw new IllegalArgumentException("A quantidade de aviões não pode ser negativa");
        }
        if (avioesParaPousar + avioesParaDecolar == 0) {
            throw new IllegalArgumentException("A simulação precisa de pelo menos um avião");
        }
        if (pistas < 1) {
            throw new IllegalArgumentException("O aeroporto precisa de pelo menos uma pista");
        }
        if (maxPousosConsecutivos < 1) {
            throw new IllegalArgumentException("torre.max-pousos-consecutivos deve ser pelo menos 1");
        }
        if (intervaloPainelMs < 1) {
            throw new IllegalArgumentException("painel.intervalo.ms deve ser pelo menos 1");
        }
        if (tempoPousoMs < 0 || tempoDecolagemMs < 0 || tempoNoSoloMs < 0 || tempoEmVooMs < 0) {
            throw new IllegalArgumentException("Os tempos da simulação não podem ser negativos");
        }
    }

    public static Configuracao carregar() {
        return carregar(Path.of(ARQUIVO_PADRAO));
    }

    public static Configuracao carregar(Path arquivo) {
        Properties p = new Properties();
        try (Reader leitor = new InputStreamReader(Files.newInputStream(arquivo), StandardCharsets.UTF_8)) {
            p.load(leitor);
        } catch (IOException e) {
            throw new IllegalStateException("Não foi possível ler o arquivo de configuração: "
                    + arquivo.toAbsolutePath(), e);
        }

        return new Configuracao(
                inteiro(p, "avioes.pousar"),
                inteiro(p, "avioes.decolar"),
                inteiro(p, "pistas"),
                longo(p, "tempo.pouso.ms"),
                longo(p, "tempo.decolagem.ms"),
                longo(p, "tempo.solo.ms"),
                longo(p, "tempo.voo.ms"),
                longo(p, "painel.intervalo.ms"),
                Boolean.parseBoolean(valor(p, "painel.limpar-tela")),
                inteiro(p, "torre.max-pousos-consecutivos"));
    }

    private static String valor(Properties p, String chave) {
        String valor = p.getProperty(chave);
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("Falta a chave '" + chave + "' no arquivo de configuração");
        }
        return valor.trim();
    }

    private static int inteiro(Properties p, String chave) {
        try {
            return Integer.parseInt(valor(p, chave));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A chave '" + chave + "' deve ser um número inteiro", e);
        }
    }

    private static long longo(Properties p, String chave) {
        try {
            return Long.parseLong(valor(p, chave));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("A chave '" + chave + "' deve ser um número inteiro", e);
        }
    }
}