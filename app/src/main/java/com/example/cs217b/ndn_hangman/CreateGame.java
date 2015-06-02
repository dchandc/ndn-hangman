package com.example.cs217b.ndn_hangman;

import android.content.Intent;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import net.named_data.jndn.*;
import net.named_data.jndn.security.*;
import net.named_data.jndn.security.SecurityException;
import net.named_data.jndn.security.identity.*;
import net.named_data.jndn.security.policy.NoVerifyPolicyManager;
import net.named_data.jndn.transport.Transport;
import net.named_data.jndn.util.Blob;

import java.io.IOException;

public class CreateGame extends ActionBarActivity {
    public GameSync sync_;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_game);

        Button button_create = (Button) findViewById(R.id.createButton);
        button_create.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("clicks", "Create Game button clicked");
                EditText gameName = (EditText) findViewById(R.id.roomName);
                EditText playerName = (EditText) findViewById(R.id.playerName);

                Name hubPrefix = new Name("ndn/edu/ucla/cs/hangman");
                String gameName_ = gameName.getText().toString();
                String playerName_ = playerName.getText().toString();
                String host = "spurs.cs.ucla.edu";

                Face face = new Face(host);

                MemoryIdentityStorage identityStorage = new MemoryIdentityStorage();
                MemoryPrivateKeyStorage privateKeyStorage = new MemoryPrivateKeyStorage();
                KeyChain keyChain = new KeyChain
                        (new IdentityManager(identityStorage, privateKeyStorage),
                                new NoVerifyPolicyManager());
                keyChain.setFace(face);
                Name keyName = new Name("/testname/DSK-123");
                Name certificateName = keyName.getSubName(0, keyName.size() - 1).append
                        ("KEY").append(keyName.get(-1)).append("ID-CERT").append("0");
                try {
                    identityStorage.addKey(keyName, KeyType.RSA, new Blob(Keys.DEFAULT_RSA_PUBLIC_KEY_DER, false));
                    privateKeyStorage.setKeyPairForKeyName
                            (keyName, KeyType.RSA, Keys.DEFAULT_RSA_PUBLIC_KEY_DER, Keys.DEFAULT_RSA_PRIVATE_KEY_DER);
                } catch (SecurityException e) {
                    Log.i("exception: ", e.getLocalizedMessage());
                }
                face.setCommandSigningInfo(keyChain, certificateName);

                sync_ = new GameSync(playerName_,gameName_,hubPrefix,face,keyChain,certificateName);
                Intent intent = new Intent(CreateGame.this, NewGameActivity.class);
                startActivity(intent);
            }
        });
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
