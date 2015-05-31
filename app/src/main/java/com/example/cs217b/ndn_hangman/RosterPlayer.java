package com.example.cs217b.ndn_hangman;

public class RosterPlayer {
    String playerName;
    long sessionNo;
    String randomString;
    int score;
    String inputWord;
    char inputLetter;
    boolean isLocal;

    public RosterPlayer(String playerName, long sessionNo, String randomString, boolean isLocal) {
        this.playerName = playerName;
        this.sessionNo = sessionNo;
        this.randomString = randomString;
        this.isLocal = isLocal;

        score = 0;
    }

    public String chooseWord() {
        return inputWord;
    }

    public char chooseLetter() {
        return inputLetter;
    }
}
