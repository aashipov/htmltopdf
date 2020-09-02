package org.dummy.plugins

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*
import org.dummy.HtmlToPdfUtils.*
import java.nio.file.Files
import java.nio.file.StandardCopyOption

const val CHROMIUM = "chromium"
const val HTML = "html"
const val STATUS_UP: String = "{\"status\":\"UP\"}"
val FILES: String = "files"

fun Application.configureRouting() {
    routing {
        get("/{...}") {
            call.respondText(STATUS_UP)
        }
        post("/{...}") {
            val uri = call.request.uri
            if (uri.contains(CHROMIUM) || uri.contains(HTML)) {
                val po = PrinterOptions(uri)
                processParts(po)
                if (po.isIndexHtml) {
                    po.htmlToPdf()
                    if (po.isPdf) {
                        pdfResponse(po)
                    } else {
                        textResponse("No " + RESULT_PDF)
                    }
                } else {
                    textResponse("No " + INDEX_HTML)
                }
                po.clearWorkdir()
            }
        }
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.processParts(po: PrinterOptions) {
    val multipartData = call.receiveMultipart()
    multipartData.forEachPart { part ->
        when (part) {
            is PartData.FileItem -> {
                if (part.name.equals(FILES)) {
                    val originalFileName = part.originalFileName as String
                    Files.copy(
                        part.streamProvider.invoke(),
                        po.workdir.resolve(originalFileName),
                        StandardCopyOption.REPLACE_EXISTING
                    )
                }
            }

            else -> {}
        }
        part.dispose()
    }
}

private suspend fun PipelineContext<Unit, ApplicationCall>.pdfResponse(
    po: PrinterOptions
) {
    call.respondBytes(
        contentType = ContentType.defaultForFileExtension("pdf"),
        status = HttpStatusCode.OK,
        bytes = po.pdf
    )
}

private suspend fun PipelineContext<Unit, ApplicationCall>.textResponse(response: String) {
    call.respondText(
        contentType = ContentType.parse("text/plain"),
        status = HttpStatusCode.OK,
        provider = { response }
    )
}
