package com.example.cs217b.ndn_hangman;

import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import net.named_data.jndn.*;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.transport.Transport;

import java.io.IOException;

public class CreateGame extends ActionBarActivity {
    private Face createGameFace;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_game);

        createGameFace = new Face("spurs.cs.ucla.edu");
        /*
        try {
            createGameFace.setCommandSigningInfo(MainActivity.keychain, MainActivity.keychain.getDefaultCertificateName());
        }
        catch (SecurityException e) {
            Log.d("SecurityException", e.getLocalizedMessage());
        }
        */
        try {
            createGameFace.registerPrefix(new Name("/ndn/hangman/public/lobby/room1"),
                    new OnInterest() {
                        @Override
                        public void onInterest(Name prefix, Interest interest, Transport transport, long interestFilterId) {

                        }
                    },
                    new OnRegisterFailed() {
                        @Override
                        public void onRegisterFailed(Name prefix) {

                        }
                    }
            );
        } catch (Exception er) {
            Log.d("Exception", er.getLocalizedMessage());
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_create_game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
