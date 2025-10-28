package biz.cunning.cunning_document_scanner

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.IntentSender
import androidx.core.app.ActivityCompat
import biz.cunning.cunning_document_scanner.fallback.DocumentScannerActivity
import biz.cunning.cunning_document_scanner.fallback.constants.DocumentScannerExtra
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.PluginRegistry


/** CunningDocumentScannerPlugin */
class CunningDocumentScannerPlugin : FlutterPlugin, MethodCallHandler, ActivityAware {
    private var delegate: PluginRegistry.ActivityResultListener? = null
    private var binding: ActivityPluginBinding? = null
    private var pendingResult: Result? = null
    private lateinit var activity: Activity
    private val START_DOCUMENT_ACTIVITY: Int = 0x362738
    private val START_DOCUMENT_FB_ACTIVITY: Int = 0x362737


    /// The MethodChannel that will the communication between Flutter and native Android
    ///
    /// This local reference serves to register the plugin with the Flutter Engine and unregister it
    /// when the Flutter Engine is detached from the Activity
    private lateinit var channel: MethodChannel

    override fun onAttachedToEngine(flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
        channel = MethodChannel(flutterPluginBinding.binaryMessenger, "cunning_document_scanner")
        channel.setMethodCallHandler(this)
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        if (call.method == "getPictures") {
            val noOfPages = call.argument<Int>("noOfPages") ?: 50;
            val isGalleryImportAllowed = call.argument<Boolean>("isGalleryImportAllowed") ?: false;
            val flashMode = call.argument<String>("flashMode") ?: "auto";
            this.pendingResult = result
            startScan(noOfPages, isGalleryImportAllowed, flashMode)
        } else {
            result.notImplemented()
        }
    }


    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        this.activity = binding.activity

        addActivityResultListener(binding)
    }

    private fun addActivityResultListener(binding: ActivityPluginBinding) {
        this.binding = binding
        if (this.delegate == null) {
            this.delegate = PluginRegistry.ActivityResultListener { requestCode, resultCode, data ->
                if (requestCode != START_DOCUMENT_ACTIVITY && requestCode != START_DOCUMENT_FB_ACTIVITY) {
                    return@ActivityResultListener false
                }
                var handled = false
                if (requestCode == START_DOCUMENT_ACTIVITY) {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            // check for errors
                            val error = data?.extras?.getString("error")
                            if (error != null) {
                                pendingResult?.error("ERROR", "error - $error", null)
                            } else {
                                // get an array with scanned document file paths
                                val scanningResult: GmsDocumentScanningResult =
                                    data?.extras?.getParcelable("extra_scanning_result")
                                        ?: return@ActivityResultListener false

                                val successResponse = scanningResult.pages?.map {
                                    it.imageUri.toString().removePrefix("file://")
                                }?.toList()
                                // trigger the success event handler with an array of cropped images
                                pendingResult?.success(successResponse)
                            }
                            handled = true
                        }

                        Activity.RESULT_CANCELED -> {
                            // user closed camera
                            pendingResult?.success(emptyList<String>())
                            handled = true
                        }
                    }
                } else {
                    when (resultCode) {
                        Activity.RESULT_OK -> {
                            // check for errors
                            val error = data?.extras?.getString("error")
                            if (error != null) {
                                pendingResult?.error("ERROR", "error - $error", null)
                            } else {
                                // get an array with scanned document file paths
                                val croppedImageResults =
                                    data?.getStringArrayListExtra("croppedImageResults")?.toList()
                                        ?: let {
                                            pendingResult?.error("ERROR", "No cropped images returned", null)
                                            return@ActivityResultListener true
                                        }

                                // return a list of file paths
                                // removing file uri prefix as Flutter file will have problems with it
                                val successResponse = croppedImageResults.map {
                                    it.removePrefix("file://")
                                }.toList()
                                // trigger the success event handler with an array of cropped images
                                pendingResult?.success(successResponse)
                            }
                            handled = true
                        }

                        Activity.RESULT_CANCELED -> {
                            // user closed camera
                            pendingResult?.success(emptyList<String>())
                            handled = true
                        }
                    }
                }

                if (handled) {
                    // Clear the pending result to avoid reuse
                    pendingResult = null
                }
                return@ActivityResultListener handled
            }
        } else {
            binding.removeActivityResultListener(this.delegate!!)
        }

        binding.addActivityResultListener(delegate!!)
    }


    /**
     * create intent to launch document scanner and set custom options
     */
    private fun createDocumentScanIntent(noOfPages: Int, flashMode: String): Intent {
        val documentScanIntent = Intent(activity, DocumentScannerActivity::class.java)

        documentScanIntent.putExtra(
            DocumentScannerExtra.EXTRA_MAX_NUM_DOCUMENTS,
            noOfPages
        )
        
        // Pass flash mode to fallback scanner for future implementation
        documentScanIntent.putExtra("flashMode", flashMode)

        return documentScanIntent
    }


    /**
     * add document scanner result handler and launch the document scanner
     * 
     * Note: Google's ML Kit Document Scanner does not expose flash control.
     * The flash is automatically managed by the system based on lighting conditions.
     * The flashMode parameter is passed to the fallback scanner for potential future use.
     */
    private fun startScan(noOfPages: Int, isGalleryImportAllowed: Boolean, flashMode: String) {
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(isGalleryImportAllowed)
            .setPageLimit(noOfPages)
            .setResultFormats(RESULT_FORMAT_JPEG)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
        val scanner = GmsDocumentScanning.getClient(options)
        scanner.getStartScanIntent(activity).addOnSuccessListener {
            try {
                // Use a custom request code for onActivityResult identification
                // Note: ML Kit scanner does not support flash mode configuration
                activity.startIntentSenderForResult(it, START_DOCUMENT_ACTIVITY, null, 0, 0, 0)

            } catch (e: IntentSender.SendIntentException) {
                pendingResult?.error("ERROR", "Failed to start document scanner", null)
            }
        }.addOnFailureListener {
            if (it is MlKitException) {
                // Fall back to custom scanner which can potentially support flash control
                val intent = createDocumentScanIntent(noOfPages, flashMode)
                try {
                    ActivityCompat.startActivityForResult(
                        this.activity,
                        intent,
                        START_DOCUMENT_FB_ACTIVITY,
                        null
                    )
                } catch (e: ActivityNotFoundException) {
                    pendingResult?.error("ERROR", "FAILED TO START ACTIVITY", null)
                }
            } else {
                pendingResult?.error("ERROR", "Failed to start document scanner Intent", null)
            }
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {

    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        addActivityResultListener(binding)
    }

    override fun onDetachedFromActivity() {
        removeActivityResultListener()
    }

    private fun removeActivityResultListener() {
        this.delegate?.let { this.binding?.removeActivityResultListener(it) }
    }
}
