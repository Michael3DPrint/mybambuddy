package io.github.michael3dprint.mybambuddy.domain.nfc

/** Untrusted text from tags and barcodes: drop control characters, trim, cap the length. */
object TagText {
    /**
     * Removes control and replacement characters, trims the result and cuts it to [maxLength]. For text that comes from outside the
     * app.
     */
    fun sanitize(text: String, maxLength: Int): String = text.filter { !it.isISOControl() && it != '�' }.trim().take(maxLength)
}
