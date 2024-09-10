package de.geoit.treescoutberlin;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class DataBaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "baumtest.db";
    private static final int DATABASE_VERSION = 1;
    private static String DATABASE_PATH;
    private final Context context;
    SQLiteDatabase db;

    public DataBaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context;
        DATABASE_PATH = context.getDatabasePath(DATABASE_NAME).getPath(); //<<<<<<<<<< Recommended way
        createDb();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
    }

    public void createDb() {
        boolean dbExist = checkDbExist();

        if (!dbExist) {
            //this.getReadableDatabase(); //<<<<<<<< will mess up Android 9 as it creates -wal and -shm files
            copyDatabase();
        }
    }

    private boolean checkDbExist() {

        /**
         * Checks the file instead of trying to open the database,
         * makes directories if needed (the get around to this was opening the database to create them)
         */
        File db = new File(DATABASE_PATH);
        if (!db.exists()) {
            if(!new File(db.getParent()).exists()) {
                new File(db.getParent()).mkdirs();
            }
            return false;
        } else {
            return true;
        }
    }

    private void copyDatabase() {
        try {
            InputStream inputStream = context.getAssets().open(DATABASE_NAME);
            String outFileName = DATABASE_PATH;
            OutputStream outputStream = new FileOutputStream(outFileName);

            byte[] b = new byte[1024];
            int length;
            while ((length = inputStream.read(b)) > 0) {
                outputStream.write(b, 0, length);
            }
            outputStream.flush();
            outputStream.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void close() {
        if (db != null) {
            db.close();
        }
    }

}
