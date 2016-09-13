package com.example.android.sunshine.app;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.text.format.Time;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Devin on 9/5/2016.
 */
public class ForecastFragment extends Fragment {

    private final String LOG_TAG_ALPHA = ForecastFragment.class.getSimpleName();

    private static ArrayAdapter<String> mForecast_Adaptor;

    public ForecastFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.forecastfragment, menu);
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_refresh) {
            updateWeatherData();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);

        mForecast_Adaptor = new ArrayAdapter<String>
                (getActivity(), R.layout.list_item_forecast, R.id.list_item_forecast_textview, new ArrayList<String>());

        ListView listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecast_Adaptor);

        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                String s = mForecast_Adaptor.getItem(i);
                Intent intent = new Intent(getActivity(), DetailActivity.class).putExtra(Intent.EXTRA_TEXT, s);
                startActivity(intent);
            }
        });

        return rootView;
    }

    @Override
    public void onStart() {
        super.onStart();
        updateWeatherData();
    }


    public void updateWeatherData() {
        SharedPreferences sharedP = PreferenceManager.getDefaultSharedPreferences(getActivity());
        new FetchWeatherTask().execute(sharedP.getString
                (getString(R.string.pref_location_key), getString(R.string.pref_location_default)));
    }

    public boolean checkWeatherUnitsImperial() {
        SharedPreferences sharedP = PreferenceManager.getDefaultSharedPreferences(getActivity());
        String units = sharedP.getString(getString(R.string.pref_units_key), getString(R.string.pref_units_default));
        if (units.equals("metric") || units.equals("Metric")) {
            return false;
        } else {
            return true;
        }
    }

    public double metricToImperial(double l) {
        return l = (l * 1.8) + 32;
    }

    public class FetchWeatherTask extends AsyncTask<String, Void, String[]> {

        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();

        private String getReadableDateString(long time) {
            /*Because the API returns a unix timestamp (measured in seconds),
            it must be converted to milliseconds in order to be converted to a valid date*/
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MM dd");
            return shortenedDateFormat.format(time);
        }

        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about the tenths of a degree
            String check = String.valueOf(checkWeatherUnitsImperial());
            if (checkWeatherUnitsImperial()) {
                high = metricToImperial(high);
                low = metricToImperial(low);
            }
            long roundHigh = Math.round(high);
            long roundLow = Math.round(low);
            String highLowStr = roundHigh + "/" + roundLow;
            return highLowStr;
        }

        /*Tale tje Stromg representing the complete forefcast in JSON format and
        pull out the data we need to construct the Strings needed for the wireframes.
        Fortunately parsing is easy: constructor takes the JSON string and converts it into
        an Objecthierarchy for us.
         */

        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            //These are the names of the JSON objects that need to be extracted
            final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_TEMPERATURE = "temp";
            final String OWM_MAX = "max";
            final String OWM_MIN = "min";
            final String OWM_DESCRIPTION = "main";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);

            /*OWM returns daily forecasts based upon the local time of the city that is being
            asked for, which means that we need to know the GMT offset to translate this data properly*/

            /*Since this data is also sent in-order and the first day is always the
            current day, we're going to take advantage of that to get a nice
            normalized UTC date for all of our weather.
             */

            Time dayTime = new Time();
            dayTime.setToNow();

            //we start at the day returned by local time. Otherwise this is a mess
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            //now we work exclusively in UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for (int i = 0; i < weatherArray.length(); i++) {
                String day;
                String description;
                String highAndLow;

                //Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                /*The date/time is returned as a long. We need to conver that
                into something human-readable, since most people won't read "210238" as
                'this Saturday'
                 */
                long dateTime;
                //Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay + i);
                day = getReadableDateString(dateTime);

                //description is in a child array called "weather", which is 1 element long
                JSONObject weatherObject = dayForecast.getJSONArray(OWM_WEATHER).getJSONObject(0);
                description = weatherObject.getString(OWM_DESCRIPTION);

                //Temperatures are in a child object called "temp". Try not to name variables
                //"temp" when working with temperature. It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMPERATURE);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " + description + " - " + highAndLow;
            }
            return resultStrs;
        }

        @Override
        protected String[] doInBackground(String... params) {
            if (params.length == 0) {
                return null;
            }

            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;
            String forecastJsonStr = null;

            String location = params[0] + ",us";
            String format = "json";
            String units = "metric";
            String day_count = "7";
            String APPID = "7a54d49a5497a4fd7be6d7e03bf3797d";

            //READ INFORMATION FROM OPENWEATHERMAP URL
            try {
                final String FORECAST_BASE_URL = "http://api.openweathermap.org/data/2.5/forecast/daily?";
                final String QUERY_PARAM = "q";
                final String FORMAT_PARAM = "mode";
                final String UNITS_PARAM = "units";
                final String COUNT_PARAM = "cnt";
                final String APPID_PARAM = "APPID";

                Uri constructedURI = Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(QUERY_PARAM, location)
                        .appendQueryParameter(FORMAT_PARAM, format)
                        .appendQueryParameter(UNITS_PARAM, units)
                        .appendQueryParameter(COUNT_PARAM, day_count)
                        .appendQueryParameter(APPID_PARAM, APPID)
                        .build();

                URL url = new URL(constructedURI.toString());
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    return null;
                }
                forecastJsonStr = buffer.toString();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Error ", e);
                return null;
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
            try {
                return getWeatherDataFromJson(forecastJsonStr, Integer.parseInt(day_count));
            } catch (JSONException e) {
                Log.e(LOG_TAG, e.getMessage(), e);
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(String[] strings) {
            if (strings != null) {
                mForecast_Adaptor.clear();
                for (String s : strings) {
                    mForecast_Adaptor.add(s);
                }
            }
        }
    }
}
