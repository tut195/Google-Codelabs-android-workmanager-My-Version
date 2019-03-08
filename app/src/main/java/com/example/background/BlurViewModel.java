/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.background;

import static com.example.background.Constants.IMAGE_MANIPULATION_WORK_NAME;
import static com.example.background.Constants.KEY_IMAGE_URI;
import static com.example.background.Constants.TAG_OUTPUT;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.net.Uri;
import android.text.TextUtils;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.OneTimeWorkRequest.Builder;
import androidx.work.WorkContinuation;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.example.background.workers.BlurWorker;
import com.example.background.workers.CleanupWorker;
import com.example.background.workers.SaveImageToFileWorker;
import java.util.List;

public class BlurViewModel extends ViewModel {

  private Uri mImageUri;
  private WorkManager mWorkManager;

  private LiveData<List<WorkInfo>> mSavedWorkInfo;

  // New instance variable for the WorkInfo
  private Uri mOutputUri;

  public BlurViewModel() {
    mWorkManager = WorkManager.getInstance();
    mSavedWorkInfo = mWorkManager.getWorkInfosByTagLiveData(TAG_OUTPUT);
  }

  LiveData<List<WorkInfo>> getOutputWorkInfo() {
    return mSavedWorkInfo;
  }

  // Add a getter and setter for mOutputUri
  void setOutputUri(String outputImageUri) {
    mOutputUri = uriOrNull(outputImageUri);
  }

  Uri getOutputUri() {
    return mOutputUri;
  }

  /**
   * Создаём WorkRequest и проталкиваем его в mWorkManager Можно запустить разовую работу или периодическую. PeriodicWorkRequest
   */
  void applyBlur(int blurLevel) {
    OneTimeWorkRequest _blurRequest = new OneTimeWorkRequest
        .Builder(BlurWorker.class)
        .setInputData(createInputDataForUri())
        .build();

//        mWorkManager.enqueue(OneTimeWorkRequest.from(BlurWorker.class));
    mWorkManager.enqueue(_blurRequest);
    // Сдесь показывается, как можно решить задачу с последовательным выполнением поставленных задачЖ
//        WorkContinuation continuation = mWorkManager.beginWith(workA);
//
//        continuation.then(workB) // FYI, then() returns a new WorkContinuation instance
//            .then(workC)
//            .enqueue(); // Enqueues the WorkContinuation which is a chain of work
    // -------------------------------------------------------------------------------------------

    // Add WorkRequest to Cleanup temporary images

    // Для того, чтобы стартануть уникальную работу, необходимо использовать
    // вместо beginWith  - beginUniqueWork;
    WorkContinuation continuation =
        mWorkManager.beginWith(OneTimeWorkRequest.from(CleanupWorker.class));

    for (int i = 0; i < blurLevel; i++) {
      OneTimeWorkRequest.Builder blurBuilder = new Builder(
          BlurWorker.class
      );
      // Input the Uri if this is the first blur operation
      // After the first blur operation the input will be the output of previous
      // blur operations.

      if (i == 0) {
        blurBuilder.setInputData(createInputDataForUri());
      }
      continuation = continuation.then(blurBuilder.build());
    }

    // Add WorkRequest to blur the image
    OneTimeWorkRequest blurRequest = new OneTimeWorkRequest.Builder(BlurWorker.class)
        .setInputData(createInputDataForUri())
        .build();
    continuation = continuation.then(blurRequest);

    // Add WorkRequest to save the image to the filesystem
    OneTimeWorkRequest save =
        new OneTimeWorkRequest.Builder(SaveImageToFileWorker.class)
            .build();

    continuation = continuation.then(save);

    // Actually start the work
    continuation.enqueue();

  }
  /*
   * Для получения статуса выполененной работы, необходимо добавить TAG. Сделаем это для saveWorkera
   *
   * */


  void applyBlurUnique(int blurLevel) {
    WorkContinuation continuation = mWorkManager
        .beginUniqueWork(IMAGE_MANIPULATION_WORK_NAME
            , ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequest.from(CleanupWorker.class));

    for (int i = 0; i < blurLevel; i++) {
      OneTimeWorkRequest.Builder blurBuilder = new Builder(
          BlurWorker.class
      );
      // Input the Uri if this is the first blur operation
      // After the first blur operation the input will be the output of previous
      // blur operations.

      if (i == 0) {
        blurBuilder.setInputData(createInputDataForUri());
      }
      continuation = continuation.then(blurBuilder.build());
    }

    // Add WorkRequest to blur the image
    OneTimeWorkRequest blurRequest = new OneTimeWorkRequest.Builder(BlurWorker.class)
        .setInputData(createInputDataForUri())
        .build();
    continuation = continuation.then(blurRequest);

    // Add WorkRequest to save the image to the filesystem
    /*
     * Пометим нашу work при помощи tag
     * */

    // Create charging constraint
    Constraints constraints = new Constraints.Builder()
        .setRequiresCharging(true)
        .build();

    OneTimeWorkRequest save =
        new OneTimeWorkRequest.Builder(SaveImageToFileWorker.class)
            .setConstraints(constraints)
            .addTag(Constants.TAG_OUTPUT)
            .build();

    continuation = continuation.then(save);

    // Actually start the work
    continuation.enqueue();
  }

  private Uri uriOrNull(String uriString) {
    if (!TextUtils.isEmpty(uriString)) {
      return Uri.parse(uriString);
    }
    return null;
  }

  /**
   * Setters
   */
  void setImageUri(String uri) {
    mImageUri = uriOrNull(uri);
  }

  /**
   * Getters
   */
  Uri getImageUri() {
    return mImageUri;
  }


  private Data createInputDataForUri() {
    Data.Builder builder = new Data.Builder();

    if (mImageUri != null) {
      builder.putString(KEY_IMAGE_URI, mImageUri.toString());
    }

    return builder.build();
  }

  /**
   * Cancel work using the work's unique name
   */
  void cancelWork() {
    mWorkManager.cancelUniqueWork(Constants.IMAGE_MANIPULATION_WORK_NAME);
  }
}