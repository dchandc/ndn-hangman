package com.example.cs217b.ndn_hangman;

/**
 * Created by Dennis on 5/2/2015.
 */
public abstract class Player {
    public int score;
    public String name;
    abstract String chooseWord();
    abstract char chooseLetter(String letters);
}