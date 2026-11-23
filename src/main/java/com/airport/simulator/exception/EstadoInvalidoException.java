package com.airport.simulator.exception;

/**
 * Lançada quando uma invariante do sistema é violada, por exemplo um avião
 * pedindo pouso enquanto já está em uma fila ou com status incompatível.
 */
public class EstadoInvalidoException extends RuntimeException {
    public EstadoInvalidoException(String mensagem) {
        super(mensagem);
    }
}
