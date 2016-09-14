package ace.fixit;

import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import com.google.firebase.database.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class Authentication extends AppCompatActivity {
	private static final String TAG = Sites.TAG;
	private EditText password;

	static FirebaseDatabase database = FirebaseDatabase.getInstance();
	static DatabaseReference databaseRef = database.getReference("Auth");
	private Collection<Object> auths;
	private Set<String> keys;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_authentication);

		password = (EditText) findViewById(R.id.password);

		databaseRef.addValueEventListener(new ValueEventListener() {
			@Override
			public void onDataChange(DataSnapshot dataSnapshot) {
				Map<String, Object> pl = (HashMap<String, Object>) dataSnapshot.getValue();
				if(pl != null) {
					auths = pl.values();
					keys = pl.keySet();
				} else {
					Log.i(TAG, "No auths?");
				}
			}

			@Override
			public void onCancelled(DatabaseError databaseError) {
				Log.i(TAG, "Database Error: " + databaseError.getCode());
			}
		});

		password.setOnEditorActionListener(
				new TextView.OnEditorActionListener() {
					@Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
						if ((event != null && (event.getKeyCode() == KeyEvent.KEYCODE_ENTER)) || (actionId == EditorInfo.IME_ACTION_DONE)) {
							String name = "";
							boolean valid = false;
							int i = 0;
							for(Object auth : auths) {
								if(password.getText().toString().equals(auth.toString())) {
									valid = true;
									name = keys.toArray()[i].toString();
									break;
								}
								i++;
							}
							if(valid) {
								Log.i(TAG, "1");
								AlertDialog dialog = new AlertDialog.Builder(Authentication.this)
										.setTitle("Authenticated!")
										.setMessage("You have been authenticated as a " + name)
										.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
											@Override
											public void onClick(DialogInterface dialogInterface, int i) {
												SharedPreferences.Editor editor = getSharedPreferences("a", MODE_PRIVATE).edit();
												editor.putBoolean("b", true);
												editor.apply();
												((App) getApplication()).setAuthentication(true);
												dialogInterface.dismiss();
												finish();
											}
										})
										.setIcon(R.drawable.ic_done).create();
							} else {
								SharedPreferences.Editor editor = getSharedPreferences("a", MODE_PRIVATE).edit();
								editor.putBoolean("b", false);
								editor.apply();
								((App) getApplication()).setAuthentication(false);
								finish();
							}
						}
						return false;
					}
				});
	}
}
