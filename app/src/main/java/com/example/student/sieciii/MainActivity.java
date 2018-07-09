package com.example.student.sieciii;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.regex.Pattern;


public class MainActivity extends AppCompatActivity {

    public final static String POWIADOMIENIE = "com.example.intent_service.odbiornik";
    public final static String INFO = "info";
    public final static String ROZMIAR_PLIKU = "rozmiar pliku";
    public final static String TYP_PLIKU = "typ pliku";
    public final static String POBRANO_BAJTOW = "pobrano bajtow";
    public final static String PASEK_POSTEPU = "pasek postepu";
    private TextView rozmiar, typ,pobranoBajtowWartoscEtykieta;
    private  EditText adresEdittext;

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(pushReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(pushReceiver, new IntentFilter(
                PobieranieService.POWIADOMIENIE));
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button info = findViewById(R.id.info);
        final Button down = findViewById(R.id.down);
        rozmiar = (TextView) findViewById(R.id.rozmiar);
        typ = (TextView) findViewById(R.id.typ);
        pobranoBajtowWartoscEtykieta = (TextView) findViewById(R.id.pobrano);

        Bundle tobolek = getIntent().getExtras();//pasek powiadomiania pobierania
        if (tobolek != null) {
            PobieranieService.PostepInfo postepInfo =
                    tobolek.getParcelable(PobieranieService.POSTEP_INFO);
            if (tobolek.containsKey(
                    PobieranieService.POSTEP_INFO))
                aktualizujPostep((PobieranieService.PostepInfo) tobolek
                        .getParcelable(PobieranieService.POSTEP_INFO));
        }

        adresEdittext = (EditText) findViewById(R.id.adres);
        info.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // down.setVisibility(View.VISIBLE);
                if(sprawdzNapisy()){
                pobierzInfo();}
            }
        });
        down.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                if(sprawdzNapisy()){
                pobierzPlik();}
            }
        });
    }
    private boolean sprawdzNapisy() {//regex
        String sS = adresEdittext.getText().toString();
        Pattern pS = Pattern.compile("^(http:\\/\\/www\\.|https:\\/\\/www\\.|http:\\/\\/|https:\\/\\/)?[a-z0-9]+([\\-\\.]{1}[a-z0-9]+)*\\.[a-z]{2,5}(:[0-9]{1,5})?(\\/.*)?$");
        if(!Pattern.matches(pS.pattern(), sS)) {
            Toast.makeText(this,"Niepoprawny adres pliku", Toast.LENGTH_LONG).show();
            return false;
        }

        return true;
    }
    private BroadcastReceiver pushReceiver =
            new BroadcastReceiver() {//oczytywanie bunndla z klasy pobieranie
                @Override
                public void onReceive(Context context, Intent intent) {
                    Bundle tobolek = intent.getExtras();
                    PobieranieService.PostepInfo postepInfo =
                            tobolek.getParcelable(PobieranieService.POSTEP_INFO);
                    aktualizujPostep(postepInfo);
                }
            };

    protected void aktualizujPostep(PobieranieService.PostepInfo postepInfo) {//pasek postępu
        ProgressBar pasekPostepu = (ProgressBar) findViewById(R.id.determinateBar);
        pasekPostepu.setProgress((int) ((double) postepInfo.mPobranychBajtow
                / (double) postepInfo.mRozmiar * 100.0));
        pobranoBajtowWartoscEtykieta.setText(Integer.toString(postepInfo.mPobranychBajtow));
        if (postepInfo.mRozmiar == postepInfo.mPobranychBajtow) {
            Toast.makeText(this, "Pobrano plik!",
                    Toast.LENGTH_LONG).show();
        }
    }

    protected void pobierzPlik() {//funkcja pobierania
        pobierzInfo();
        EditText adresEdittext = (EditText) findViewById(R.id.adres);
        Intent zamiarPobierania = new Intent(this, PobieranieService.class);
        zamiarPobierania.putExtra(PobieranieService.ADRES, adresEdittext.getText().toString());
        startService(zamiarPobierania);
    }

    private void ustawInfoOPliku(InfoOPliku infoOPliku) {//Ustawianie wartości rozmiaru i typu
        if(infoOPliku.mRozmiar<500){
            Toast.makeText(this,"ERROR brak pliku", Toast.LENGTH_LONG).show();
        }else {        rozmiar.setText(Integer.toString(infoOPliku.mRozmiar));
            typ.setText(infoOPliku.mTyp);

        }
    }
    @Override//zapisywanie wartosci przy zmianie orientacji
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("ROZMIAR", rozmiar.getText().toString());
        outState.putString("TYP", typ.getText().toString());
        outState.putString("POBRANO_BAJTOW", pobranoBajtowWartoscEtykieta.getText().toString());
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        rozmiar.setText(savedInstanceState.getString("ROZMIAR"));
        typ.setText(savedInstanceState.getString("TYP"));
        pobranoBajtowWartoscEtykieta.setText(savedInstanceState.getString("POBRANO_BAJTOW"));
    }

    protected void pobierzInfo() {//funkcja pobierająca
        TextView adresEdittext = (TextView) findViewById(R.id.adres);
        PobierzInfoTask pobierzInfoTask = new PobierzInfoTask();
        pobierzInfoTask.execute(new String[]{adresEdittext.getText().toString()});
    }

    private class InfoOPliku {
        public int mRozmiar;
        public String mTyp;
    }

    private class PobierzInfoTask extends
            AsyncTask<String, Void, InfoOPliku> {//tworzenie asynchonicznego zadania
        @Override
        protected InfoOPliku doInBackground(String... params) {//tworzenie połączemnia i pozyskiwanie danych
            HttpURLConnection polaczenie = null;
            InfoOPliku infoOPliku = null;
            try {
                URL url = new URL(params[0]);
                polaczenie = (HttpURLConnection) url.openConnection();
                polaczenie.setRequestMethod("GET");
                polaczenie.setDoOutput(true);
                infoOPliku = new InfoOPliku();
                infoOPliku.mRozmiar = polaczenie.getContentLength();
                infoOPliku.mTyp = polaczenie.getContentType();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (polaczenie != null)
                    polaczenie.disconnect();
            }
            return infoOPliku;
        }

        @Override
        protected void onPostExecute(InfoOPliku result) {
            ustawInfoOPliku(result);
            super.onPostExecute(result);
        }
    }


}

