package com.example.cs217b.ndn_hangman;

import java.util.Random;
import android.os.Handler;

/**
 * Created by Dennis on 5/2/2015.
 */
public class AIPlayer extends Player {
    private static final long thinkTime = 500;
    private String[] words;
    private Random rand;
    private long startTime;

    public AIPlayer(String name) {
        this.name = name;
        this.score = 0;
        this.sessionNo = 0;
        this.sid = name;
        this.isLocal = true;

        words = new String[]{"alphabet", "dictionary", "alliteration", "consonant"};
        rand = new Random();
        startTime = 0;
    }

    @Override
    public String chooseWord() {
        if (startTime != 0 && System.currentTimeMillis() - startTime >= thinkTime) {
            startTime = 0;
            return words[rand.nextInt(words.length)];
        }

        return "";
    }

    @Override
    public char chooseLetter(String letters) {
        if (startTime != 0 && System.currentTimeMillis() - startTime >= thinkTime) {
            startTime = 0;
            int index = rand.nextInt(letters.length());
            return letters.charAt(index);
        }

        return '\0';
    }

    @Override
    public void setTurn(boolean turnState) {
        if (turnState)
            startTime = System.currentTimeMillis();
        else
            startTime = 0;
    }

    @Override
    public boolean isThinking() {
        return (startTime != 0 && System.currentTimeMillis() - startTime < thinkTime);
    }

    @Override
    public void think(String word) {

    }

    @Override
    public void think(char letter) {

    }
}
