package com.sample.yolopytorch;

import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.media.Image;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private PreviewView previewView;
    private ProcessCameraProvider processCameraProvider;
    public boolean isProcess = false;
    public static int INPUT_SIZE = 640;
    Runner runner;
    String fileName = "yolov5s.torchscript.ptl";
    private JSONObject jsonObject;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        previewView = findViewById(R.id.previewView);

        //권한 허용하기
        permissionCheck();

        //Runner 객체 생성
        runner = new Runner(this, this, fileName);

        //모델 불러오기
        runner.getAssets(fileName);
        runner.getAssets("coco.txt");
        runner.init();

        try {
            ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
            processCameraProvider = cameraProviderFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
        //카메라 켜기
        startCamera();

    }

    //카메라 메소드
    public void startCamera() {
        previewView.setScaleType(PreviewView.ScaleType.FIT_CENTER);

        //카메라 렌즈 중 자기가 고를 렌즈 선택
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                .build();

        //표준 화면으로 미리보기 //736이 어차피 분석 이미지 최고치
        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        //이미지 분석
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                //분석할 사진의 크기, 프리뷰의 크기와 같게 해야한다. 이후에 모델에 맞게 수정하고 넣어야함. 디버깅찍어보면 736으로 고정? 정사각형임.
               // .setTargetResolution(new Size(INPUT_SIZE,INPUT_SIZE))
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                //분석 중이면 그 다음 화면이 대기중인게 아니라 계속 받아오는 화면으로 새로 고침함. 분석이 끝나면 그 최신 화면을 다시 분석
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        imageAnalysis.setAnalyzer(Executors.newCachedThreadPool(), new ImageAnalysis.Analyzer() {
            @Override
            public void analyze(@NonNull ImageProxy imageProxy) {
                //만약 이미지 처리 과정을 하고 있다면 이미지 처리를 하지 않음.

                if (isProcess) {
                    imageProxy.close();
                    return;
                }
                //이미지 처리 메소드
                imageToBitmap(imageProxy);

                // after done, release the ImageProxy object
                imageProxy.close();
            }
        });

        processCameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }

    @SuppressLint("UnsafeOptInUsageError")
    public void imageToBitmap(ImageProxy imageProxy) {
        //시간측정해보자
        long start = System.currentTimeMillis();

        //이미지 받아오기
        Image image = imageProxy.getImage();

        //이미지 처리과정 On;
        isProcess = true;

        //비트맵 만들기
        if (image != null) {
            //화면에 비춰지는 2차원 평면
            Image.Plane[] planes = image.getPlanes();

            //yuv 형태의 색상 표현 RGB 색상 표현 방식은 색 전부를 표현하지만, 용량이 너무 크기에 yuv 형태로 표현한다.
            //y = 빛의 밝기를 나타내는 휘도, u,v = 색상신호,  u가 클수록 보라색,파란색 계열 ,v가 클수록 빨간색, 초록색 계열이다.
            ByteBuffer yBuffer = planes[0].getBuffer();
            ByteBuffer uBuffer = planes[1].getBuffer();
            ByteBuffer vBuffer = planes[2].getBuffer();

            //buffer 에 담긴 데이터를 limit 까지 읽어들일 수 있는 데이터의 갯수를 리턴한다.
            int ySize = yBuffer.remaining();
            int uSize = uBuffer.remaining();
            int vSize = vBuffer.remaining();

            //버퍼 최대크기를 모아놓은 바이트 배열
            byte[] yuvBytes = new byte[ySize + uSize + vSize];
            //buffer 의 offset 위치부터 length 길이 만큼 읽음.
            //각각 y, u, v 읽어오기
            yBuffer.get(yuvBytes, 0, ySize);
            vBuffer.get(yuvBytes, ySize, vSize);
            uBuffer.get(yuvBytes, ySize + vSize, uSize);

            //이제 바이트 배열을 가지고와서 Yuv 이미지를 만듬.
            YuvImage yuvImage = new YuvImage(yuvBytes, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            //jpeg 파일로 압축
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 90, out);

            //비트맵으로 변환
            byte[] imageBytes = out.toByteArray();
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);

            //비트맵은 현재 회전되어있는 상태 모델에 넣으려면 각도를 돌리고 넣어야 한다.
            Matrix matrix = new Matrix();
            matrix.postRotate(90.0f);
            //90도 회전
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), null, true);

            //모델파일에 넣으려면 비트맵 크기를 줄여야한다. 문제는 가로 비율로 하면 잘린 화면으로 모델에 집어넣음. 어떻게 해야할까나 시작부터그냥 모델 입력비로 맞춤
            bitmap = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true);

            //추론 클래스에 넣어서 추론하고 결과 가져오기
            ArrayList<Result> results = runner.setInput(bitmap);

            //추론된게 있다면 화면에 보여주기
            if (results != null && results.size() > 0) {
                for (Result result : results) {
                    String name = PrePostProcessor.mClasses[result.classIndex];
                    String score = Math.round(result.score * 100) + "%";
                    try {
                        jsonObject = new JSONObject();
                        jsonObject.put(name, score);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), jsonObject.toString(), Toast.LENGTH_SHORT).show();
                    }
                });

            }

            //이미지 처리과정 Off
            isProcess = false;

            //시간 종료 추론까지 약 1초 소요.
            long end = System.currentTimeMillis();
            Log.d("time", ":" + (end - start));
        }
    }

    public Matrix getMappingMatrix(ImageProxy imageProxy, PreviewView previewView) {
        Rect cropRect = imageProxy.getCropRect();
        int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
        Matrix matrix = new Matrix();

        // A float array of the source vertices (crop rect) in clockwise order. 시계방향으로 좌표값 받아온 것 x,y값 처럼 두개씩
        float[] source = {
                cropRect.left,
                cropRect.top,
                cropRect.right,
                cropRect.top,
                cropRect.right,
                cropRect.bottom,
                cropRect.left,
                cropRect.bottom
        };

        // A float array of the destination vertices in clockwise order. 시계방향으로 꼭짓점들 가져온것 x,y값 처럼 두개씩
        float[] destination = {
                0f,
                0f,
                previewView.getWidth(),
                0f,
                previewView.getWidth(),
                previewView.getHeight(),
                0f,
                previewView.getHeight()
        };

        // The destination vertexes need to be shifted based on rotation degrees.
        // The rotation degree represents the clockwise rotation needed to correct
        // the image.

        // Each vertex is represented by 2 float numbers in the vertices array.
        int vertexSize = 2;
        // The destination needs to be shifted 1 vertex for every 90° rotation.
        int shiftOffset = rotationDegrees / 90 * vertexSize;
        float[] tempArray = destination.clone();
        for (int toIndex = 0; toIndex < source.length; toIndex++) {
            int fromIndex = (toIndex + shiftOffset) % source.length;
            destination[toIndex] = tempArray[fromIndex];
        }
        matrix.setPolyToPoly(source, 0, destination, 0, 4);
        return matrix;
    }

    @Override
    protected void onDestroy() {
        processCameraProvider.unbindAll();
        super.onDestroy();
    }

    //권한 허용하기
    private void permissionCheck() {
        //권한 클래스
        PermissionSupport permissionSupport = new PermissionSupport(this, this);

        permissionSupport.onCheckPermission();
    }

    //권한 허용 확인
    @Override
    public void onRequestPermissionsResult(int requestCode, @androidx.annotation.NonNull String[] permissions, @androidx.annotation.NonNull int[] grantResults) {
        if (requestCode == PermissionSupport.MULTIPLE_PERMISSIONS) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Ok", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "No", Toast.LENGTH_SHORT).show();
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

}