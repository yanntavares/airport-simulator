package com.airport.simulator.model;

public class Aviao implements Runnable {
    private String modelo;
    private int capacidade;
    private String numeroVoo;
    private StatusAviao status;

    public Aviao(String modelo, int capacidade, String numeroVoo, StatusAviao status) {
        this.modelo = modelo;
        this.capacidade = capacidade;
        this.numeroVoo = numeroVoo;
        this.status = status;
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

    // aqui vou precisar da torre de controle para definir o ciclo de vida do avião
    @Override
    public void run() {
    }
}