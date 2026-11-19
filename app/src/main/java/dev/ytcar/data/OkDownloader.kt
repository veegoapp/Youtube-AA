package dev.ytcar.data

import dev.ytcar.net.Net
import okhttp3.Request.Builder
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException

/** Minimal OkHttp-backed Downloader for NewPipeExtractor. No cookies, no identifiers stored. */
object OkDownloader : Downloader() {
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; rv:128.0) Gecko/20100101 Firefox/128.0"

    private val client get() = Net.client

    override fun execute(request: Request): Response {
        val body = request.dataToSend()?.toRequestBody()
        val method = request.httpMethod()
        val rb = Builder().url(request.url())
            .method(method, body ?: if (method == "POST" || method == "PUT") ByteArray(0).toRequestBody() else null)
            .header("User-Agent", USER_AGENT)
        for ((name, values) in request.headers()) {
            rb.removeHeader(name)
            values.forEach { rb.addHeader(name, it) }
        }
        client.newCall(rb.build()).execute().use { resp ->
            if (resp.code == 429) throw ReCaptchaException("Rate limited (429)", request.url())
            return Response(
                resp.code,
                resp.message,
                resp.headers.toMultimap(),
                resp.body?.string(),
                resp.request.url.toString(),
            )
        }
    }
}
