package com.airport.simulator.service;

import java.util.List;

/**
 * Foto imutável do estado da torre, usada pelo painel para exibir as filas e
 * o uso das pistas sem precisar acessar as estruturas internas da torre.
 *
 * @param filaPouso       números de voo esperando para pousar, em ordem de chegada
 * @param filaDecolagem   números de voo esperando para decolar, em ordem de chegada
 * @param pistas          situação de cada pista
 */
public record EstadoTorre(List<String> filaPouso, List<String> filaDecolagem, List<EstadoPista> pistas) {

    /**
     * @param nome        nome da pista
     * @param numeroVoo   voo que está com a pista autorizada, ou {@code null} se estiver livre
     * @param operacao    operação em andamento, ou {@code null} se estiver livre
     */
    public record EstadoPista(String nome, String numeroVoo, TipoSolicitacao operacao) {
        public boolean livre() {
            return numeroVoo == null;
        }
    }
}
