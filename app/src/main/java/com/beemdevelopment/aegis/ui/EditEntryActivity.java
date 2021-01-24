package com.beemdevelopment.aegis.ui;

import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.AdapterView;
import android.widget.AutoCompleteTextView;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;

import com.amulyakhare.textdrawable.TextDrawable;
import com.avito.android.krop.KropView;
import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.encoding.Base32;
import com.beemdevelopment.aegis.encoding.EncodingException;
import com.beemdevelopment.aegis.helpers.DropdownHelper;
import com.beemdevelopment.aegis.helpers.EditTextHelper;
import com.beemdevelopment.aegis.helpers.TextDrawableHelper;
import com.beemdevelopment.aegis.otp.GoogleAuthInfo;
import com.beemdevelopment.aegis.otp.HotpInfo;
import com.beemdevelopment.aegis.otp.OtpInfo;
import com.beemdevelopment.aegis.otp.OtpInfoException;
import com.beemdevelopment.aegis.otp.SteamInfo;
import com.beemdevelopment.aegis.otp.TotpInfo;
import com.beemdevelopment.aegis.util.Cloner;
import com.beemdevelopment.aegis.vault.VaultEntry;
import com.beemdevelopment.aegis.vault.VaultManager;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import de.hdodenhof.circleimageview.CircleImageView;

public class EditEntryActivity extends AegisActivity {
    private static final int PICK_IMAGE_REQUEST = 0;

    private boolean _isNew = false;
    private VaultEntry _origEntry;
    private TreeSet<String> _groups;
    private boolean _hasCustomIcon = false;
    // keep track of icon changes separately as the generated jpeg's are not deterministic
    private boolean _hasChangedIcon = false;
    private boolean _isEditingIcon;
    private CircleImageView _iconView;
    private ImageView _saveImageButton;

    private TextInputEditText _textName;
    private TextInputEditText _textIssuer;
    private TextInputEditText _textPeriodCounter;
    private TextInputLayout _textPeriodCounterLayout;
    private TextInputEditText _textDigits;
    private TextInputEditText _textSecret;

    private AutoCompleteTextView _dropdownType;
    private AutoCompleteTextView _dropdownAlgo;
    private AutoCompleteTextView _dropdownGroup;
    private List<String> _dropdownGroupList = new ArrayList<>();

    private KropView _kropView;

    private RelativeLayout _advancedSettingsHeader;
    private RelativeLayout _advancedSettings;

    private VaultManager _vault;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_entry);
        setSupportActionBar(findViewById(R.id.toolbar));

        _vault = getApp().getVaultManager();
        _groups = _vault.getGroups();

        ActionBar bar = getSupportActionBar();
        bar.setHomeAsUpIndicator(R.drawable.ic_close);
        bar.setDisplayHomeAsUpEnabled(true);

        // retrieve info from the calling activity
        Intent intent = getIntent();
        UUID entryUUID = (UUID) intent.getSerializableExtra("entryUUID");
        if (entryUUID != null) {
            _origEntry = _vault.getEntryByUUID(entryUUID);
        } else {
            _origEntry = (VaultEntry) intent.getSerializableExtra("newEntry");
            _isNew = true;
            setTitle(R.string.add_new_entry);
        }

        String selectedGroup = intent.getStringExtra("selectedGroup");

        // set up fields
        _iconView = findViewById(R.id.profile_drawable);
        _kropView = findViewById(R.id.krop_view);
        _saveImageButton = findViewById(R.id.iv_saveImage);
        _textName = findViewById(R.id.text_name);
        _textIssuer = findViewById(R.id.text_issuer);
        _textPeriodCounter = findViewById(R.id.text_period_counter);
        _textPeriodCounterLayout = findViewById(R.id.text_period_counter_layout);
        _textDigits = findViewById(R.id.text_digits);
        _textSecret = findViewById(R.id.text_secret);
        _dropdownType = findViewById(R.id.dropdown_type);
        DropdownHelper.fillDropdown(this, _dropdownType, R.array.otp_types_array);
        _dropdownAlgo = findViewById(R.id.dropdown_algo);
        DropdownHelper.fillDropdown(this, _dropdownAlgo, R.array.otp_algo_array);
        _dropdownGroup = findViewById(R.id.dropdown_group);
        updateGroupDropdownList();
        DropdownHelper.fillDropdown(this, _dropdownGroup, _dropdownGroupList);

        _advancedSettingsHeader = findViewById(R.id.accordian_header);
        _advancedSettings = findViewById(R.id.expandableLayout);

        // fill the fields with values if possible
        if (_origEntry.hasIcon()) {
            Glide.with(this)
                .asDrawable()
                .load(_origEntry)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(false)
                .into(_iconView);
            _hasCustomIcon = true;
        } else {
            TextDrawable drawable = TextDrawableHelper.generate(_origEntry.getIssuer(), _origEntry.getName(), _iconView);
            _iconView.setImageDrawable(drawable);
        }

        _textName.setText(_origEntry.getName());
        _textIssuer.setText(_origEntry.getIssuer());

        OtpInfo info = _origEntry.getInfo();
        if (info instanceof TotpInfo) {
            _textPeriodCounterLayout.setHint(R.string.period_hint);
            _textPeriodCounter.setText(Integer.toString(((TotpInfo) info).getPeriod()));
        } else if (info instanceof HotpInfo) {
            _textPeriodCounterLayout.setHint(R.string.counter);
            _textPeriodCounter.setText(Long.toString(((HotpInfo) info).getCounter()));
        } else {
            throw new RuntimeException(String.format("Unsupported OtpInfo type: %s", info.getClass()));
        }
        _textDigits.setText(Integer.toString(info.getDigits()));

        byte[] secretBytes = _origEntry.getInfo().getSecret();
        if (secretBytes != null) {
            String secretString = Base32.encode(secretBytes);
            _textSecret.setText(secretString);
        }

        _dropdownType.setText(_origEntry.getInfo().getTypeId().toUpperCase(), false);
        _dropdownAlgo.setText(_origEntry.getInfo().getAlgorithm(false), false);

        String group = _origEntry.getGroup();
        setGroup(group);

        // update the icon if the text changed
        _textIssuer.addTextChangedListener(_iconChangeListener);
        _textName.addTextChangedListener(_iconChangeListener);

        // show/hide period and counter fields on type change
        _dropdownType.setOnItemClickListener((parent, view, position, id) -> {
            String type = _dropdownType.getText().toString().toLowerCase();
            switch (type) {
                case TotpInfo.ID:
                case SteamInfo.ID:
                    _textPeriodCounterLayout.setHint(R.string.period_hint);
                    _textPeriodCounter.setText(String.format("%d", 30));
                    break;
                case HotpInfo.ID:
                    _textPeriodCounterLayout.setHint(R.string.counter);
                    _textPeriodCounter.setText(String.format("%d", 0));
                    break;
                default:
                    throw new RuntimeException(String.format("Unsupported OTP type: %s", type));
            }
        });

        _iconView.setOnClickListener(v -> {
            startIconSelectionActivity();
        });

        _advancedSettingsHeader.setOnClickListener(v -> openAdvancedSettings());

        // automatically open advanced settings since 'Secret' is required.
        if (_isNew) {
            openAdvancedSettings();
            setGroup(selectedGroup);
        }

        _dropdownGroup.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            private int prevPosition = _dropdownGroupList.indexOf(_dropdownGroup.getText().toString());

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (position == _dropdownGroupList.size() - 1) {
                    Dialogs.showTextInputDialog(EditEntryActivity.this, R.string.set_group, R.string.group_name_hint, text -> {
                        String str = new String(text);
                        if (str.isEmpty()) {
                            return;
                        }
                        _groups.add(str);
                        updateGroupDropdownList();
                        _dropdownGroup.setText(_dropdownGroupList.get(position), false);
                    });
                    _dropdownGroup.setText(_dropdownGroupList.get(prevPosition), false);
                } else {
                    prevPosition = position;
                }
            }
        });
    }

    private void setGroup(String groupName) {
        int pos = 0;
        if (groupName != null) {
            pos = _groups.contains(groupName) ? _groups.headSet(groupName).size() + 1 : 0;
        }

        _dropdownGroup.setText(_dropdownGroupList.get(pos), false);
    }

    private void openAdvancedSettings() {
        Animation fadeOut = new AlphaAnimation(1, 0);
        fadeOut.setInterpolator(new AccelerateInterpolator());
        fadeOut.setDuration(220);
        _advancedSettingsHeader.startAnimation(fadeOut);

        Animation fadeIn = new AlphaAnimation(0, 1);
        fadeIn.setInterpolator(new AccelerateInterpolator());
        fadeIn.setDuration(250);

        fadeOut.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                _advancedSettingsHeader.setVisibility(View.GONE);
                _advancedSettings.startAnimation(fadeIn);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });

        fadeIn.setAnimationListener(new Animation.AnimationListener() {
            @Override
            public void onAnimationStart(Animation animation) {

            }

            @Override
            public void onAnimationEnd(Animation animation) {
                _advancedSettings.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationRepeat(Animation animation) {

            }
        });
    }

    private void updateGroupDropdownList() {
        Resources res = getResources();
        _dropdownGroupList.clear();
        _dropdownGroupList.add(res.getString(R.string.no_group));
        _dropdownGroupList.addAll(_groups);
        _dropdownGroupList.add(res.getString(R.string.new_group));
    }

    @Override
    public void onBackPressed() {
        if (_isEditingIcon) {
            stopEditingIcon(false);
            return;
        }

        AtomicReference<String> msg = new AtomicReference<>();
        AtomicReference<VaultEntry> entry = new AtomicReference<>();

        try {
            entry.set(parseEntry());
        } catch (ParseException e) {
            msg.set(e.getMessage());
        }

        // close the activity if the entry has not been changed
        if (!_hasChangedIcon && _origEntry.equals(entry.get())) {
            super.onBackPressed();
            return;
        }

        // ask for confirmation if the entry has been changed
        Dialogs.showDiscardDialog(this,
                (dialog, which) -> {
                    // if the entry couldn't be parsed, we show an error dialog
                    if (msg.get() != null) {
                        onSaveError(msg.get());
                        return;
                    }

                    addAndFinish(entry.get());
                },
                (dialog, which) -> super.onBackPressed()
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.action_save:
                onSave();
                break;
            case R.id.action_delete:
                Dialogs.showDeleteEntriesDialog(this, Collections.singletonList(_origEntry.getIssuer()), (dialog, which) -> {
                    deleteAndFinish(_origEntry);
                });
                break;
            case R.id.action_edit_icon:
                startIconSelectionActivity();
                break;
            case R.id.action_default_icon:
                TextDrawable drawable = TextDrawableHelper.generate(_origEntry.getIssuer(), _origEntry.getName(), _iconView);
                _iconView.setImageDrawable(drawable);
                _hasCustomIcon = false;
                _hasChangedIcon = true;
            default:
                return super.onOptionsItemSelected(item);
        }

        return true;
    }

    private void startIconSelectionActivity() {
        Intent galleryIntent = new Intent(Intent.ACTION_PICK);
        galleryIntent.setDataAndType(android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");

        Intent fileIntent = new Intent(Intent.ACTION_GET_CONTENT);
        fileIntent.setType("image/*");

        Intent chooserIntent = Intent.createChooser(galleryIntent, getString(R.string.select_icon));
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[] { fileIntent });
        startActivityForResult(chooserIntent, PICK_IMAGE_REQUEST);
    }

    private void startEditingIcon(Uri data) {
        Glide.with(this)
                .asBitmap()
                .load(data)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(false)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        _kropView.setBitmap(resource);
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {

                    }
                });
        _iconView.setVisibility(View.GONE);
        _kropView.setVisibility(View.VISIBLE);

        _saveImageButton.setOnClickListener(v -> {
            stopEditingIcon(true);
        });

        _isEditingIcon = true;
    }

    private void stopEditingIcon(boolean save) {
        if (save) {
            _iconView.setImageBitmap(_kropView.getCroppedBitmap());
        }
        _iconView.setVisibility(View.VISIBLE);
        _kropView.setVisibility(View.GONE);
        _hasCustomIcon = _hasCustomIcon || save;
        _hasChangedIcon = save;
        _isEditingIcon = false;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit, menu);
        if (_isNew) {
            menu.findItem(R.id.action_delete).setVisible(false);
        }
        if (!_hasCustomIcon) {
            menu.findItem(R.id.action_default_icon).setVisible(false);
        }

        return true;
    }

    private void addAndFinish(VaultEntry entry) {
        // It's possible that the new entry was already added to the vault, but writing the
        // vault to disk failed, causing the user to tap 'Save' again. Calling addEntry
        // again would cause a crash in that case, so the isEntryDuplicate check prevents
        // that.
        if (_isNew && !_vault.isEntryDuplicate(entry)) {
            _vault.addEntry(entry);
        } else {
            _vault.replaceEntry(entry);
        }

        saveAndFinish(entry, false);
    }

    private void deleteAndFinish(VaultEntry entry) {
        _vault.removeEntry(entry);
        saveAndFinish(entry, true);
    }

    private void saveAndFinish(VaultEntry entry, boolean delete) {
        Intent intent = new Intent();
        intent.putExtra("entryUUID", entry.getUUID());
        intent.putExtra("delete", delete);

        if (saveVault(true)) {
            setResult(RESULT_OK, intent);
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, final int resultCode, Intent data) {
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            startEditingIcon(data.getData());
        }

        super.onActivityResult(requestCode, resultCode, data);
    }

    private int parsePeriod() throws ParseException {
        try {
            return Integer.parseInt(_textPeriodCounter.getText().toString());
        } catch (NumberFormatException e) {
            throw new ParseException("Period is not an integer.");
        }
    }

    private VaultEntry parseEntry() throws ParseException {
        if (_textSecret.length() == 0) {
            throw new ParseException("Secret is a required field.");
        }

        String type = _dropdownType.getText().toString();
        String algo = _dropdownAlgo.getText().toString();

        int digits;
        try {
            digits = Integer.parseInt(_textDigits.getText().toString());
        } catch (NumberFormatException e) {
            throw new ParseException("Digits is not an integer.");
        }

        byte[] secret;
        try {
            String secretString = new String(EditTextHelper.getEditTextChars(_textSecret));
            secret = GoogleAuthInfo.parseSecret(secretString);
            if (secret.length == 0) {
                throw new ParseException("Secret cannot be empty");
            }
        } catch (EncodingException e) {
            throw new ParseException("Secret is not valid base32.");
        }

        OtpInfo info;
        try {
            switch (type.toLowerCase()) {
                case TotpInfo.ID:
                    info = new TotpInfo(secret, algo, digits, parsePeriod());
                    break;
                case SteamInfo.ID:
                    info = new SteamInfo(secret, algo, digits, parsePeriod());
                    break;
                case HotpInfo.ID:
                    long counter;
                    try {
                        counter = Long.parseLong(_textPeriodCounter.getText().toString());
                    } catch (NumberFormatException e) {
                        throw new ParseException("Counter is not an integer.");
                    }
                    info = new HotpInfo(secret, algo, digits, counter);
                    break;
                default:
                    throw new RuntimeException(String.format("Unsupported OTP type: %s", type));
            }

            info.setDigits(digits);
            info.setAlgorithm(algo);
        } catch (OtpInfoException e) {
            throw new ParseException("The entered info is incorrect: " + e.getMessage());
        }

        VaultEntry entry = Cloner.clone(_origEntry);
        entry.setInfo(info);
        entry.setIssuer(_textIssuer.getText().toString());
        entry.setName(_textName.getText().toString());

        int groupPos = _dropdownGroupList.indexOf(_dropdownGroup.getText().toString());
        if (groupPos != 0) {
            String group = _dropdownGroupList.get(groupPos);
            entry.setGroup(group);
        } else {
            entry.setGroup(null);
        }

        if (_hasChangedIcon) {
            if (_hasCustomIcon) {
                Bitmap bitmap = ((BitmapDrawable) _iconView.getDrawable()).getBitmap();
                ByteArrayOutputStream stream = new ByteArrayOutputStream();
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream);
                byte[] data = stream.toByteArray();
                entry.setIcon(data);
            } else {
                entry.setIcon(null);
            }
        }

        return entry;
    }

    private void onSaveError(String msg) {
        Dialogs.showSecureDialog(new AlertDialog.Builder(this)
                .setTitle(getString(R.string.saving_profile_error))
                .setMessage(msg)
                .setPositiveButton(android.R.string.ok, null)
                .create());
    }

    private boolean onSave() {
        if (_isEditingIcon) {
            stopEditingIcon(true);
        }

        VaultEntry entry;
        try {
            entry = parseEntry();
        } catch (ParseException e) {
            onSaveError(e.getMessage());
            return false;
        }

        addAndFinish(entry);
        return true;
    }

    private TextWatcher _iconChangeListener = new TextWatcher() {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (!_hasCustomIcon) {
                TextDrawable drawable = TextDrawableHelper.generate(_textIssuer.getText().toString(), _textName.getText().toString(), _iconView);
                _iconView.setImageDrawable(drawable);
            }
        }
    };

    private static class ParseException extends Exception {
        public ParseException(String message) {
            super(message);
        }
    }
}
