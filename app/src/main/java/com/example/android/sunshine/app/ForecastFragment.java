package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ForecastFragment extends Fragment {

    private final String LOG_TAG = ForecastFragment.class.getSimpleName();

    private TextView tv_lastUpdate, tv_tempUnit;

    private ArrayAdapter<String> forecastAdapter;

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        tv_lastUpdate = (TextView) rootView.findViewById(R.id.tv_lastUpdate);
        tv_tempUnit = (TextView) rootView.findViewById(R.id.tv_tempUnit);

        forecastAdapter = new ArrayAdapter<>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forcast_textview, new ArrayList<String>());

        ListView listView = (ListView) rootView.findViewById(R.id.lv_forecast);
        listView.setAdapter(forecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                String forecast = forecastAdapter.getItem(position);

                Intent intent = new Intent(getActivity(), DetailActivity.class);
                intent.putExtra("" + IntentExtra.FORECAST_DATA, forecast);
                startActivity(intent);
            }
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeather();
    }

    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment,menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        Log.d(LOG_TAG, "onOptionsItemSelected()");
        Log.d(LOG_TAG, "item=" + getActivity().getResources().getResourceName(item.getItemId()));


        int id = item.getItemId();
        if (id == R.id.action_refresh) {

            updateWeather();

            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void updateWeather(){

        String numDays = "7";

        SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String postCode = defaultPrefs.getString(getString(R.string.pref_location_key), getString(R.string.pref_location_default));

        new FetchWeatherTask().execute(postCode, numDays);
    }

    public class FetchWeatherTask extends AsyncTask<String,Void,String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        private String buildURL(String... queryParams){

//            URL url = new URL("http://api.openweathermap.org/data/2.5/forecast/daily?q=94043&mode=json&units=metric&cnt=7");

            Uri.Builder builder = new Uri.Builder();
            builder.scheme("http")
                    .authority("api.openweathermap.org")
                    .appendPath("data")
                    .appendPath("2.5")
                    .appendPath("forecast")
                    .appendPath("daily");

            for(String param: queryParams){
                if(param!=null && param.contains("=")){
                    String paramKey = param.split("=")[0];
                    String paramValue = param.split("=")[1];
                    builder.appendQueryParameter(paramKey, paramValue);
                }
            }
            return builder.build().toString();
        }

        @Override
        protected void onPreExecute() {
            tv_lastUpdate.setText(getResources().getString(R.string.currentlyUpdating));
        }

        @Override
        protected String[] doInBackground(String... params) {

            String postcode = (params != null && params.length > 0 && params[0] != null) ? params[0] : "";
            String numDaysString = (params != null && params.length > 1 && params[1] != null) ? params[1] : "";

            int numDays;
            try{
                numDays = Integer.valueOf(numDaysString);
            }catch (Exception e){
                numDays = 1;
            }

            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are available at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
//                URL url = new URL("http://api.openweathermap.org/data/2.5/forecast/daily?q=94043&mode=json&units=metric&cnt=7");

                String[] urlParams = {"q="+postcode, "mode=json", "units=metric", "cnt="+numDays};
                URL url = new URL(buildURL(urlParams));
                Log.d(LOG_TAG, "url="+url);


                        // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    forecastJsonStr = null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    forecastJsonStr = null;
                }
                forecastJsonStr = buffer.toString();


            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attempting
                // to parse it.
                forecastJsonStr = null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e(LOG_TAG, "Error closing stream", e);
                    }
                }
            }

            String[] weatherData;
            try{
                weatherData = WeatherDataParser.getWeatherDataFromJson(forecastJsonStr, numDays);
            }catch (Exception e){
                weatherData = null;
            }

            return weatherData;
        }

        public void onPostExecute(String[] weatherData){

            if(weatherData!=null && weatherData.length>0){

                // avoid notify changes with every add()
                forecastAdapter.setNotifyOnChange(false);

                forecastAdapter.clear();
                for(String data: weatherData)
                    if(data!=null)
                        forecastAdapter.add(data);

                forecastAdapter.setNotifyOnChange(true);
                forecastAdapter.notifyDataSetChanged();

                tv_lastUpdate.setText(getResources().getString(R.string.lastUpdated)+": "+getTimeStamp());

                SharedPreferences defaultPrefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
                String tempUnit = defaultPrefs.getString(getString(R.string.pref_units_key), getString(R.string.pref_units_metric));

                if(tempUnit.equals(getResources().getString(R.string.pref_units_metric))){
                    tv_tempUnit.setText(getResources().getString(R.string.pref_units_short_label_metric));

                }else if(tempUnit.equals(getResources().getString(R.string.pref_units_imperial))){
                    tv_tempUnit.setText(getResources().getString(R.string.pref_units_short_label_imperial));

                }
            }
        }
    }

    private String getTimeStamp(){
        String pattern = "HH:mm:ss";
        Locale locale = getResources().getConfiguration().locale;
        SimpleDateFormat format = new SimpleDateFormat(pattern, locale);
        Date date = new Date();

        return format.format(date);
    }
}