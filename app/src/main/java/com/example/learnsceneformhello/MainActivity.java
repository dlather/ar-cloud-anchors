package com.example.learnsceneformhello;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import com.google.ar.core.Anchor;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.sceneform.AnchorNode;
import com.google.ar.sceneform.FrameTime;
import com.google.ar.sceneform.HitTestResult;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.ux.ArFragment;
import com.google.ar.sceneform.ux.TransformableNode;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final double MIN_OPENGL_VERSION = 3.0;

    private CloudAnchorFragment arFragment;
    private Anchor cloudAnchor;
    private List<Anchor> anchorList = new ArrayList<Anchor>();
    private List<String> cloudAnchorIdList = new ArrayList<String>();
    private List<Anchor> resolvingAnchorList = new ArrayList<Anchor>();
    private ModelRenderable modelRenderable;
    private ModelRenderable offerRenderable;
    Switch offerSwitch;
    enum AppAnchorState{
        NONE,
        HOSTING,
        HOSTED,
        RESOLVING,
        RESOLVED
    }
    private AppAnchorState appAnchorState = AppAnchorState.NONE;
    private String cloudAnchorId = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if(!checkIsSupportedDeviceOrFinish(this)){
            return;
        }
        setContentView(R.layout.activity_main);
        arFragment = (CloudAnchorFragment) getSupportFragmentManager().findFragmentById(R.id.ar_fragment);
        arFragment.getPlaneDiscoveryController().hide();

       offerSwitch = findViewById(R.id.offer_switch);

        final Button hostButton = findViewById(R.id.host_button);
        hostButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("mine","Host button clicked");
                if(cloudAnchor == null){
                    Toast toast = Toast.makeText(getApplicationContext(),"Add Anchor before Hosting",Toast.LENGTH_SHORT);
                    toast.show();
                    return;
                }
                cloudAnchor = arFragment.getArSceneView().getSession().hostCloudAnchor(cloudAnchor);
                Toast toast = Toast.makeText(getApplicationContext(),"Hosting Cloud Anchor",Toast.LENGTH_SHORT);
                toast.show();
                appAnchorState = AppAnchorState.HOSTING;
            }
        });

        final Button resolveButton = findViewById(R.id.resolve_button);
        resolveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.i("mine","Resolve button clicked");

                if(cloudAnchorIdList == null || cloudAnchorIdList.isEmpty()){
                    Log.i("mine", "Cloud Anchor Id List is Empty");
                    return;
                }
                if(cloudAnchorIdList.size() >= 10){
                    Log.i("mine", "Cloud Anchor Id List is Too Large");
                    return;
                }
                if(resolvingAnchorList == null){
                    resolvingAnchorList = new ArrayList<Anchor>();
                }
                if(anchorList == null || anchorList.isEmpty()){
                    for(int i = 0;i < cloudAnchorIdList.size();i++){
                        resolvingAnchorList.add(arFragment.getArSceneView().getSession().resolveCloudAnchor(cloudAnchorIdList.get(i)));
                    }
                    appAnchorState = AppAnchorState.RESOLVING;
                }
                else{
                    Log.i("mine","Anchor list is " + ((anchorList == null) ? "Null" : "empty"));
                }
            }
        });

        final Button clearButton = findViewById(R.id.clear_button);
        clearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(anchorList == null){
                    return;
                }
                for(int i = 0; i < anchorList.size(); i++){
                    anchorList.get(i).detach();
                }
                anchorList = new ArrayList<Anchor>();
                setCloudAnchor(null);
                Log.i("mine", "Cleared Anchors");
                return;
            }
        });

        arFragment.getArSceneView().getScene().addOnUpdateListener(this::checkAnchorStatus);

        arFragment.setOnTapArPlaneListener(
                (HitResult hitResult, Plane plane, MotionEvent motionEvent) -> {
                    if(plane.getType() != Plane.Type.HORIZONTAL_UPWARD_FACING){
                        Toast toast = Toast.makeText(this,"Need Horizontal surface",Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }
                    if(appAnchorState != AppAnchorState.NONE){
                        Toast toast = Toast.makeText(this,"Hosting / Resolving",Toast.LENGTH_LONG);
                        toast.show();
                        return;
                    }

                    Anchor anchor = hitResult.createAnchor();
                    setCloudAnchor(anchor);
                    placeObject(arFragment,anchor);
                }
        );

        ModelRenderable.builder()
                .setSource(this,R.raw.model)
                .build()
                .thenAccept(renderable -> modelRenderable = renderable)
                .exceptionally(throwable -> {
                    Log.i("mine","Error in Loading asset");
                    return null;
                });
        ModelRenderable.builder()
                .setSource(this,R.raw.andy)
                .build()
                .thenAccept(renderable -> offerRenderable = renderable)
                .exceptionally(throwable -> {
                    Log.i("mine","Error in Loading asset");
                    return null;
                });
    }

    private void setCloudAnchor(Anchor anchor){
        Log.i("mine","setCloudAnchor Started");
        if(cloudAnchor != null){
            cloudAnchor.detach();
        }
        cloudAnchor = anchor;
        appAnchorState = AppAnchorState.NONE;
        Log.i("mine","setCloudAnchor Finished");
    }

    private void checkAnchorStatus(FrameTime frameTime){
        if(appAnchorState != AppAnchorState.HOSTING && appAnchorState != AppAnchorState.RESOLVING){
            return;
        }
        if(appAnchorState == AppAnchorState.HOSTING){
            if(cloudAnchor.getCloudAnchorState().isError()){
                Toast toast = Toast.makeText(getApplicationContext(), "Error in Hosting", Toast.LENGTH_SHORT);
                Log.i("mine","Error in Hosting");
                toast.show();
                appAnchorState = AppAnchorState.NONE;
                return;
            }
            else if(cloudAnchor.getCloudAnchorState() == Anchor.CloudAnchorState.SUCCESS){

                cloudAnchorId = cloudAnchor.getCloudAnchorId();
                cloudAnchorIdList.add(cloudAnchorId);
                Log.i("mine","Hosted  :  " + cloudAnchorId);
                Toast toast = Toast.makeText(getApplicationContext(), "Hosted" +cloudAnchorId , Toast.LENGTH_SHORT);
                toast.show();
                appAnchorState = AppAnchorState.NONE;
                return;
            }
            else{
                Log.i("mine",cloudAnchor.getCloudAnchorState().toString());
            }
        }
        else if(appAnchorState == AppAnchorState.RESOLVING){
            int isResolved = 0;
            for(int i = 0;i < resolvingAnchorList.size() && isResolved != -1; i++) {
                if (resolvingAnchorList.get(i).getCloudAnchorState().isError()) {
                    isResolved = -1;
                } else if (resolvingAnchorList.get(i).getCloudAnchorState() == Anchor.CloudAnchorState.SUCCESS) {
                    Log.i(TAG, "checkAnchorStatus: Resolved with id : " + resolvingAnchorList.get(i).getCloudAnchorId());
                    isResolved = isResolved + 1;
                }
            }
                if(isResolved == -1){
                    Log.i("mine","Error in Resolving");
                    Toast toast = Toast.makeText(getApplicationContext(), "Error in Resolving", Toast.LENGTH_SHORT);
                    toast.show();
                    appAnchorState = AppAnchorState.NONE;
                    return;
                }
                else if(isResolved == resolvingAnchorList.size()){
                    for(int j = 0; j < resolvingAnchorList.size();j++){
                        placeObject(arFragment,resolvingAnchorList.get(j));
                    }
                    Toast toast = Toast.makeText(getApplicationContext(), "Resolved", Toast.LENGTH_SHORT);
                    toast.show();
                    appAnchorState = AppAnchorState.NONE;
                    return;
                }
                else{
                    Log.i("mine","Resolving List");
                    return;
                }
        }
    }

    private void placeObject(ArFragment arFragment, Anchor anchor){
        if(modelRenderable == null){
            Log.i("mine","Model not ready");
            return;
        }
        Log.i("mine","placeObject Started");
        AnchorNode anchorNode = new AnchorNode(anchor);
        anchorNode.setParent(arFragment.getArSceneView().getScene());
        TransformableNode transformableNode = new TransformableNode(arFragment.getTransformationSystem());
        ModelRenderable renderable;
        if(offerSwitch.isChecked()){
            renderable = offerRenderable;
        }
        else{
            renderable = modelRenderable;
        }
        transformableNode.setRenderable(renderable);
        transformableNode.setParent(anchorNode);
        transformableNode.select();
        if(anchorList == null){
            anchorList = new ArrayList<Anchor>();
        }
        anchorList.add(anchor);
        Log.i("mine","placeObject Finished");
    }

    public static boolean checkIsSupportedDeviceOrFinish(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            Log.i("mine","Is supported called");
            Log.e(TAG, "Sceneform requires Android N or later");
            Toast.makeText(activity, "Sceneform requires Android N or later", Toast.LENGTH_LONG).show();
            activity.finish();
            return false;
        }
        String openGlVersionString =
                ((ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE))
                        .getDeviceConfigurationInfo()
                        .getGlEsVersion();
        if (Double.parseDouble(openGlVersionString) < MIN_OPENGL_VERSION) {
            Log.e(TAG, "Sceneform requires OpenGL ES 3.0 later");
            Toast.makeText(activity, "Sceneform requires OpenGL ES 3.0 or later", Toast.LENGTH_LONG)
                    .show();
            activity.finish();
            return false;
        }
        return true;
    }


}
