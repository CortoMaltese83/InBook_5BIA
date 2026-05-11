package com.inbook.dto;

public class Docente {
    private String email;
    private String password;
    private  String name;
    private String surname;
    public Docente() {
    }
    public Docente(String email, String password, String name, String surname) {
        this.email = email;
        this.password = password;
        this.name = name;
        this.surname = surname;
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

//d
    public String getEmail() {
        return email;
    }

    public String getPassword() {
        return password;
    }
    public String getName() {return name;}
    public String getSurname() {return surname;}

}
