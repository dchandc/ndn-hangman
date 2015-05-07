package com.example.cs217b.ndn_hangman;

import java.util.ArrayList;

/**
 * Created by Dennis on 5/2/2015.
 */
public class GameState {
    public ArrayList<Player> players;
    public int currentDrawerIndex;
    public int currentGuesserIndex;
    public int numberGuessedWrong;
    public String hangmanWord;
    public String statusUpdate;

    public GameState(ArrayList<Player> players, int currentDrawerIndex, int currentGuesserIndex,
                     int numberGuessedWrong, String hangmanWord, String statusUpdate) {
        this.players = players;
        this.currentDrawerIndex = currentDrawerIndex;
        this.currentGuesserIndex = currentGuesserIndex;
        this.numberGuessedWrong = numberGuessedWrong;
        this.hangmanWord = hangmanWord;
        this.statusUpdate = statusUpdate;
    }
}
