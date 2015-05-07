package com.example.cs217b.ndn_hangman;

/**
 * Created by Dennis on 5/2/2015.
 */
public class HumanPlayer extends Player {
    boolean isLocal;

    public HumanPlayer(String name, boolean isLocal) {
        this.name = name;
        this.isLocal = isLocal;
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
