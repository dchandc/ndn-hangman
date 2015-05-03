package com.example.cs217b.ndn_hangman;

/**
 * Created by Dennis on 5/2/2015.
 */
public class HumanPlayer extends Player {

    public HumanPlayer(String name) {
        this.name = name;
    }

    @Override
    String chooseWord() {
        return null;
    }

    @Override
    char chooseLetter(String letters) {
        return 0;
    }
}
