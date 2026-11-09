package app.gamecollection

import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Каждый кадр ищет штрихкод. Когда пользователь нажимает «Прочитать текст»,
 * следующий кадр отправляется на распознавание текста (код издания, название).
 */
class FrameAnalyzer(
    private val onBarcode: (String) -> Unit,
    private val onText: (String) -> Unit,
    private val wantText: AtomicBoolean
) : ImageAnalysis.Analyzer {

    private val barcodeScanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A, Barcode.FORMAT_UPC_E
            ).build()
    )
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(proxy: ImageProxy) {
        val media = proxy.image
        if (media == null) {
            proxy.close()
            return
        }
        val image = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
        if (wantText.getAndSet(false)) {
            textRecognizer.process(image)
                .addOnSuccessListener { onText(it.text) }
                .addOnCompleteListener { proxy.close() }
        } else {
            barcodeScanner.process(image)
                .addOnSuccessListener { list -> list.firstOrNull()?.rawValue?.let(onBarcode) }
                .addOnCompleteListener { proxy.close() }
        }
    }
}
