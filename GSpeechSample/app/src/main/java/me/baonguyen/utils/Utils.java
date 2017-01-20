package me.baonguyen.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;
import java.util.TimeZone;

/**
 * Created by bao on 17/01/2017.
 */

public class Utils {

    public static String randomString() {
        Random generator = new Random();
        StringBuilder randomStringBuilder = new StringBuilder();
        int length = 8;
        char tempChar;
        for (int i = 0; i < length; i++){
            tempChar = (char) (generator.nextInt(24) + 65);
            randomStringBuilder.append(tempChar);
        }
        return randomStringBuilder.toString();
    }

    public static String getTimeFromTimeStamp(int timeStamp) {
        long mils = (long) timeStamp * 1000;
        Date date = new Date(mils);
        SimpleDateFormat format = new SimpleDateFormat("HH:mm", Locale.getDefault());
        format.setTimeZone(TimeZone.getDefault());
        return format.format(date);
    }

    public static String getDateFromTimeStamp(int timeStamp) {
        long mils = (long) timeStamp * 1000;
        Date date = new Date(mils);
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
        format.setTimeZone(TimeZone.getDefault());
        return format.format(date);
    }
}
