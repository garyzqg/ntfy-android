package io.heckel.ntfy.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import io.heckel.ntfy.R;
import io.heckel.ntfy.util.NotificationOperaterKt;

public class TestJavaActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_java);
        NotificationOperaterKt.subscribe();

    }
}