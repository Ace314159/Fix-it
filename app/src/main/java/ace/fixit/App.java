package ace.fixit;

import android.app.Application;
import android.content.SharedPreferences;

public class App extends Application {
	private int authenticated;
	private final String TAG = "Fix-it!";

	public int isAuthenticated() {
		return authenticated;
	}

	public void setAuthentication  (int a) {
		authenticated = a;
	}

	public String getTag() {
		return TAG;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		SharedPreferences preferences = getSharedPreferences("a", MODE_PRIVATE);
		int b = preferences.getInt("b", 0);
		setAuthentication(b);
	}
}
