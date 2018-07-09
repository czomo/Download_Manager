package com.example.student.sieciii;

import android.app.IntentService;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.v4.app.TaskStackBuilder;
import android.util.Log;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.example.student.sieciii.MainActivity.TYP_PLIKU;

/**
 * Created by student on 22.05.18.
 */


public class PobieranieService extends IntentService {
    public final static String POWIADOMIENIE = "com.example.intent_service.odbiornik";
    public final static String POSTEP_INFO = "info o postepie";
    public final static String ADRES = "adres pliku";
    public final static int ROZMIAR_BLOKU = 32 * 1024; // 32kB
    public PostepInfo progressInfo = new PostepInfo();
    int mPoprzednieProcenty;
    String mAdres;
    NotificationManager pushManager;
    int mIdPush;
    Notification.Builder pushBuilder;
    Intent mZamiarPowiadomienia;
    private String mTypPliku;

    public PobieranieService() {
        super("usługa pobierania plików");
    }

    int procenty() {//procenty paska pobierania -zołty
        return (int) ((double) progressInfo.mPobranychBajtow
                / (double) progressInfo.mRozmiar * 100);
    }

    void wyslijWiadomosc() {//pasek pobierania zolty
        int noweProcenty = procenty();
        if (noweProcenty != mPoprzednieProcenty
                || progressInfo.mWynik == PostepInfo.POBIERANIE_BLAD
                || progressInfo.mWynik == PostepInfo.POBIERANIE_OK) {
            Intent zamiar = new Intent(POWIADOMIENIE);
            zamiar.putExtra(POSTEP_INFO, progressInfo);
            sendBroadcast(zamiar);
            mPoprzednieProcenty = noweProcenty;
        }
    }

    @Override
    protected void onHandleIntent(Intent intent) {//budowa obsługi pobierania
        mAdres = intent.getStringExtra(ADRES);
        Log.d("PS", "adres: " + mAdres);
        HttpURLConnection polaczenie = null;
        InputStream strumienZSieci = null;
        FileOutputStream strumienDoPliku = null;
        mPoprzednieProcenty = 0;

        try {
            URL url = new URL(mAdres);
            File plikRoboczy = new File(url.getFile());
//            String baseDir = Environment.getExternalStoragePublicDirectory();
//            File plikWyjsciowy = new File(baseDir + File.separator+ plikRoboczy.getName());
            File plikWyjsciowy = new File(Environment.getExternalStoragePublicDirectory(//zapisywanie do katalogu
                    Environment.DIRECTORY_DOWNLOADS), plikRoboczy.getName());
            Log.e("PdgfdghS", plikWyjsciowy.getPath());
            if (!plikWyjsciowy.mkdirs()) {
                Log.e("XD", "Directory not created");
            }
            Log.d("PS", "plik: " + plikWyjsciowy);
            if (plikWyjsciowy.exists())
                plikWyjsciowy.delete();

            polaczenie = (HttpURLConnection) url.openConnection();
            polaczenie.setRequestMethod("GET");
            polaczenie.setDoOutput(true);
            Log.d("PT", "uruchomione");
            Log.d("PT", "URL " + url.toString());

            progressInfo.mPobranychBajtow = 0;
            progressInfo.mRozmiar = polaczenie.getContentLength();
            progressInfo.mWynik = PostepInfo.POBIERANIE_TRWA;
            przygotujPowiadomienie();
            mTypPliku = polaczenie.getContentType();
            DataInputStream czytnik = new DataInputStream(polaczenie.getInputStream());
            strumienDoPliku = new FileOutputStream(plikWyjsciowy.getPath());
            Log.d("PT", "załadowano");
            byte bufor[] = new byte[ROZMIAR_BLOKU];
            int pobrano = czytnik.read(bufor, 0, ROZMIAR_BLOKU);
            while (pobrano != -1) {
                strumienDoPliku.write(bufor, 0, pobrano);
                progressInfo.mPobranychBajtow += pobrano;
                pobrano = czytnik.read(bufor, 0, ROZMIAR_BLOKU);
                Log.d("PT", "trwa łączenie");
                wyslijWiadomosc();////
                aktualizujPowiadomienie();
            }
            progressInfo.mWynik = PostepInfo.POBIERANIE_OK;
        } catch (Exception e) {
            e.printStackTrace();
            progressInfo.mWynik = PostepInfo.POBIERANIE_BLAD;
            Toast.makeText(this,"ERROR strumienia brak pliku", Toast.LENGTH_LONG).show();

        } finally {
            if (strumienZSieci != null) {
                try {
                    strumienZSieci.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (strumienDoPliku != null) {
                try {
                    strumienDoPliku.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (polaczenie != null)
                polaczenie.disconnect();
            pushManager.cancel(mIdPush);
            Log.d("PS", "rozłączanie");
        }
    }

    void przygotujPowiadomienie() {//tworzenie paska powiadomienia
        mZamiarPowiadomienia = new Intent(this,
                MainActivity.class);
        mZamiarPowiadomienia.putExtra(TYP_PLIKU, mTypPliku);
//        mZamiarPowiadomienia.putExtras(ROZMIAR_PLIKU,ROZMIAR_PLIKU);
//        mZamiarPowiadomienia.putExtras(ADRES,MainActivity.);
        sendBroadcast(mZamiarPowiadomienia);
        TaskStackBuilder budowniczyStosu = TaskStackBuilder.create(this);
        budowniczyStosu.addParentStack(MainActivity.class);
        budowniczyStosu.addNextIntent(mZamiarPowiadomienia);
        PendingIntent zamiarOczekujacy = budowniczyStosu.getPendingIntent(0,
                        PendingIntent.FLAG_UPDATE_CURRENT);

        pushManager = (NotificationManager)
                        getSystemService(NOTIFICATION_SERVICE);
        mIdPush = 1;
        pushBuilder = new Notification.Builder(this);
        pushBuilder.setContentTitle(
                getString(R.string.pasekpobrano))
                .setProgress(1, 0, false)
                .setContentIntent(zamiarOczekujacy)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setOngoing(true);
        pushManager.notify(mIdPush,
                pushBuilder.build());
    }

    void aktualizujPowiadomienie() {//aktualizowanie paska powiadomien
        pushBuilder.setProgress(progressInfo.mRozmiar,
                progressInfo.mPobranychBajtow, false);
        if (progressInfo.mWynik == PostepInfo.POBIERANIE_OK || progressInfo.mWynik == PostepInfo.POBIERANIE_BLAD) {
            mZamiarPowiadomienia.putExtra(POSTEP_INFO, progressInfo);
            TaskStackBuilder budowniczyStosu = TaskStackBuilder.create(this);
            budowniczyStosu.addParentStack(MainActivity.class);
            budowniczyStosu.addNextIntent(mZamiarPowiadomienia);
            PendingIntent zamiarOczekujacy = budowniczyStosu.getPendingIntent(0,
                    PendingIntent.FLAG_UPDATE_CURRENT);
            pushBuilder.setOngoing(false).setAutoCancel(true).setContentIntent(zamiarOczekujacy);
        }
        pushManager.notify(mIdPush,
                pushBuilder.build());
    }

    public static class PostepInfo implements Parcelable {//klasa przechowujaca wartosci początkowe i konstruktory
        public static final int POBIERANIE_OK = 0;
        public static final int POBIERANIE_TRWA = 1;
        public static final int POBIERANIE_BLAD = 2;
        public static final Parcelable.Creator<PostepInfo>
                CREATOR = new Parcelable.Creator<PostepInfo>() {
            @Override
            public PostepInfo createFromParcel(Parcel source) {
                return new PostepInfo(source);
            }

            @Override
            public PostepInfo[] newArray(int size) {
                return new PostepInfo[size];
            }
        };
        public int mPobranychBajtow;
        public int mRozmiar;
        public int mWynik;

        public PostepInfo(Parcel dane) {

            mPobranychBajtow = dane.readInt();
            mRozmiar = dane.readInt();
            mWynik = dane.readInt();
        }

        public PostepInfo() {

        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mPobranychBajtow);
            dest.writeInt(mRozmiar);
            dest.writeInt(mWynik);

        }

    }


}