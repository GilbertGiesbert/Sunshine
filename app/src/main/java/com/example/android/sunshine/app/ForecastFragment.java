package com.example.android.sunshine.app;

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
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
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;

public class ForecastFragment extends Fragment {

    private final String LOG_TAG = ForecastFragment.class.getSimpleName();

    ArrayAdapter<String> adapter;

    @Override
    public void onCreate(Bundle savedInstanceBundle) {
        super.onCreate(savedInstanceBundle);
        setHasOptionsMenu(true);
    }


    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        adapter = new ArrayAdapter<>(getActivity(), R.layout.list_item_forecast, R.id.list_item_forcast_textview, new ArrayList<String>());

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forcast);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String weather = adapter.getItem(position);
                Toast.makeText(getActivity(), weather, Toast.LENGTH_SHORT).show();
            }
        });

        if(adapter.getCount()==0){
            String postCode = "94043";
            String numDays = "7";
            new FetchWeatherTask().execute(postCode, numDays);
        }

        return rootView;
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

            String postCode = "94043";
            String numDays = "7";

            new FetchWeatherTask().execute(postCode, numDays);

            return true;
        }
        return super.onOptionsItemSelected(item);
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
                adapter.setNotifyOnChange(false);

                adapter.clear();
                for(String data: weatherData)
                    if(data!=null)
                        adapter.add(data);

                adapter.setNotifyOnChange(true);
                adapter.notifyDataSetChanged();
            }
        }
    }
}