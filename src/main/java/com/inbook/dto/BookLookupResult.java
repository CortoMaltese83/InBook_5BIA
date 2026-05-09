package com.inbook.dto;

public class BookLookupResult {
    private final String isbn;
    private final String autore;
    private final String titolo;
    private final Integer volume;
    private final String casaEditrice;
    private final Double prezzo;
    private final String source;

    public BookLookupResult(String isbn, String autore, String titolo, Integer volume,
                            String casaEditrice, Double prezzo, String source) {
        this.isbn = isbn;
        this.autore = autore;
        this.titolo = titolo;
        this.volume = volume;
        this.casaEditrice = casaEditrice;
        this.prezzo = prezzo;
        this.source = source;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getAutore() {
        return autore;
    }

    public String getTitolo() {
        return titolo;
    }

    public Integer getVolume() {
        return volume;
    }

    public String getCasaEditrice() {
        return casaEditrice;
    }

    public Double getPrezzo() {
        return prezzo;
    }

    public String getSource() {
        return source;
    }
}
