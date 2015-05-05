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
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return "abcdefghij";
    }

    @Override
    char chooseLetter(String letters) {
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        Random rand = new Random();
        int index = rand.nextInt(letters.length());
        return letters.charAt(index);
    }
}
