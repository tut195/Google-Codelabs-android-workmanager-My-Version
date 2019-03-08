package com.example.background.workers;

import static android.support.constraint.Constraints.TAG;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.example.background.Constants;

public class BlurWorker extends Worker {


  public BlurWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
  }

  @NonNull
  @Override
  public Worker.Result doWork() {
    Context applicationContext = getApplicationContext();

    String resourceUri = getInputData().getString(Constants.KEY_IMAGE_URI);

    // Makes a notification when the work starts and slows down the work so that it's easier to
    // see each WorkRequest start, even on emulated devices
    WorkerUtils.makeStatusNotification("Bluring image", applicationContext);
    WorkerUtils.sleep();

    try {
      // Этот код замещён на использовангие uri, которе выбрал пользователь.
      //Bitmap picture = BitmapFactory.decodeResource(applicationContext.getResources(), R.drawable.test);

      if (TextUtils.isEmpty(resourceUri)) {
        Log.e(TAG, "Invalid input uri");
        throw new IllegalArgumentException("Invalid input uri");
      }

      // Blur the bitmap

      ContentResolver resolver = applicationContext.getContentResolver();
      Bitmap picture = BitmapFactory.decodeStream(resolver.openInputStream(Uri.parse(resourceUri)));

      Bitmap output = WorkerUtils.blurBitmap(picture, applicationContext);

      // Wrirte bitmap to a temp file

      Uri outputUri = WorkerUtils.writeBitmapToFile(applicationContext, output);

      Data outputData = new Data.Builder()
          .putString(Constants.KEY_IMAGE_URI, outputUri.toString())
          .build();

      WorkerUtils.makeStatusNotification("Output is " + outputUri.toString(), applicationContext);
      // If there were no errors, return Success

      return Result.success(outputData);

    } catch (Throwable throwable) {
      // Technically WorkManager will return Result.failure()
      // but it's best to be explicit about it.
      // Thus if there were errors, we're return FAILURE
      Log.e(TAG, "Error applying blur", throwable);
      return Result.failure();
    }

  }
}
