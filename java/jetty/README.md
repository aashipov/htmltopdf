### HTML to PDF ###

https://github.com/aashipov/htmltopdf twin, implemented in JavaSE (jetty)

Expects chromium executable at ```/usr/bin/chromium```. Use ```chromium.executable``` system property to override 

##### Docker, single instance #####

JVM ```docker pull aashipov/htmltopdf-javase:jetty && docker run -d --rm --name=htmltopdf-javase -p 8080:8080 aashipov/htmltopdf-javase:jetty```

##### Docker, three instance behind HAProxy #####

```cd test``` ```cd farm``` ```bash farm-refresh```
