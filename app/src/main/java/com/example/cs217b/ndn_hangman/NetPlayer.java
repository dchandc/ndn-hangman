package com.example.cs217b.ndn_hangman;

/**
 * This class represents any online human player.
 */
public class NetPlayer extends Player{
    String storedWord;
    char storedLetter;
    boolean isThinking;

    public NetPlayer(String name, long sessionNo, String sid, boolean isLocal) {
        this.name = name;
        this.score = 0;
        this.isLocal = isLocal;
        this.sid = sid;
        this.sessionNo = sessionNo;

        storedWord = "";
        storedLetter = '\0';
        isThinking = false;
    }

    @Override
    public String inputWord() {
        if (!isThinking) {
            String temp = storedWord;
            storedWord = "";
            return temp;
        }

        return "";
    }

    @Override
    public char inputLetter(String ignored) {
        if (!isThinking) {
            char temp = storedLetter;
            storedLetter = '\0';
            return temp;
        }

        return '\0';
    }

    @Override
    public void setTurn(boolean turnState) {
        isThinking = turnState;
    }

    @Override
    public boolean isThinking() {
        return isThinking;
    }

    @Override
    public void think(String word) {
        storedWord = word;
        isThinking = false;
    }

    @Override
    public void think(char letter) {
        storedLetter = letter;
        isThinking = false;
    }
}
