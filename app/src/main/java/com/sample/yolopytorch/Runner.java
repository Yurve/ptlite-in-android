package com.sample.yolopytorch;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;
import org.pytorch.IValue;
import org.pytorch.LiteModuleLoader;
import org.pytorch.Module;
import org.pytorch.Tensor;
import org.pytorch.torchvision.TensorImageUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class Runner {
    Context context;
    Activity activity;
    String fileName;
    Module module;

    //생성자
    public Runner(Context context, Activity activity, String fileName) {
        this.context = context;
        this.activity = activity;
        this.fileName = fileName;
    }

    public void getAssets(String fileName) {
        AssetManager assetManager = context.getAssets();
        File outputFile = new File(context.getFilesDir().getAbsolutePath() + "/" + fileName);
        try {
            InputStream inputStream = assetManager.open(fileName);
            OutputStream outputStream = new FileOutputStream(outputFile);

            byte[] buffer = new byte[4 * 1024];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
            }
            inputStream.close();
            outputStream.flush();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void init() {
        try {
            module = LiteModuleLoader.load(context.getFilesDir().getAbsolutePath() + "/" + fileName);
            BufferedReader br = new BufferedReader(new InputStreamReader(context.getAssets().open("coco.txt")));
            String line;
            List<String> classes = new ArrayList<>();
            while ((line = br.readLine()) != null) {
                classes.add(line);
            }
            PrePostProcessor.mClasses = new String[classes.size()];
            classes.toArray(PrePostProcessor.mClasses);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //이미지 → 텐서 → 모델 → 추론 결과 → 결과값을 텐서로 변환 → 텐서에서 float 형 배열로 변환 → 배열에서 각각의 객체 좌표 추출
    public ArrayList<Result> setInput(Bitmap bitmap) {
        //입력준비
        Tensor inputTensor = TensorImageUtils.bitmapToFloat32Tensor(bitmap, TensorImageUtils.TORCHVISION_NORM_MEAN_RGB, TensorImageUtils.TORCHVISION_NORM_STD_RGB);
        //출력 준비 & 추론 실행
        IValue[] outputTuple = module.forward(IValue.from(inputTensor)).toTuple();
        Tensor outputTensor = outputTuple[0].toTensor();
        float[] scores = outputTensor.getDataAsFloatArray();
        //처리 결과
        //이미지 크기 수정
        float imgScaleX = (float) bitmap.getWidth() / MainActivity.INPUT_SIZE;
        float imgScaleY = (float) bitmap.getHeight() / MainActivity.INPUT_SIZE;
        //화면에 보여주는 크기 비율
        float ivScaleX = (float) MainActivity.INPUT_SIZE / bitmap.getWidth();
        float ivScaleY = (float) MainActivity.INPUT_SIZE / bitmap.getHeight();
        //결과 도출
        ArrayList<Result> results = PrePostProcessor.outputsToNMSPredictions(scores, imgScaleX, imgScaleY, ivScaleX, ivScaleY, 0, 0);

        //result View 로 보내서 그림그리기
        return results;

    }


}

