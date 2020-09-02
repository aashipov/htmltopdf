package org.dummy

import kotlinx.coroutines.runBlocking
import org.hildan.chrome.devtools.domains.page.PrintToPDFRequest
import org.hildan.chrome.devtools.protocol.ChromeDPClient
import org.hildan.chrome.devtools.sessions.BrowserSession
import org.hildan.chrome.devtools.sessions.PageSession
import org.hildan.chrome.devtools.sessions.goto
import org.hildan.chrome.devtools.sessions.newPage

/**
 * [BrowserSession] wrapper.
 */
class ChromiumWrapper {

    companion object {
        private suspend fun browserSession(): BrowserSession {
            return ChromeDPClient("http://0.0.0.0:9222").webSocket()
        }

        private val browserSession: BrowserSession =
            runBlocking {
                browserSession()
            }

        private suspend fun pdfInner(url: String, printToPDFRequest: PrintToPDFRequest): String {
            val pageSession: PageSession = browserSession.newPage()
            pageSession.goto(url)
            val pdf: String = pageSession.page.printToPDF(printToPDFRequest).data
            pageSession.close()
            return pdf
        }

        @JvmStatic
        fun pdf(url: String, printToPDFRequest: PrintToPDFRequest): String =
            runBlocking {
                pdfInner(url, printToPDFRequest)
            }
    }
}
