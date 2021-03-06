package ch.fhnw.ip5.emotionhunt.models;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.media.RingtoneManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import java.util.ArrayList;
import java.util.List;

import ch.fhnw.ip5.emotionhunt.R;
import ch.fhnw.ip5.emotionhunt.activities.ExperienceListActivity;
import ch.fhnw.ip5.emotionhunt.activities.MainActivity;
import ch.fhnw.ip5.emotionhunt.helpers.DbHelper;
import ch.fhnw.ip5.emotionhunt.helpers.DeviceHelper;
import ch.fhnw.ip5.emotionhunt.helpers.Params;
import ch.fhnw.ip5.emotionhunt.tasks.RestExperienceListTask;
import ch.fhnw.ip5.emotionhunt.tasks.RestTask;

/**
 * EmotionHunt ch.fhnw.ip5.emotionhunt.models
 *
 * @author Benjamin Bur
 */
public class ReceivedExperience extends Experience {
    private static final String TAG = ReceivedExperience.class.getSimpleName();
    private static final int CATCHABLE_WITHIN_METERS = 50;

    /**
     * Returns a ReceivedExperience instance from the experience sql lite database.
     * @param context
     * @param id PK of the required experience entry
     * @return ReceivedExperience or null if no matching experience was found
     */
    public static ReceivedExperience findById(Context context, long id) {
        SQLiteDatabase db = new DbHelper(context).getWritableDatabase();
        Cursor c = db.rawQuery("SELECT * FROM " + ExperienceDbContract.TABLE_NAME + " WHERE " +
                ExperienceDbContract.COL_ID + "=" + id, null);
        if(c.moveToFirst()){
            do{
                ReceivedExperience experience = new ReceivedExperience();
                experience = (ReceivedExperience) loadFromCursor(c, experience);
                c.close();
                db.close();
                return experience;
            }while(c.moveToNext());
        }
        c.close();
        db.close();
        return null;
    }

    public boolean saveDb(Context context) {
        //check if this experience is an already stored experience
        if (ReceivedExperience.findById(context, this.id) != null) {
            Log.d(TAG, "received experience with id " + id + " already stored into sql db.");
            return false;
        }

        Log.d(TAG, "saveDb");
        SQLiteDatabase db = new DbHelper(context).getWritableDatabase();
        ContentValues contentValues = new ContentValues();
        contentValues.put(ExperienceDbContract.COL_ID, id);
        contentValues.put(ExperienceDbContract.COL_LAT, lat);
        contentValues.put(ExperienceDbContract.COL_LON, lon);
        contentValues.put(ExperienceDbContract.COL_IS_PUBLIC, isPublic);
        contentValues.put(Experience.ExperienceDbContract.COL_IS_LOCATION_BASED, isLocationBased ? 1 : 0);
        contentValues.put(ExperienceDbContract.COL_IS_SENT, 0);
        contentValues.put(ExperienceDbContract.COL_IS_READ, isRead ? 1 : 0);
        contentValues.put(ExperienceDbContract.COL_EMOTION, "");
        contentValues.put(ExperienceDbContract.COL_TEXT, text);
        contentValues.put(ExperienceDbContract.COL_FILENAME, filename);
        contentValues.put(ExperienceDbContract.COL_CREATED_AT, createdAt);
        contentValues.put(ExperienceDbContract.COL_VISIBILITY_DURATION, visibilityDuration);
        contentValues.put(ExperienceDbContract.COL_SENDER_ID, senderId);
        contentValues.put(ExperienceDbContract.COL_SENDER_NAME, senderName);

        boolean validation = db.insert(ExperienceDbContract.TABLE_NAME, null, contentValues) != -1;
        db.close();

        //show notification if this is a new private experience
        //which is successfully stored into mysqlite db
        if (!isPublic && validation) this.showNotification(context);

        return validation;
    }

    /**
     * Shows a push notification because of this new received experience.
     * @param context
     * @throws IllegalStateException in case of public experience
     */
    public void showNotification(Context context) {
        //assert only private experiences
        if (this.isPublic) throw new IllegalStateException();

        NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setVibrate(new long[] { 1000, 1000, 1000, 1000, 1000 })
                .setContentTitle(context.getString(R.string.new_experience_available))
                .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
                .setContentText(this.senderName + " " + context.getString(R.string.has_left_something_for_you));

        //creates an explicit intent for an Activity in your app
        Intent resultIntent = new Intent(context, (this.isLocationBased ? MainActivity.class : ExperienceListActivity.class));

        TaskStackBuilder stackBuilder = TaskStackBuilder.create(context);
        //adds the Intent that starts the Activity to the top of the stack
        stackBuilder.addNextIntent(resultIntent);

        PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,PendingIntent.FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(resultPendingIntent);
        NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        //mId allows you to update the notification later on.
        mNotificationManager.notify((int) this.id, mBuilder.build());
    }

    /**
     * Calls the RestTask to load experiences into local msqlite db.
     * @param context
     */
    public static void loadExperiencesFromApi(Context context) {
        String url = Params.getApiActionUrl(context, "experience.get");

        List<NameValuePair> nameValuePairs = new ArrayList<>();
        LocationHistory lh = LocationHistory.getLastPositionHistory(context,"");
        if (lh == null) return;
        String lat = String.valueOf(lh.lat);
        String lon = String.valueOf(lh.lon);
        nameValuePairs.add(new BasicNameValuePair("lat", lat));
        nameValuePairs.add(new BasicNameValuePair("lon", lon));
        nameValuePairs.add(new BasicNameValuePair("imei", DeviceHelper.getDeviceId(context)));

        RestTask task = new RestExperienceListTask(context, url, nameValuePairs);
        task.execute();
    }

    /**
     * Returns all read received experiences.
     * @param context
     * @param isRead
     * @return
     */
    public static ArrayList<ReceivedExperience> getAllRead(Context context, boolean isRead){
        return Experience.getAll(context, false, isRead);
    }

    /**
     * Returns whether an experience is ready to be opened or not.
     * That depends on the "isRead" and "isSent" and on the distance.
     * @return whether this experience is catchable or not
     */
    public boolean isCatchable(Context context) {
        //validate distance from the experience and the current location
        LocationHistory currentLocation = LocationHistory.getLastPositionHistory(context,"fused");
        return currentLocation != null && currentLocation.getLocation().distanceTo(getLocation()) < CATCHABLE_WITHIN_METERS;
    }

    /**
     * Returns the marker icon
     * @return
     */
    public BitmapDescriptor getMarkerIcon(Context context) {
        if(this.isRead()){
            return BitmapDescriptorFactory.fromResource(R.drawable.img_location);
        }else if(this.isCatchable(context)){
            return BitmapDescriptorFactory.fromResource(R.drawable.img_location_checked);
        } else if (!this.isRead() && !this.isCatchable(context)){
            return BitmapDescriptorFactory.fromResource(R.drawable.img_location_cross);
        } else{
            return BitmapDescriptorFactory.fromResource(R.drawable.img_location);
        }
    }
}
