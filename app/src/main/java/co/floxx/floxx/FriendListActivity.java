package co.floxx.floxx;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.firebase.client.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;


public class FriendListActivity extends AppCompatActivity {
    FriendListActivity thisList;
    private HashSet<String> selected = new HashSet<String>();
    public static HashMap<String, String> names = new HashMap<String, String>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        thisList = this;
        setContentView(R.layout.activity_activity_friend_list);
        Button button = (Button) findViewById(R.id.button);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(FriendListActivity.this, FullscreenActivity.class));
            }
        });

        ImageButton button1 = (ImageButton) findViewById(R.id.setting_logo);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startActivity(new Intent(FriendListActivity.this, SettingActivity.class));
            }
        });

        // TODO: find some way to clear the SELECTED set every time the view loads

        Firebase.setAndroidContext(this);
        final Firebase ref = new Firebase("https://floxx.firebaseio.com/");
        final String uid = ref.getAuth().getUid().toString();
        Query queryRef = ref.child("users").orderByKey().equalTo(uid);
        queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object result = dataSnapshot.child(uid).child("friends").getValue();
                if (result != null) {
                    ArrayList<String> friends = (ArrayList<String>) result;

                    for (final String fuid : friends) {
                        final Button b = new Button(thisList);
                        Query nameQRef = ref.child("uids").orderByValue().equalTo(fuid);
                        nameQRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                for (DataSnapshot child : dataSnapshot.getChildren()) {
                                    final String name = child.getKey();
                                    names.put(fuid, name);
                                    b.setText(name);

                                    LinearLayout ll = (LinearLayout) findViewById(R.id.button_container);
                                    LayoutParams lp = new LayoutParams(LayoutParams.MATCH_PARENT,
                                            LayoutParams.WRAP_CONTENT);
                                    ll.addView(b, lp);

                                    b.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View view) {
                                            if (selected.contains(fuid)) {
                                                selected.remove(fuid);
                                                b.getBackground().clearColorFilter();
                                            } else {
                                                selected.add(fuid);
                                                b.getBackground().setColorFilter(Color.parseColor("#ffde00"),
                                                        PorterDuff.Mode.DARKEN);
                                            }
                                        }
                                    });
                                }
                            }

                            @Override
                            public void onCancelled(FirebaseError firebaseError) {}
                        });
                    }
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {}
        });

        Button mapButton = (Button) findViewById(R.id.mapgo);
        mapButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View view) {
                switchToMap(view);
            }
        });
    }

    public void switchToMap(View view) {
        Intent intent = new Intent(FriendListActivity.this, MapActivity.class);
        for (String ouid : selected) {
            intent.putExtra(ouid, 0);
        }
        FriendListActivity.this.startActivity(intent);
    }
}