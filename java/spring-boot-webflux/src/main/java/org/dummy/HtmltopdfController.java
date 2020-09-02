package org.dummy;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.dummy.HtmlToPdfUtils.INDEX_HTML;
import static org.dummy.HtmlToPdfUtils.RESULT_PDF;

@RestController
public class HtmltopdfController {
    static final String STATUS_UP = "{\"status\":\"UP\"}";
    static final String PDF_ATTACHED = "attachment;filename=\"" + RESULT_PDF + "\"";

    @GetMapping(path = "/**", produces = MediaType.TEXT_PLAIN_VALUE)
    public Mono<String> get() {
        return Mono.just(STATUS_UP);
    }

    @PostMapping(path = "/**")
    public Mono<byte[]> post(ServerWebExchange exchange, @RequestPart(name = "files") Flux<FilePart> filePartFlux) {
        String url = exchange.getRequest().getURI().toString();
        HtmlToPdfUtils.PrinterOptions po = new HtmlToPdfUtils.PrinterOptions(url);

        return filePartFlux.flatMap(filePart -> {
            Path path = po.getWorkdir().resolve(filePart.filename());
            return filePart.transferTo(path);
        }).then(Mono.create(sink -> {
            if (!po.isIndexHtml()) {
                exchange.getResponse().setStatusCode(HttpStatus.NOT_ACCEPTABLE);
                exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
                sink.success(("No " + INDEX_HTML).getBytes(StandardCharsets.UTF_8));
            } else {
                po.htmlToPdf();
                po.clearWorkdir();
                if (!po.isPdf()) {
                    exchange.getResponse().setStatusCode(HttpStatus.NOT_ACCEPTABLE);
                    exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
                    sink.success(("No " + RESULT_PDF).getBytes(StandardCharsets.UTF_8));
                } else {
                    exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PDF);
                    exchange.getResponse().getHeaders().setContentDisposition(ContentDisposition.parse(PDF_ATTACHED));
                    sink.success(po.getPdf());
                }
            }
        }));
    }
}
