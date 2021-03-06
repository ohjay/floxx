package co.floxx.floxx;

import android.app.ProgressDialog;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;
import android.widget.Toast;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.Query;
import com.firebase.client.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;

/**
 * Official activity for the Requests page, which allows people to search for friends.
 * Queries are username-based (note that usernames are necessarily distinct between users!).
 * @author owenjow
 */
public class RequestsActivity extends AppCompatActivity {
    private static final int DELAY = 500; // 500 ms
    private int numFriendsAdded;
    private Firebase ref;
    private Context context;

    static TreeMap<String, String> allUsers = new TreeMap<String, String>();
    private ListView listView; // where we'll put the search output
    private int progressIndex = -1, numFriends;
    private ProgressDialog dialog;
    private static final int PROGRESS_DELAY = 1000; // this is in ms!
    private HashSet<String> currentFriends;
    private String currentUsername, currUser; // user = UID
    private HashSet<String> pending = new HashSet<String>(); // YOU sent these and you're waiting
    private TextView searchText;
    private float initialY;

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_requests);

        Typeface montserrat = Typeface.createFromAsset(getAssets(), "Montserrat-Regular.otf");
        ((TextView) findViewById(R.id.reqs_header)).setTypeface(montserrat);
        ((TextView) findViewById(R.id.curr_friends_header)).setTypeface(montserrat);
        ((TextView) findViewById(R.id.received_header)).setTypeface(montserrat);
        ((TextView) findViewById(R.id.search_header)).setTypeface(montserrat);

        if (FriendListActivity.names.isEmpty()) {
            FriendListActivity.initializeNames();
        }

        // Search view stuff
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        final SearchView searchView = (SearchView) findViewById(R.id.user_search_bar);

        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setIconifiedByDefault(true);

        int id = searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        searchText = (TextView) searchView.findViewById(id);
        searchText.setTextColor(Color.LTGRAY);
        searchText.setHintTextColor(Color.LTGRAY);

        initialY = searchText.getY() - 4;
        searchText.setY(initialY);

        id = searchView.getContext().getResources().getIdentifier("android:id/search_close_btn", null, null);
        ImageView closeButton = (ImageView) searchView.findViewById(id);
        closeButton.setY(closeButton.getY() + 6); // obviously, this is an approximation

        // Firebase configuration
        Firebase.setAndroidContext(this);
        ref = new Firebase("https://floxx.firebaseio.com/");
        final String currentUser = ref.getAuth().getUid();

        // Get pending friend reqs
        ref.child(currentUser).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<String> sent = (ArrayList<String>) dataSnapshot.getValue();
                if (sent != null) {
                    pending = new HashSet<String>(sent);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.w("RA – onCreate", "Read failed: " + firebaseError.getMessage());
            }
        });

        // Populate the "received requests" area
        context = this;
        final LinearLayout layout = (LinearLayout) findViewById(R.id.received_container);

        Query queryRef = ref.child("users").orderByKey().equalTo(currentUser);
        queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<String> requests;
                Object result = dataSnapshot.child(currentUser).child("requests").getValue();
                requests = (result == null) ? new ArrayList<String>() : (ArrayList<String>) result;

                if (requests.isEmpty()) {
                    TextView notApplicableText = new TextView(context);
                    notApplicableText.setTextSize(19);
                    notApplicableText.setTypeface(Typeface.SANS_SERIF);
                    notApplicableText.setTextColor(Color.GRAY);
                    notApplicableText.setText("N/A");

                    layout.addView(notApplicableText, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));

                    return;
                }

                for (final String senderID : requests) {
                    // Setting up the view for the name text
                    final TextView senderName = new TextView(context);
                    senderName.setTextSize(19);
                    senderName.setTypeface(Typeface.SANS_SERIF);
                    senderName.setTextColor(Color.LTGRAY);

                    final ImageButton declineButton = new ImageButton(context);
                    final ImageButton acceptButton = new ImageButton(context);

                    // Grab the sender's actual username
                    Query nameQRef = ref.child("uids").orderByValue().equalTo(senderID);
                    nameQRef.addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(DataSnapshot dataSnapshot) {
                            for (DataSnapshot child : dataSnapshot.getChildren()) {
                                senderName.setText(child.getKey());
                            }
                        }

                        @Override
                        public void onCancelled(FirebaseError firebaseError) {
                            Log.w("RA – onCreate", "Read failed: " + firebaseError.getMessage());
                        }
                    });

                    // Setting up the accept button
                    acceptButton.setImageResource(R.drawable.ic_check_circle_white_24dp);
                    acceptButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Accept the request! Yay!
                            acceptRequest(senderID, currentUser);

                            senderName.setTextColor(Color.DKGRAY);
                            acceptButton.setVisibility(View.INVISIBLE);
                            declineButton.setVisibility(View.INVISIBLE);
                        }
                    });

                    // Setting up the decline button
                    declineButton.setImageResource(R.drawable.ic_block_white_24dp);
                    declineButton.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            // Decline the sender's request
                            // Remove it from the registry, for starters
                            declineRequest(senderID, currentUser);

                            senderName.setTextColor(Color.DKGRAY);
                            acceptButton.setVisibility(View.INVISIBLE);
                            declineButton.setVisibility(View.INVISIBLE);
                        }
                    });

                    // Add the name and buttons to the layout
                    LinearLayout littleLayout = new LinearLayout(context);
                    littleLayout.setOrientation(LinearLayout.HORIZONTAL);

                    littleLayout.addView(senderName,
                            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.MATCH_PARENT));
                    littleLayout.addView(new View(context), new LinearLayout.LayoutParams(0, 0, 1));
                    littleLayout.addView(acceptButton,
                            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));
                    littleLayout.addView(declineButton,
                            new LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT));

                    senderName.setGravity(Gravity.CENTER_VERTICAL); // it looks weird otherwise

                    layout.addView(littleLayout, new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.w("RA – onCreate", "Read failed: " + firebaseError.getMessage());
            }
        });

        configureSearch(); // and also fill in the current friend list
    }

    private void configureSearch() {
        listView = (ListView) findViewById(R.id.search_list);

        final Firebase ref = new Firebase("https://floxx.firebaseio.com/");
        currUser = ref.getAuth().getUid();

        // Launching the progress dialog
        dialog = new ProgressDialog(this);
        dialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        dialog.setMessage("Retrieving user data...");
        dialog.show();

        // Initialization for progress variables
        progressIndex = 0;
        numFriends = 0;

        // Download all usernames and UIDs (uname -> uid)
        if (allUsers.isEmpty()) {
            Query queryRef = ref.child("users").orderByKey().equalTo(FirebaseActivity.OSKI_UID);
            queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    Object result = dataSnapshot.child(FirebaseActivity.OSKI_UID)
                            .child("friends").getValue();
                    if (result != null) {
                        ArrayList<String> friends = (ArrayList<String>) result;
                        numFriends = friends.size();

                        for (final String fuid : friends) {
                            Query nameQRef = ref.child("uids").orderByValue().equalTo(fuid);
                            nameQRef.addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(DataSnapshot dataSnapshot) {
                                    for (DataSnapshot child : dataSnapshot.getChildren()) {
                                        final String name = child.getKey();
                                        allUsers.put(name, fuid);
                                    }

                                    ++progressIndex;
                                    dialog.setProgress(progressIndex * 100 / numFriends);
                                }

                                @Override
                                public void onCancelled(FirebaseError firebaseError) {
                                    Log.w("cS0", "Read failed: " + firebaseError.getMessage());
                                }
                            });
                        }
                    }
                }

                @Override
                public void onCancelled(FirebaseError firebaseError) {
                    dialog.dismiss();
                }
            });
        }

        numFriends++; // to guarantee friend list loading... watch this

        // Grab all of the user's current friends
        currentFriends = new HashSet<String>(); // clear the set to start with
        Query queryRef = ref.child("users").orderByKey().equalTo(currUser);
        queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                Object result = dataSnapshot.child(currUser).child("friends").getValue();
                if (result != null) {
                    ArrayList<String> friends = (ArrayList<String>) result;
                    numFriends += friends.size() - 1;

                    for (final String fuid : friends) {
                        Query nameQRef = ref.child("uids").orderByValue().equalTo(fuid);
                        nameQRef.addListenerForSingleValueEvent(new ValueEventListener() {
                            @Override
                            public void onDataChange(DataSnapshot dataSnapshot) {
                                for (DataSnapshot child : dataSnapshot.getChildren()) {
                                    currentFriends.add(child.getKey()); // add the friend's name
                                }

                                if (dialog != null) {
                                    ++progressIndex;
                                    dialog.setProgress(progressIndex * 100 / numFriends);
                                }
                            }

                            @Override
                            public void onCancelled(FirebaseError firebaseError) {
                                Log.w("cS1", "Read failed: " + firebaseError.getMessage());
                            }
                        });
                    }
                } else {
                    numFriends--;
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) { dialog.dismiss(); }
        });

        runProgressHandler();

        final SearchView searchView = (SearchView) findViewById(R.id.user_search_bar);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                executeSearch(query);
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                searchText.setY((newText.length() == 0) ? initialY : initialY + 5);

                executeSearch(newText);
                return true;
            }
        });

        currentUsername = FriendListActivity.names.get(currUser);
    }

    private void acceptRequest(String senderID, String recipientID) {
        numFriendsAdded = 0; // so we know when the request has been entirely fulfilled

        addFriend(senderID, recipientID);
        addFriend(recipientID, senderID);
        delayedDestroyRequest(senderID, recipientID);

        String confirmation = FriendListActivity.names.get(senderID) + " is now your friend! "
                + "Your friend list will be updated the next time you load this page.";
        Toast.makeText(RequestsActivity.this, confirmation, Toast.LENGTH_LONG).show();
    }

    private void delayedDestroyRequest(final String senderID, final String recipientID) {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (numFriendsAdded >= 2) {
                    // The request has been fully accepted, so we don't need it anymore
                    destroyRequest(senderID, recipientID);
                } else {
                    delayedDestroyRequest(senderID, recipientID);
                }
            }
        }, DELAY);
    }

    /**
     * Adds NEW_FRIEND to UID's friend list.
     * @param uid the ID of the person who's having a friend added
     * @param newFriend this is also a UID
     */
    private void addFriend(final String uid, final String newFriend) {
        Query queryRef = ref.child("users").orderByKey().equalTo(uid);
        queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<String> friends, requests;

                Object result = dataSnapshot.child(uid).child("friends").getValue();
                friends = (result == null) ? new ArrayList<String>() : (ArrayList<String>) result;
                result = dataSnapshot.child(uid).child("requests").getValue();
                requests = (result == null) ? new ArrayList<String>() : (ArrayList<String>) result;

                if (!friends.contains(newFriend)) {
                    friends.add(newFriend);
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put("friends", friends);
                    map.put("requests", requests);

                    ref.child("users").child(uid).setValue(map); // our job here is done :)
                }

                ++numFriendsAdded;
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.w("RA – addFriend", "Read failed: " + firebaseError.getMessage());
            }
        });
    }

    private void declineRequest(String senderID, String recipientID) {
        destroyRequest(senderID, recipientID);
        String confirmation = FriendListActivity.names.get(senderID) + "'s request has been declined.";
        Toast.makeText(RequestsActivity.this, confirmation, Toast.LENGTH_LONG).show();
    }

    private void destroyRequest(final String senderID, final String recipientID) {
        Query queryRef = ref.child("users").orderByKey().equalTo(recipientID);
        queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<String> friends, requests;

                Object result = dataSnapshot.child(recipientID).child("friends").getValue();
                friends = (result == null) ? new ArrayList<String>() : (ArrayList<String>) result;
                result = dataSnapshot.child(recipientID).child("requests").getValue();
                requests = (result == null) ? new ArrayList<String>() : (ArrayList<String>) result;

                int senderIndex = requests.indexOf(senderID);
                if (senderIndex >= 0) {
                    requests.remove(senderIndex);

                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put("friends", friends);
                    map.put("requests", requests);
                    ref.child("users").child(recipientID).setValue(map);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.w("RA – destroyRequest", "Read failed: " + firebaseError.getMessage());
            }
        });

        // Remove the request from the sender's pending list
        ref.child(senderID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<String> sent = (ArrayList<String>) dataSnapshot.getValue();
                if (sent != null) {
                    sent.remove(FriendListActivity.names.get(recipientID));
                    ref.child(senderID).setValue(sent);
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.w("RA – destroyReq", "Read failed: " + firebaseError.getMessage());
            }
        });
    }

    private void runProgressHandler() {
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if (progressIndex >= numFriends) {
                    populateFriendList();
                    dialog.dismiss();
                    handleIntent(getIntent());
                } else {
                    runProgressHandler();
                }
            }
        }, PROGRESS_DELAY);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        handleIntent(intent);
    }

    /**
     * Gets the intent, verifies the action, and retrieves the query.
     * @param intent search intent that contains the query
     */
    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            executeSearch(query);
        }
    }

    private void populateFriendList() {
        // Populate the current friends area
        final LinearLayout cfLayout = (LinearLayout) findViewById(R.id.curr_friends_container);
        TextView[] firstFriends = new TextView[] { null, null };
        for (final String friend : currentFriends) {
            final TextView friendText = new TextView(context);
            friendText.setTextSize(19);
            friendText.setTypeface(Typeface.SANS_SERIF);
            friendText.setText(friend);
            friendText.setTextColor(Color.LTGRAY);

            if (firstFriends[1] == null) {
                firstFriends[1] = friendText;
            } else if (firstFriends[0] == null) {
                firstFriends[0] = friendText;
            } else {
                // Add the name to the layout
                cfLayout.addView(friendText, new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT));
            }
        }

        // Just to shuffle the list a little
        for (int i = 1; i >= 0; --i) {
            TextView firstFriend = firstFriends[i];
            if (firstFriend == null) {
                break;
            }

            cfLayout.addView(firstFriend, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        }
    }

    /**
     * Searches for a user with a name matching QUERY, then displays the results.
     * @param query the search query (some username, presumably)
     */
    private void executeSearch(String query) {
        ArrayList<String> results = new ArrayList<String>();
        if (currentUsername == null) {
            currentUsername = FriendListActivity.names.get(currUser);
        }

        if (!query.isEmpty()) { // alternatively, maybe > 2 chars or so?
            int i = 0;
            for (String username : allUsers.keySet()) {
                if (username.equals(currentUsername) || currentFriends.contains(username)) {
                    continue; // we don't want to be able to add our curr. friends... or ourselves
                } else if (Pattern.compile(Pattern.quote(query),
                        Pattern.CASE_INSENSITIVE).matcher(username).find()) {
                    results.add(username);

                    if (i >= 4) {
                        break;
                    } else {
                        ++i;
                    }
                }
            }
        }

        UsersAdapter adapter = new UsersAdapter(this, results);
        listView.setAdapter(adapter);
    }

    class UsersAdapter extends BaseAdapter {
        private Context context;
        private String[] results;

        public UsersAdapter(Context context, ArrayList<String> results) {
            this.context = context;
            this.results = results.toArray(new String[results.size()]);
        }

        public int getCount() {
            return results.length;
        }

        public Object getItem(int position) {
            return results[position];
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            EntryView entryView;

            if (convertView == null) {
                entryView = new EntryView(context, results[position]);
            } else {
                entryView = (EntryView) convertView;
                String username = results[position];
                entryView.setTextContent(username);
            }

            return entryView;
        }
    }

    void sendFriendRequest(final String username) {
        Firebase.setAndroidContext(this);
        final Firebase ref = new Firebase("https://floxx.firebaseio.com/");
        final String senderID = ref.getAuth().getUid(); // current user
        final String recipientID = allUsers.get(username);

        pending.add(username);

        ref.child(senderID).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<String> sent = (ArrayList<String>) dataSnapshot.getValue();
                if (sent == null) {
                    sent = new ArrayList<String>();
                    sent.add(username);
                } else if (!sent.contains(username)) {
                    sent.add(username);
                }

                ref.child(senderID).setValue(sent);
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.w("RA – sendFReq", "Read failed: " + firebaseError.getMessage());
            }
        });

        // Save sender UID under /users/<recipient UID>/requests
        // For the future: it'd be nice to store the date as well
        Query queryRef = ref.child("users").orderByKey().equalTo(recipientID);
        queryRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                ArrayList<String> friends, requests;

                // First, we'll grab the user's current friend/request lists
                // Note (- Owen 6/16): we grab the friend list so that it doesn't get erased
                Object result = dataSnapshot.child(recipientID).child("friends").getValue();
                friends = (result == null) ? new ArrayList<String>() : (ArrayList<String>) result;
                result = dataSnapshot.child(recipientID).child("requests").getValue();
                requests = (result == null) ? new ArrayList<String>() : (ArrayList<String>) result;

                if (!requests.contains(senderID)) { // no duplicates, man
                    requests.add(senderID);
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put("friends", friends);
                    map.put("requests", requests);
                    ref.child("users").child(recipientID).setValue(map);

                    String confirmation = "Your invitation has been sent!";
                    Toast.makeText(RequestsActivity.this, confirmation, Toast.LENGTH_LONG).show();
                } else {
                    String confirmation = "You've already sent " + username + " an invitation!";
                    Toast.makeText(RequestsActivity.this, confirmation, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onCancelled(FirebaseError firebaseError) {
                Log.w("RA – sendFReq", "Read failed: " + firebaseError.getMessage());
            }
        });
    }

    class EntryView extends LinearLayout {
        private TextView nameView;
        private ImageButton requestButton;

        public EntryView(Context context, final String username) {
            super(context);
            this.setOrientation(HORIZONTAL);

            // Creating the nameView (which should contain a username)
            nameView = new TextView(context);
            nameView.setText(username);
            nameView.setTextSize(19);
            nameView.setTypeface(Typeface.SANS_SERIF);

            // Creating the image button (the ADD symbol)
            requestButton = new ImageButton(context);
            requestButton.setImageResource(R.drawable.ic_add_circle_white_24dp);

            requestButton.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    // Send a connection request
                    sendFriendRequest(username);
                    nameView.setTextColor(Color.DKGRAY);
                    requestButton.setVisibility(View.INVISIBLE);
                }
            });

            addView(nameView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
            addView(new View(context), new LayoutParams(0, 0, 1));
            addView(requestButton, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

            nameView.setGravity(Gravity.CENTER_VERTICAL);

            if (pending.contains(username)) {
                nameView.setTextColor(Color.DKGRAY);
                requestButton.setVisibility(View.INVISIBLE);
            } else {
                nameView.setTextColor(Color.LTGRAY);
            }
        }

        public void setTextContent(String content) {
            nameView.setText(content);
        }
    }
}
