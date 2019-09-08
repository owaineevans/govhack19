package com.example.myapplication

import android.Manifest
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AlertDialog
import com.google.android.gms.location.LocationRequest
import com.google.ar.core.Anchor
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.rendering.ModelRenderable
import com.google.ar.sceneform.ux.ArFragment
import com.google.ar.sceneform.ux.TransformableNode
import com.jakewharton.rxbinding3.view.clicks
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.disposables.CompositeDisposable
import kotlinx.android.synthetic.main.activity_main.button
import kotlinx.android.synthetic.main.activity_main.textView
import kotlinx.android.synthetic.main.activity_main.ux_fragment
import pl.charmas.android.reactivelocation2.ReactiveLocationProvider

class MainActivity : SensorActivity() {
    private val TAG = MainActivity::javaClass.name

    private val disposable = CompositeDisposable()

    private val rxPermissions = RxPermissions(this)
    private val locationProvider = ReactiveLocationProvider(this)

    private lateinit var arFragment: ArFragment

    private val mCameraRelativePose = Pose.makeTranslation(0f, 0f, -1f)

    private var andy: ModelRenderable? = null
    private var andyAnchor: Anchor? = null

    private val freoLocation = Location("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        arFragment = ux_fragment as ArFragment

        arFragment.arSceneView.scene.addOnUpdateListener {
            updateScene()
        }

        loadModels(Uri.parse("andy.sfb"))

        freoLocation.latitude = -32.056525
        freoLocation.longitude = 115.743473
    }

    override fun onResumeFragments() {
        super.onResumeFragments()

        val request = LocationRequest.create() //standard GMS LocationRequest
            .setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY)
            .setInterval(1000)

        disposable.add(button.clicks().subscribe({
            addObject()
        }, {
            it.logError()
        }))

        disposable.add(rxPermissions.request(Manifest.permission.ACCESS_FINE_LOCATION).subscribe({
            disposable.add(locationProvider.getUpdatedLocation(request).subscribe({
                updateLocation(it)
            }, { it.logError() }))
        }, { it.logError() }))

        disposable.add(rotationSubject.subscribe({
            textView.text = "$it"
        }, { it.logError() }))
    }

    override fun onPause() {
        super.onPause()

        disposable.clear()
    }

    private fun updateScene() {
        val camera = arFragment.arSceneView.arFrame?.camera ?: return
        val session = arFragment.arSceneView.session ?: return

        if (camera.trackingState != TrackingState.TRACKING) return
        andyAnchor?.detach()
        andyAnchor = session.createAnchor(camera.pose.compose(mCameraRelativePose)).also {
            addNode(arFragment, it, andy)
        }
    }

    private fun addObject() {
        // val frame = arFragment.arSceneView.arFrame
        // val point = getScreenCenter()
        // if (frame != null) {
        //     val hits = frame.hitTest(point.x.toFloat(), point.y.toFloat())
        //     hits.firstOrNull()?.createAnchor()?.also {
        //         placeObject(arFragment, it, parse)
        //     }
        // }
        val camera = arFragment.arSceneView.arFrame?.camera ?: return

        Log.e(TAG, camera.pose.toString())
        Log.e(TAG, camera.displayOrientedPose.toString())

        val session = arFragment.arSceneView.session ?: return
        andyAnchor = session.createAnchor(camera.pose.compose(mCameraRelativePose)).also {
            if (andy == null) return@also
            this@MainActivity.addNode(arFragment, it, andy)
        }
    }

    private fun loadModels(model: Uri) {
        if (andy != null) return
        ModelRenderable.builder().setSource(this, model).build().thenAccept {
            andy = it
        }.exceptionally {
            val builder = AlertDialog.Builder(this)
            builder.setMessage(it.message).setTitle("error!")
            builder.create().show()
            null
        }
    }

    private fun addNode(fragment: ArFragment, createAnchor: Anchor, renderable: ModelRenderable?) {
        val anchorNode = AnchorNode(createAnchor)
        val transformableNode = TransformableNode(fragment.transformationSystem)
        transformableNode.renderable = renderable
        transformableNode.setParent(anchorNode)
        fragment.arSceneView.scene.addChild(anchorNode)
        transformableNode.select()
    }

    private fun Throwable.logError() {
        Log.e(TAG, this.message ?: this.toString())
    }

    private fun updateLocation(location: Location) {
        Log.e(TAG, "Distance : ${location.distanceTo(freoLocation)} bearing : ${location.bearingTo(freoLocation)}")
    }
}
