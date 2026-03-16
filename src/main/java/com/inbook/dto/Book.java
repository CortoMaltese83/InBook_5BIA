package com.inbook.dto;

public class Book {
    private String isbn;
    private String autore;
    private String titolo;
    private int volume;
    private String casaEditrice;
    private double prezzo;
    private boolean daAcquistare;
    private boolean consigliato;

    public Book(){
    }

    public Book(String isbn, String autore, String titolo, int volume, String casaEditrice, double prezzo, boolean daAcquistare, boolean consigliato) {
        this.isbn = isbn;
        this.autore = autore;
        this.titolo = titolo;
        this.volume = volume;
        this.casaEditrice = casaEditrice;
        this.prezzo = prezzo;
        this.daAcquistare = daAcquistare;
        this.consigliato = consigliato;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getAutore() {
        return autore;
    }

    public void setAutore(String autore) {
        this.autore = autore;
    }

    public String getTitolo() {
        return titolo;
    }

    public void setTitolo(String titolo) {
        this.titolo = titolo;
    }

    public int getVolume() {
        return volume;
    }

    public void setVolume(int volume) {
        this.volume = volume;
    }

    public String getCasaEditrice() {
        return casaEditrice;
    }

    public void setCasaEditrice(String casaEditrice) {
        this.casaEditrice = casaEditrice;
    }

    public double getPrezzo() {
        return prezzo;
    }

    public void setPrezzo(double prezzo) {
        this.prezzo = prezzo;
    }

    public boolean isDaAcquistare() {
        return daAcquistare;
    }

    public void setDaAcquistare(boolean daAcquistare) {
        this.daAcquistare = daAcquistare;
    }

    public boolean isConsigliato() {
        return consigliato;
    }

    public void setConsigliato(boolean consigliato) {
        this.consigliato = consigliato;
    }
}