package com.example.cs217b.ndn_hangman;

import java.util.Random;
import android.os.Handler;

/**
 * Created by Dennis on 5/2/2015.
 */
public class AIPlayer extends Player {

    public AIPlayer(String name) {
        this.name = name;
    }

    @Override
    String chooseWord() {
        return "hangman";
    }

    @Override
    char chooseLetter(String letters) {
        Random rand = new Random();
        int index = rand.nextInt(letters.length());
        return letters.charAt(index);
    }
}
