package com.airport.simulator.service;

import java.util.concurrent.CompletableFuture;

import com.airport.simulator.model.Aviao;
import com.airport.simulator.model.Pista;

/**
 * Pedido de um avião à torre. A torre completa {@code autorizacao} com a pista
 * concedida; o avião fica bloqueado esperando esse resultado.
 */
public record Solicitacao(Aviao aviao, TipoSolicitacao tipo, CompletableFuture<Pista> autorizacao) {

    public Solicitacao(Aviao aviao, TipoSolicitacao tipo) {
        this(aviao, tipo, new CompletableFuture<>());
    }
}
