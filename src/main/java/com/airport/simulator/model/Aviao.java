package com.airport.simulator.model;
import com.airport.simulator.config.Configuracao;
import com.airport.simulator.service.TorreControle;

public class Aviao implements Runnable {
    private final String modelo;
    private final int capacidade;
    private final String numeroVoo;
    private volatile StatusAviao status;
    private final TorreControle torreControle;
    private final Configuracao configuracao;

    public Aviao(String modelo, int capacidade, String numeroVoo, StatusAviao status, TorreControle torreControle, Configuracao configuracao) {
        this.modelo = modelo;
        this.capacidade = capacidade;
        this.numeroVoo = numeroVoo;
        this.status = status;
        this.torreControle = torreControle;
        this.configuracao = configuracao;
    }

    public String getModelo() {
        return modelo;
    }

    public String getNumeroVoo() {
        return numeroVoo;
    }

    public int getCapacidade() {
        return capacidade;
    }

    public StatusAviao getStatus() {
        return status;
    }

    public void setStatus(StatusAviao status) {
        this.status = status;
    } 

    public void setStatus(int codigo) {
        this.status = StatusAviao.fromCodigo(codigo);
    }

    @Override
    public void run() {
        while (true) {
            try {
                switch (status) {
            case QUERENDO_DECOLAR:
                try {
                    Pista pista = torreControle.solicitarDecolagem(this);
                    try {
                        Thread.sleep(configuracao.tempoDecolagemMs());
                    } finally {
                        torreControle.liberarPista(pista, this);
                    }
                    setStatus(StatusAviao.EM_VOO);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                break;
            case EM_VOO:
                Thread.sleep(configuracao.tempoEmVooMs());
                this.setStatus(StatusAviao.QUERENDO_POUSAR);
                break;
            case QUERENDO_POUSAR:
                try {
                    Pista pista = torreControle.solicitarPouso(this);
                    try {
                        Thread.sleep(configuracao.tempoPousoMs());
                    } finally {
                        torreControle.liberarPista(pista, this);
                    }
                    this.setStatus(StatusAviao.EM_SOLO);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                break;
            case EM_SOLO:
                Thread.sleep(configuracao.tempoNoSoloMs());
                this.setStatus(StatusAviao.QUERENDO_DECOLAR);
                break;
            default:
                throw new IllegalStateException("Status de avião desconhecido: " + status);
        }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
