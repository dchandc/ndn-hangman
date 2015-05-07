package com.example.cs217b.ndn_hangman;

import java.util.Random;
import android.os.Handler;

/**
 * Created by Dennis on 5/2/2015.
 */
public class AIPlayer extends Player {
    private String[] words;

    public AIPlayer(String name) {
        this.name = name;

        words = new String[]{"alphabet", "dictionary", "alliteration", "consonant"};
    }

    @Override
    String chooseWord() {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Random rand = new Random();
        return words[rand.nextInt(words.length)];
    }

    @Override
    char chooseLetter(String letters) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Random rand = new Random();
        int index = rand.nextInt(letters.length());
        return letters.charAt(index);
    }
}
