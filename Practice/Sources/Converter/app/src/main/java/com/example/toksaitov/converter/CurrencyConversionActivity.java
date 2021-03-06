package com.example.toksaitov.converter;

import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;

public class CurrencyConversionActivity extends AppCompatActivity {

    private Spinner firstCurrencySpinner,
                    resultCurrencySpinner;

    private EditText firstCurrencyEditText,
                     resultEditText,
                     currencyRateEditText;

    private JSONObject currencyRates;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_currency_conversion);

        firstCurrencySpinner = findViewById(R.id.firstCurrencySpinner);
        resultCurrencySpinner = findViewById(R.id.resultCurrencySpinner);

        firstCurrencyEditText = findViewById(R.id.firstCurrencyEditText);
        resultEditText = findViewById(R.id.resultEditText);
        currencyRateEditText = findViewById(R.id.currencyRateEditText);

        setupSpinners();
        setupInputFields();
        loadInitialCurrencyRates();
    }

    @Override
    protected void onStop() {
        super.onStop();

        saveCurrencyRates();
    }

    public void downloadNewRate(View view) {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="https://api.exchangeratesapi.io/latest?base=" + firstCurrencySpinner.getSelectedItem();

        final Context context = this;
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    double rate;
                    try {
                        rate = response.getJSONObject("rates").getDouble((String) resultCurrencySpinner.getSelectedItem());
                    } catch (JSONException e) {
                        Toast.makeText(context, "Failed to load the currency rate", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    currencyRateEditText.setText(String.valueOf(rate));
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast.makeText(context, "Failed to load the currency rates", Toast.LENGTH_SHORT).show();
                }
            }
        );
        queue.add(jsonObjectRequest);
    }

    private void setupSpinners() {
        RequestQueue queue = Volley.newRequestQueue(this);
        String url ="https://api.exchangeratesapi.io/latest";

        final Context context = this;
        JsonObjectRequest jsonObjectRequest = new JsonObjectRequest(Request.Method.GET, url, null,
            new Response.Listener<JSONObject>() {
                @Override
                public void onResponse(JSONObject response) {
                    populateSpinners(response);
                }
            },
            new Response.ErrorListener() {
                @Override
                public void onErrorResponse(VolleyError error) {
                    Toast.makeText(context, "Failed to load the currency rates", Toast.LENGTH_SHORT).show();
                }
            }
        );
        queue.add(jsonObjectRequest);

        AdapterView.OnItemSelectedListener listener = new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                loadRate();
                convert();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) { }
        };

        firstCurrencySpinner.setOnItemSelectedListener(listener);
        resultCurrencySpinner.setOnItemSelectedListener(listener);
    }

    private void populateSpinners(JSONObject response) {
        JSONObject rates;
        try {
            rates = response.getJSONObject("rates");
        } catch (JSONException e) {
            Toast.makeText(this, "Failed to process currency rates", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayList<CharSequence> currencies = new ArrayList<>();
        Iterator<String> keys = rates.keys();
        while (keys.hasNext()) {
            currencies.add(keys.next());
        }
        CharSequence[] currenciesArray = currencies.toArray(new CharSequence[0]);
        if (currenciesArray == null) {
            Toast.makeText(this, "Failed to load currency rates", Toast.LENGTH_SHORT).show();
            return;
        }

        ArrayAdapter<CharSequence> adapter =
            new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, currenciesArray);

        adapter.setDropDownViewResource(
            android.R.layout.simple_spinner_dropdown_item
        );
        firstCurrencySpinner.setAdapter(adapter);
        resultCurrencySpinner.setAdapter(adapter);
    }

    private void setupInputFields() {
        firstCurrencyEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                convert();
            }
        });

        currencyRateEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {  }
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) { }

            @Override
            public void afterTextChanged(Editable s) {
                updateRates();
                convert();
            }
        });
    }

    private void loadInitialCurrencyRates() {
        File conversionRatiosFile;
        InputStream inputStream = null;

        if ((conversionRatiosFile = getFileStreamPath("user_currency_rates.json")).exists()) {
            try {
                inputStream = new FileInputStream(conversionRatiosFile);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        } else {
            inputStream = getResources().openRawResource(R.raw.initial_currency_rates);
        }

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder result = new StringBuilder();

        try {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                result.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            currencyRates = new JSONObject(result.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void saveCurrencyRates() {
        String filename = "user_currency_rates.json";
        String fileContents = currencyRates.toString();
        FileOutputStream outputStream;

        try {
            outputStream = openFileOutput(filename, Context.MODE_PRIVATE);
            outputStream.write(fileContents.getBytes());
            outputStream.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadRate() {
        String fromCurrency =
            firstCurrencySpinner.getSelectedItem().toString();
        String toCurrency =
            resultCurrencySpinner.getSelectedItem().toString();

        double rate = 1.0;
        if (firstCurrencySpinner.getSelectedItemPosition() !=
                resultCurrencySpinner.getSelectedItemPosition()) {
            String key =
                    fromCurrency + "-" + toCurrency;

            try {
                rate = currencyRates.getDouble(key);
            } catch (JSONException e) {
                downloadNewRate(null);
            }
        }

        currencyRateEditText.setText(
            String.valueOf(rate)
        );
    }

    private void updateRates() {
        String fromCurrency =
            firstCurrencySpinner.getSelectedItem().toString();
        String toCurrency =
            resultCurrencySpinner.getSelectedItem().toString();

        if (firstCurrencySpinner.getSelectedItemPosition() !=
                resultCurrencySpinner.getSelectedItemPosition()) {
            String key =
                fromCurrency + "-" + toCurrency;

            String rateText =
                currencyRateEditText.getText().toString();

            double rate;
            try {
                rate = Double.parseDouble(rateText);
            } catch (NumberFormatException e) {
                e.printStackTrace();
                return;
            }

            try {
                currencyRates.put(key, rate);
            } catch (JSONException e) {
                e.printStackTrace();
                return;
            }
        }
    }

    private void convert() {
        String valueText =
            firstCurrencyEditText.getText().toString();

        double value;
        try {
            value = Double.parseDouble(valueText);
        } catch (NumberFormatException e) {
            e.printStackTrace();

            resultEditText.setText("");
            return;
        }

        String rateText =
            currencyRateEditText.getText().toString();

        double rate;
        try {
            rate = Double.parseDouble(rateText);
        } catch (NumberFormatException e) {
            e.printStackTrace();

            resultEditText.setText("");
            return;
        }

        double result = value * rate;
        resultEditText.setText(
            String.format(Locale.getDefault(), "%.2f", result)
        );
    }
}
