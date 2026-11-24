package com.airport.simulator.model;
import com.airport.simulator.config.Configuracao;
import com.airport.simulator.service.TorreControle;

public class Aviao implements Runnable {
    private String modelo;
    private int capacidade;
    private String numeroVoo;
    private volatile StatusAviao status;
    private TorreControle torreControle;
    private Configuracao configuracao;

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

    public void setModelo(String modelo) {
        this.modelo = modelo;
    }

    public String getNumeroVoo() {
        return numeroVoo;
    }

    public void setNumeroVoo(String numeroVoo) {
        this.numeroVoo = numeroVoo;
    }

    public int getCapacidade() {
        return capacidade;
    }

    public void setCapacidade(int capacidade) {
        this.capacidade = capacidade;
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
