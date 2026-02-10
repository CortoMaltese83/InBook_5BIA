package com.inbook.controller;

public class Docente {
    private String email;
    private String password;
    private  String name;
    private String surname;
    private String username;
    public Docente() {
    }
    public Docente(String email, String password, String name, String surname, String username) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.surname = surname;
        this.username = username;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setSurname(String surname) {
        this.surname = surname;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
    public String getName() {return name;}
    public String getSurname() {return surname;}
    public String getUsername() {return username;}

}
