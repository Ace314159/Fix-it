package ace.fix_it;

import com.google.firebase.database.IgnoreExtraProperties;

@IgnoreExtraProperties
public class Problem {

    public String id;
    public double lat;
    public double llong;
    public int rating;

    public Problem() {

    }

    public Problem(double lat, double llong, String id, int rating) {
        this.lat = lat;
        this.llong = llong;
        this.id = id;
        this.rating = rating;
    }
}
