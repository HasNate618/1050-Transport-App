package com.here.routing;

import static android.app.Activity.RESULT_OK;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

public class SubmitActivity extends AppCompatActivity {
    private EditText titleInput, descriptionInput;
    private Button submitButton;
    Spinner spinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_submit);

        // Initialize views
        titleInput = findViewById(R.id.titleInput);
        descriptionInput = findViewById(R.id.descriptionInput);
        submitButton = findViewById(R.id.submitButton);
        spinner = findViewById(R.id.typeSpinner);

        // Set up the spinner
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                this,
                R.array.PointTypeArray,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        // Initially disable the submit button
        submitButton.setEnabled(false);

        // Add listeners to text fields
        TextWatcher textWatcher = new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                checkFields();
            }

            @Override
            public void afterTextChanged(Editable s) {}
        };

        titleInput.addTextChangedListener(textWatcher);
        descriptionInput.addTextChangedListener(textWatcher);

        // Handle the submit button click
        submitButton.setOnClickListener(v -> {
            String title = titleInput.getText().toString();
            String description = descriptionInput.getText().toString();
            int type = spinner.getSelectedItemPosition();

            Intent resultIntent = new Intent();
            resultIntent.putExtra("title", title);
            resultIntent.putExtra("description", description);
            resultIntent.putExtra("type", type);
            setResult(RESULT_OK, resultIntent);
            finish();
        });
    }

    // Enable button if both fields are not empty
    private void checkFields() {
        String title = titleInput.getText().toString().trim();
        String description = descriptionInput.getText().toString().trim();

        submitButton.setEnabled(!title.isEmpty() && !description.isEmpty());
    }
}
