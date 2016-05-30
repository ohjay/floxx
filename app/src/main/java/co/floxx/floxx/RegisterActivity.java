package co.floxx.floxx;

import android.content.Intent;
import android.graphics.Typeface;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class RegisterActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        Button button = (Button) findViewById(R.id.register_button);
        final Bundle extras = new Bundle();

        Typeface montserrat = Typeface.createFromAsset(getAssets(), "Montserrat-Regular.otf");
        final EditText usernameText = (EditText) findViewById(R.id.register_user);
        final EditText emailText = (EditText) findViewById(R.id.register_email);
        final EditText passwordText = (EditText) findViewById(R.id.register_password);
        final EditText confirmText = (EditText) findViewById(R.id.confirm_password);

        usernameText.setTypeface(montserrat);
        emailText.setTypeface(montserrat);
        passwordText.setTypeface(montserrat);
        confirmText.setTypeface(montserrat);

        button.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(RegisterActivity.this, FirebaseActivity.class);
                String password = passwordText.getText().toString();
                String confirm = confirmText.getText().toString();

                if (!password.equals(confirm)) {
                    Toast.makeText(RegisterActivity.this, "Passwords did not match. Try again!",
                            Toast.LENGTH_LONG).show();
                    return;
                }

                String username = usernameText.getText().toString();
                String email = emailText.getText().toString();

                extras.putString("register_user", username);
                extras.putString("register_email", email);
                extras.putString("register_password", password);
                intent.putExtras(extras);
                RegisterActivity.this.startActivity(intent);
            }
        });
    }
}
