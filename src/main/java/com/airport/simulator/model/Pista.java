package com.airport.simulator.model;

import com.airport.simulator.exception.ColisaoException;

public class Pista {
    private String nome;
    private boolean ocupada;
    private Aviao aviao;

    public Pista (String nome) {
        this.nome = nome;
        this.ocupada = false;
        this.aviao = null;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }
    
    public boolean isOcupada() {
        return ocupada;
    }

    public void ocupar(Aviao aviao) {
        if (this.ocupada) {
            throw new ColisaoException("Pista " + nome + " já está ocupada por " + this.aviao.getNumeroVoo());
        }
        this.ocupada = true;
        this.aviao = aviao;
    }

    public void liberar() {
        // [ASSERCAO 1] Só se libera uma pista que estava ocupada.
        assert this.ocupada : "A pista " + nome + " foi liberada, mas já estava livre";
        this.ocupada = false;
        this.aviao = null;
    }
}