package com.example.schedulenotifications;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;
import android.annotation.SuppressLint;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;
import com.example.schedulenotifications.models.Contact;
import com.example.schedulenotifications.repositories.ContactRepository;
import com.hbb20.CountryCodePicker;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.regex.Pattern;
import static com.example.schedulenotifications.Converters.convertDateToLong;
import static com.example.schedulenotifications.Converters.convertLongToDate;

public class ContactEdit extends AppCompatActivity {

    private EditText editTextName, editTextPhone, editTextFrequency;
    @SuppressLint("StaticFieldLeak")
    private static TextView selectedDate;
    private TextView nextAlarm;
    private SwitchCompat statusSwitch;
    private CountryCodePicker country_picker;
    private ContactRepository contactRepository;
    private Contact editContact;
    private Context context;
    public static Calendar selectedCalStartDate;
    private int editContactID;
    boolean isNewContact = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_edit);

        // initialize basic variables
        context = getApplicationContext();
        contactRepository = new ContactRepository(getApplication());

        // get ui components
        editTextName = findViewById(R.id.name_entry_edit_text);
        country_picker = findViewById(R.id.country_picker);
        editTextPhone = findViewById(R.id.phone_entry_edit_text);
        editTextFrequency = findViewById(R.id.frequency_entry_edit_text);
        statusSwitch = findViewById(R.id.status_switch);
        selectedDate = findViewById(R.id.selected_start_date);
        nextAlarm = findViewById(R.id.next_alarm);
        Button deleteButton = findViewById(R.id.delete_contact_button);

        // limit input of possible frequencies to between 1 and 365 days
        editTextFrequency.setFilters(new InputFilter[]{new InputFilterMinMax("1", "365")});

        editContactID = getIntent().getIntExtra("id", -100);

        if (editContactID == -100)                    // is this a new contact?
            isNewContact = true;

            //selectedStartDate = new Date();         // start date will initialize to now for new contacts
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EE MM d, yyyy h:mm aa", Locale.US);
            selectedCalStartDate = Calendar.getInstance();

            selectedDate.setText(simpleDateFormat.format(selectedCalStartDate.getTime()));      // set the selected start date textview
            statusSwitch.setChecked(true);          // new contacts are active by default
            country_picker.detectLocaleCountry(true);
            updateNextAlarm();                      // next alarm is updated based on start date and frequency
        if (!isNewContact) {                        // if it's not a new contact, we retrieve the contact info to set up the
            fetchContactById(editContactID);        // edit contact activity & we enable the delete button
            deleteButton.setVisibility(View.VISIBLE);
        }

        deleteButton.setOnClickListener(v -> deleteContact(editContact));

        editTextFrequency.addTextChangedListener(new TextWatcher() {            // whenever we update the frequency, update the next alarm in response
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(s.length() != 0) {
                    updateNextAlarm();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        selectedDate.addTextChangedListener(new TextWatcher() {                 // whenever we update the start date, update the next alarm in response
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if(s.length() != 0) {
                    updateNextAlarm();
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        statusSwitch.setOnClickListener((buttonView) -> {
            if (statusSwitch.isChecked()) {
                updateNextAlarm();
            }
            else {
                nextAlarm.setText(getResources().getString(R.string.notifications_turned_off));
            }
        });
    }

    // whenever the start date or frequency changes, or we're updating the activity for with an existing contact's data,
    // we call this to update the next alarm date & time shown to the user
    public void updateNextAlarm() {
        if (!statusSwitch.isChecked()) {
            nextAlarm.setText(getResources().getString(R.string.notifications_turned_off));
            return;
        }

        String stringFrequency = editTextFrequency.getText().toString().trim();
        int freq = 5;          // frequency is 5 by default
        if (stringFrequency.length() != 0) {
            freq = Integer.parseInt(stringFrequency);
        }
        if ((freq < 1) || (freq > 365)) {       // values outside these bounds shouldn't be possible
            freq = 5;
        }

        long milDay = 60000L;     //60000L is a minute //86400000L is a day
        long mRepeatTime = freq * milDay;

        long startDate;
        if (selectedCalStartDate == null) {
            startDate = convertDateToLong(new Date());
        }
        else {
            Date selectedStartDate = selectedCalStartDate.getTime();
            startDate = convertDateToLong(selectedStartDate);
        }

        @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EE MM d, yyyy h:mm aa");

        Calendar nextCalDate = Calendar.getInstance();
        Date nextDate = Converters.convertLongToDate(MessageCreator.getNextAlarmTime(startDate, mRepeatTime));
        nextCalDate.setTime(nextDate);
        nextAlarm.setText(simpleDateFormat.format(nextCalDate.getTime()));
    }

    // date picker for selecting the start date for notifications
    public void showDatePickerDialog(View v) {
        DialogFragment newFragment = new DatePickerFragment();
        newFragment.show(getSupportFragmentManager(), "datePicker");
    }

    public static class DatePickerFragment extends DialogFragment
            implements DatePickerDialog.OnDateSetListener {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // use the current date as the default date in the picker
            final Calendar c = Calendar.getInstance();
            int year = c.get(Calendar.YEAR);
            int month = c.get(Calendar.MONTH);
            int day = c.get(Calendar.DAY_OF_MONTH);

            // create a new instance of DatePickerDialog and return it
            return new DatePickerDialog(getActivityNonNull(), this, year, month, day);
        }

        FragmentActivity getActivityNonNull() {
            if (super.getActivity() != null) {
                return super.getActivity();
            } else {
                throw new RuntimeException("null returned from getActivity()");
            }
        }

        // update the first scheduled TextView
        public void onDateSet(DatePicker view, int year, int month, int day) {
            selectedCalStartDate.set(Calendar.YEAR, year);
            selectedCalStartDate.set(Calendar.MONTH, month);
            selectedCalStartDate.set(Calendar.DAY_OF_MONTH, day);
            @SuppressLint("SimpleDateFormat") SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EE MM d, yyyy h:mm aa");
            selectedDate.setText(simpleDateFormat.format(selectedCalStartDate.getTime()));
        }
    }

    // time picker for selecting the start time for notifications
    public void showTimePickerDialog(View v) {
        DialogFragment newFragment = new TimePickerFragment();
        newFragment.show(getSupportFragmentManager(), "timePicker");
    }

    public static class TimePickerFragment extends DialogFragment
            implements TimePickerDialog.OnTimeSetListener {

        @NonNull
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            // use the current time as the default values for the picker
            final Calendar c = Calendar.getInstance();

            int hour = c.get(Calendar.HOUR_OF_DAY);
            int minute = c.get(Calendar.MINUTE);

            // create a new instance of TimePickerDialog and return it
            return new TimePickerDialog(getActivity(), this, hour, minute,
                    DateFormat.is24HourFormat(getActivity()));
        }

        // update the first scheduled TextView
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            selectedCalStartDate.set(Calendar.HOUR_OF_DAY, hourOfDay);
            selectedCalStartDate.set(Calendar.MINUTE, minute);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EE MM d, yyyy h:mm aa", Locale.US);
            selectedDate.setText(simpleDateFormat.format(selectedCalStartDate.getTime()));
        }
    }

    // save the contact
    public void saveContact(View v) {
        final int contactID = editContactID;        // we'll only have an ID already if we're editing an existing contact

        final String contactName = editTextName.getText().toString().trim();
        if (!validateName(contactName)) {
            return;
        }

        String stringFrequency = editTextFrequency.getText().toString().trim();
        int freq = 5;
        if (stringFrequency.length() != 0) {
            freq = Integer.parseInt(stringFrequency);
        }
        if (freq < 1) {
            freq = 5;
        }
        final int frequency = freq;

        if (!validateFrequency(frequency)) {
            return;
        }

        final String countryCode = country_picker.getSelectedCountryNameCode();
        String phoneTemp = editTextPhone.getText().toString().trim();
        final String phoneNumber = phoneTemp.replaceAll("[^\\d.]", "");
        if (!validatePhoneNumber(phoneTemp, phoneNumber)) {
            return;
        }

        final int status = statusSwitch.isChecked() ? 1 : 0;

        boolean goToContactSelect = false;
        long startDate;
        if (selectedCalStartDate == null) {
            startDate = convertDateToLong(new Date());
        }
        else {
            Date selectedStartDate = selectedCalStartDate.getTime();
            startDate = convertDateToLong(selectedStartDate);
        }

        // creating a new contact
        Contact contact = new Contact();
        contact.setName(contactName);
        contact.setPhone(phoneNumber);
        contact.setFrequency(frequency);
        contact.setStartDate(startDate);
        contact.setStatus(status);
        contact.setCountryCode(countryCode);

        if (isNewContact) {
            contactRepository.insert(contact);
        }
        else {
            contact.setId(contactID);
            contactRepository.update(contact);
            goToContactSelect = true;           // if we're editing an existing contact, we came from ContactSelect so we should return there
        }

        MessageCreator.setMessageTask(context, contact);

        if (goToContactSelect) {
            Intent contactSelectIntent = new Intent(context, ContactSelect.class);
            contactSelectIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(contactSelectIntent);
        }
        else {
            Intent mainActivityIntent = new Intent(context, MainActivity.class);
            mainActivityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(mainActivityIntent);
        }

        finish();
        Toast.makeText(context, getResources().getString(R.string.contact_saved_toast), Toast.LENGTH_LONG).show();
    }

    // if we're editing an existing contact, we fetch it by its ID and fill out fields with the contact's data
    @SuppressLint("StaticFieldLeak")
    private void fetchContactById(final int contactID) {

        new AsyncTask<Integer, Void, Contact>() {
            @Override
            protected Contact doInBackground(Integer... params) {
                return contactRepository.contactDao.fetchContactById(params[0]);
            }

            @SuppressLint("DefaultLocale")
            @Override
            protected void onPostExecute(Contact contact) {
                super.onPostExecute(contact);
                editTextName.setText(contact.getName());
                editTextPhone.setText(contact.getPhone());
                editTextFrequency.setText(String.format("%d", contact.getFrequency()));

                SimpleDateFormat simpleDateFormat = new SimpleDateFormat("EE MM d, yyyy h:mm aa", Locale.US);
                selectedCalStartDate.setTime(convertLongToDate(contact.getStartDate()));
                selectedDate.setText(simpleDateFormat.format(selectedCalStartDate.getTime()));

                int contactStatus = contact.getStatus();
                if (contactStatus == 1) {
                    statusSwitch.setChecked(true);
                }
                else {
                    statusSwitch.setChecked(false);
                }

                editContact = contact;
                editContactID = contact.getId();
                country_picker.setCountryForNameCode(contact.getCountryCode());

                updateNextAlarm();
            }
        }.execute(contactID);
    }

    // deletes a contact
    @SuppressLint("StaticFieldLeak")
    private void deleteContact(Contact contact)
    {
        contact.setStatus(0);
        MessageCreator.setMessageTask(context, contact);        // cancel their notifications before deleting
        contactRepository.delete(contact);
        Toast.makeText(context, getResources().getString(R.string.contact_deleted_toast), Toast.LENGTH_LONG).show();

        Intent contactSelectIntent = new Intent(context, ContactSelect.class);
        contactSelectIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(contactSelectIntent);
        finish();
    }

    // input filter to limit entered frequencies to reasonable values
    public static class InputFilterMinMax implements InputFilter {

        private int min, max;

/*        public InputFilterMinMax(int min, int max) {
            this.min = min;
            this.max = max;
        }*/

        InputFilterMinMax(String min, String max) {
            this.min = Integer.parseInt(min);
            this.max = Integer.parseInt(max);
        }

        @Override
        public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) {
            try {
                int input = Integer.parseInt(dest.toString() + source.toString());
                if (isInRange(min, max, input))
                    return null;
            } catch (NumberFormatException ignored) { }
            return "";
        }

        private boolean isInRange(int a, int b, int c) {
            return b > a ? c >= a && c <= b : c >= b && c <= a;
        }
    }

    // validation code starts here


    // name validation

    public boolean validateName(String name) {
        if (isNameEmpty(name)) {
            editTextName.setError(getResources().getString(R.string.name_missing_toast));
            editTextName.requestFocus();
            return false;
        }
        if (isNameTooLong(name)) {
            editTextName.setError(getResources().getString(R.string.name_too_long_toast));
            editTextName.requestFocus();
            return false;
        }
        return true;
    }

    // static name validation helpers

    public static boolean isNameEmpty(String name) {
        return name.isEmpty();
    }

    public static boolean isNameTooLong(String name) { return (name.length() > 100);}

    // frequency validation

    public boolean validateFrequency(int frequency) {
        if (isFrequencyTooLong(frequency)) {
            editTextFrequency.setError(getResources().getString(R.string.max_frequency_toast));
            editTextFrequency.requestFocus();
            return false;
        }
        if (isFrequencyTooSmall(frequency)) {
            editTextFrequency.setError(getResources().getString(R.string.min_frequency_toast));
            editTextFrequency.requestFocus();
            return false;
        }
        return true;
    }

    // static frequency validation helpers

    public static boolean isFrequencyTooLong(int frequency) {
        return (frequency > 365);
    }

    public static boolean isFrequencyTooSmall(int frequency) {
        return (frequency < 1);
    }

    // phone number validation

    public boolean validatePhoneNumber(String phoneTemp, String phoneNumber) {
        if (phoneNumberStartsWithPlus(phoneTemp)) {
            editTextPhone.setError(getResources().getString(R.string.included_country_code_toast));
            editTextPhone.requestFocus();
            return false;
        }

        if (phoneNumberIsEmpty(phoneNumber)) {
            editTextPhone.setError(getResources().getString(R.string.phone_number_missing_toast));
            editTextPhone.requestFocus();
            return false;
        }

        if ((!phoneNumberIsEmpty(phoneNumber)) && !isMobileValidLength(phoneNumber)) {
            editTextPhone.setError(getResources().getString(R.string.invalid_phone_number_length_toast));
            editTextPhone.requestFocus();
            return false;
        }
        return true;
    }

    // static phone number validation helpers

    public static boolean phoneNumberStartsWithPlus(String phoneNumber) { return phoneNumber.startsWith("+"); }

    public static boolean phoneNumberIsEmpty(String phoneNumber) { return phoneNumber.isEmpty(); }

    public static boolean isMobileValidLength(String phone) {
        if(!Pattern.matches("[a-zA-Z]+", phone)) {
            return phone.length() >= 4 && phone.length() <= 12;
        }
        return false;
    }
}
