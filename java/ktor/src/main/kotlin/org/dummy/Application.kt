package org.dummy

import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import org.dummy.plugins.configureRouting

fun main() {
    HtmlToPdfUtils.restartChromiumHeadless()
    createApplicationEngine()
        .start(wait = true)
}

fun createApplicationEngine() = embeddedServer(CIO, port = 8080, host = "0.0.0.0", module = Application::module)

fun Application.module() {
    configureRouting()
}
