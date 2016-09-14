package ace.fixit;

import android.app.Application;
import android.content.SharedPreferences;

public class App extends Application {
	private boolean authenticated;

	public boolean isAuthenticated() {
		return authenticated;
	}

	public void setAuthentication  (boolean a) {
		authenticated = a;
	}

	@Override
	public void onCreate() {
		super.onCreate();
		SharedPreferences preferences = getSharedPreferences("a", MODE_PRIVATE);
		boolean b = preferences.getBoolean("b", false);
		if(b) {
			setAuthentication(true);
		} else {
			setAuthentication(false);
		}
	}
}
